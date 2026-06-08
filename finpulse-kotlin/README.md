# FinPulse

A real-time financial news sentiment platform that tracks 1,700+ NASDAQ/NYSE tickers, classifies each article with a FinBERT ML model, and surfaces sentiment trends and unusual trading volume on a live dashboard.

## Architecture Overview

```
+--------------------------------- INGESTION ----------------------------------+
|                                                                              |
|   [Scheduler / 15 min]            [POST /api/v1/admin/fetch]                |
|           |                                   |                             |
|           +------------------+----------------+                             |
|                              v                                               |
|                    NewsFetcherService                                        |
|              +---------------+---------------+                               |
|              v               v               v                               |
|        FinnhubClient  MarketauxClient  NewsDataClient                        |
|              +---------------+---------------+                               |
|                      deduplicate by URL                                      |
+------------------------------+-----------------------------------------------+
                               |  ArticleEvent
                               |  (summary truncated to 2000 chars)
                               v
                        +--------------+
                        | NewsProducer |
                        | key: ticker_ |  <- salted key spreads hot tickers
                        |  url_hash%N  |     across N partitions
                        +------+-------+
                               |
              +----------------+------------------+
              |   Kafka: raw-articles             |
              |   partitions=6  retention=24h     |
              |   max.message.bytes=1MB           |
              +----------------+------------------+
                               |
                    +----------+----------+
                    |  finpulse-sentiment |  Python 3.12
                    |  FinBERT classifier |  ProsusAI/finbert
                    |  (Kafka consumer)   |  positive / negative / neutral
                    +----------+----------+
                               |  AnalyzedArticleEvent + sentimentScore
                               v
              +----------------+------------------+
              |   Kafka: analyzed-articles        |
              |   partitions=6  retention=24h     |
              |   max.message.bytes=1MB           |
              +----------------+------------------+
                               |
                        +------+------+
                        | NewsConsumer|
                        +--+-----+----+
            +--------------+     +---------------------+
            |                    |                     | score >= 0.85
            v                    v                     v
     +-------------+    +--------------+   +--------------------+
     |    MySQL    |    |    Redis     |   | SentimentAlertBus  |
     |  articles   |    |  sentiment   |   |  (Reactor sink)    |
     | vol_records |    |  trending    |   +--------+-----------+
            |           +------+-------+            |
            |                  |          GET /api/v1/alerts/stream
            +----------+-------+          (Server-Sent Events)
                       |
          +------------+----------------------------+
          |              REST API                   |
          |  /tickers/{sym}/articles                |
          |  /tickers/{sym}/sentiment               |
          |  /tickers/{sym}/rvol                    |
          |  /tickers/trending  /tickers/movers     |
          |  /articles  /dashboard  /admin/fetch    |
          +------------+----------------------------+
                       |
          +------------+----------------------------+
          |           Thymeleaf UI                  |
          |    /dashboard        /ticker/{sym}      |
          +-----------------------------------------+
```

---

## Features

- **Dashboard** — top trending tickers by 24h mention count, sentiment movers (biggest score shift), RVOL per ticker
- **Ticker page** — 30-day sentiment trend chart, daily positive/negative/neutral breakdown, paginated news articles with scores
- **RVOL (Relative Volume)** — today's volume vs 30-day average; intraday volume is projected to a full-day equivalent during US market hours so the ratio is meaningful at any time of day
- **Symbol registry** — 1,700+ tickers from NASDAQ, NYSE, and NYSE American, loaded on startup and refreshed daily from Finnhub
- **Live alerts** — articles scoring ≥ 0.85 streamed to the browser in real time via Server-Sent Events
- **Rate limiting** — 60 req/min per IP enforced in Redis via a WebFlux filter

---

## Data Flow

1. **Ingestion** — `NewsFetcherService` polls three external APIs (Finnhub, Marketaux, NewsData) on a 15-minute schedule, or on-demand via `POST /api/v1/admin/fetch`. Duplicate URLs are filtered in-memory before publishing to Kafka.

2. **Processing** — The Python `finpulse-sentiment` service consumes `raw-articles`, classifies each article with FinBERT (`ProsusAI/finbert`), and publishes an `AnalyzedArticleEvent` to `analyzed-articles`.

3. **Storage** — `NewsConsumer` consumes `analyzed-articles`, persists new articles to MySQL, and updates per-ticker sentiment counters and a trending sorted-set in Redis.

4. **Alerts** — Articles scoring >= 0.85 are pushed to the `SentimentAlertBus` (Reactor multicast sink) and streamed to connected browsers via Server-Sent Events at `/api/v1/alerts/stream`.

5. **Serving** — REST controllers serve ticker sentiment history, article search/pagination, trending tickers, and sentiment movers from MySQL and Redis.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Kotlin 2.2 (K2), Spring Boot 4, Spring WebFlux, Kotlin Coroutines |
| ML Service | Python 3.12, FinBERT (`ProsusAI/finbert`), kafka-python |
| Messaging | Apache Kafka (KRaft mode) |
| Persistence | MySQL 8, Spring Data JPA, Hibernate 7 |
| Cache / Trending | Redis 7, Spring Data Redis |
| Volume data | Twelve Data API |
| Frontend | Thymeleaf, Bootstrap 5, Chart.js |

## Non-functional Design Decisions

### Kafka

- **Fault tolerance and durability** 
  - `acks=all` ensures that the message is acknowledged only after all in-sync replicas have written it. 
  - `enable.idempotence=true` prevents message loss and duplicates even if the broker leader changes mid-write.

- **Scalability**
  - **Message size**
    - Summaries are truncated to 2,000 characters before serialisation
    - both the producer (`max.request.size`) and topics (`max.message.bytes`) enforce a hard 1 MB cap.

  - **Partitioning Strategy : Hot partition handling** 
    - **key salting**: the partition key is `{ticker}_{abs(url.hashCode()) % N}` where N is configurable via `finpulse.kafka.salt-buckets` (default 3). 
      - The trade-off: per-ticker ordering is no longer guaranteed, which is acceptable here since each article is scored and saved independently.
    - Both topics use 6 partitions, giving headroom for up to 6 parallel `SentimentProcessor` or `NewsConsumer` instances before repartitioning is needed.

- **Retention** — 24-hour retention on both topics. Processed articles are durably stored in MySQL, so Kafka is treated as a transport layer, not a source of truth.

### Redis Cache

**Why Redis for sentiment data**

- **Atomic increments** (`HINCRBY`) : multiple Kafka consumer threads accumulate sentiment scores for the same ticker concurrently. Redis performs each increment atomically and in microseconds with no locking, whereas MySQL would require a `SELECT … FOR UPDATE` or optimistic-retry loop on every article.
- **Sorted-set leaderboard** (`ZADD` / `ZREVRANGE`) : the trending ticker list is a live ranked set. Redis sorted sets maintain rank in O(log N) on every write and serve the top-N in O(log N + N). The equivalent in MySQL is a full `GROUP BY … ORDER BY` scan on every dashboard request.

- Implementation details
  - sliding 7-day TTL** (`finpulse.redis.sentiment-ttl-days`) : set on each sentiment hash on every `update()` call, so active tickers stay warm and inactive ones are evicted automatically.
  - The trending sorted sets have no TTL because they are maintained by score updates rather than time.

- **Hot key problem** : Popular tickers (AAPL, TSLA, NVDA) receive disproportionately high traffic on a small number of Redis keys. Three strategies and their trade-offs:
- **Key sharding** — The trending sorted set is replicated across N shards (`finpulse:trending:0 … finpulse:trending:{N-1}`, controlled by `finpulse.redis.trending-shards`, default 3).
  - Every `update()` writes the same score to **all** shards so each shard holds a complete view. 
  - `topTrending()` reads from a **randomly selected shard**, spreading read load across N keys in the Redis cluster : this resolves the hot key problem 
  - Trade-off: write amplification increases by N×, which is acceptable since writes here are far less frequent than dashboard reads.

## Running Locally

### Prerequisites
- Docker and Docker Compose
- JDK 17
- Python 3.12

### 1. Start infrastructure
```bash
docker-compose up kafka mysql redis
```

### 2. Configure environment variables
Copy `.env.example` to `.env` and fill in your API keys, or export directly:
```bash
export DB_USERNAME=finpulse
export DB_PASSWORD=finpulse
export FINNHUB_API_KEY=your_key
export MARKETAUX_API_KEY=your_key
export NEWSDATA_API_KEY=your_key
export TWELVEDATA_API_KEY=your_key
# KAFKA_BOOTSTRAP_SERVERS defaults to localhost:9092
```

### 3. Start the Spring Boot app
```bash
./gradlew bootRun
```
Starts on http://localhost:8081. Hibernate creates all tables automatically on first run.

### 4. Start the sentiment analyzer
The Python service lives in the companion repo [finpulse-sentiment](https://github.com/hyunssong/finpulse-sentiment):
```bash
cd ../finpulse-sentiment
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python analyzer.py
```

### 5. Run everything with Docker Compose
```bash
docker-compose up --build
```
Builds and starts all services — Kafka, MySQL, Redis, the Spring Boot app, and the Python analyzer.

### FinBERT over general-purpose sentiment models

Standard models (VADER, TextBlob) are trained on general text and misread financial language — "bearish", "short", and "volatile" score as negative emotionally but are neutral or contextual domain terms. FinBERT is fine-tuned specifically on financial news and analyst reports, producing more accurate signal for this use case.

The trade-off is operational: FinBERT requires a separate Python process (~900MB Docker image with CPU-only PyTorch) and introduces ML inference latency per article. This is absorbed by running it as an async Kafka consumer — the Spring Boot app never waits on it.

### Intraday RVOL normalization

Comparing today's partial volume directly against historical full-day averages always produces an artificially small ratio during trading hours. The fix projects today's raw volume to a full-day equivalent: `rawVolume × (390 / elapsedMinutes)` where 390 is the number of US market minutes (9:30–4:00 ET). Historical records are already full-day and pass through unchanged. This makes the RVOL ratio meaningful at any point during the trading day.

### Symbol registry with `fixedDelay`

With 1,700+ symbols and a 150ms inter-request delay (Finnhub free tier: 60 req/min), one full fetch cycle takes ~4 minutes. Using `fixedRate` would start the next cycle before the previous one finishes, causing overlapping runs and duplicate API calls. `fixedDelay` waits for the current run to complete before scheduling the next. The symbol list itself is loaded once on startup via `ApplicationReadyEvent` and refreshed daily.

---

## Testing

```bash
./gradlew test                  # runs unit tests (22 tests)
./gradlew test jacocoTestReport # coverage report -> build/reports/jacoco/test/html/index.html
```

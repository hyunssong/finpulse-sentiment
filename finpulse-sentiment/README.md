# finpulse-sentiment

Kafka consumer that classifies financial news articles using [FinBERT](https://huggingface.co/ProsusAI/finbert) and publishes sentiment-enriched results back to Kafka.

---

## How it works

1. Consumes messages from the `raw-articles` Kafka topic
2. Combines the article title and summary and runs them through FinBERT
3. Publishes the original article JSON enriched with `sentiment` (`positive` / `negative` / `neutral`) and `sentimentScore` to `analyzed-articles`

---

## Tech Stack

- Python 3.12
- [ProsusAI/finbert](https://huggingface.co/ProsusAI/finbert) via HuggingFace Transformers
- PyTorch (CPU)
- kafka-python-ng

---

## Running Locally

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python analyzer.py
```

Requires a running Kafka instance with the `raw-articles` topic. See [finpulse-kotlin](https://github.com/hyunssong/finpulse-kotlin) for the full local setup with Docker Compose.

---

## Configuration

All config is via environment variables:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_INPUT_TOPIC` | `raw-articles` | Topic to consume from |
| `KAFKA_OUTPUT_TOPIC` | `analyzed-articles` | Topic to publish to |
| `KAFKA_GROUP_ID` | `sentiment-analyzer` | Consumer group ID |

Copy `.env.example` to `.env` and fill in values for your environment.

---

## Design Notes

**Why FinBERT?** General-purpose sentiment models (VADER, TextBlob) misread financial language — terms like "bearish", "short", and "correction" are flagged as negative emotionally but are neutral domain vocabulary. FinBERT is fine-tuned on financial news and analyst reports, giving substantially more accurate signal.

**Model pre-loading** The Dockerfile downloads the FinBERT model (~500MB) at build time, so container cold starts are instant rather than spending 30+ seconds downloading on every restart.

**CPU-only PyTorch** GPU inference isn't needed for this throughput level. Using `torch+cpu` cuts the Docker image from ~2.5GB to ~900MB.

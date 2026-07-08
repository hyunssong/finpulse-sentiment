import json
import logging
import os
import threading
from kafka import KafkaConsumer, KafkaProducer
from transformers import pipeline
from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

BOOTSTRAP_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
INPUT_TOPIC        = os.environ.get("KAFKA_INPUT_TOPIC",        "raw-articles")
OUTPUT_TOPIC       = os.environ.get("KAFKA_OUTPUT_TOPIC",       "analyzed-articles")
GROUP_ID           = os.environ.get("KAFKA_GROUP_ID",           "sentiment-analyzer")
FINBERT_MODEL      = os.environ.get("FINBERT_MODEL",            "ProsusAI/finbert")
API_PORT           = int(os.environ.get("FINBERT_API_PORT",     "8000"))
MAX_TOKENS = 512

_finbert = None
_model_lock = threading.Lock()

app = FastAPI()


class AnalyzeRequest(BaseModel):
    title: str
    summary: str = ""


class AnalyzeResponse(BaseModel):
    sentiment: str
    sentimentScore: float


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze_endpoint(req: AnalyzeRequest):
    sentiment, score = _infer(req.title, req.summary)
    return AnalyzeResponse(sentiment=sentiment, sentimentScore=score)


def _infer(title: str, summary: str = "") -> tuple[str, float]:
    text = f"{title}. {summary}"
    with _model_lock:
        result = _finbert(text)[0]
    return result["label"].lower(), round(result["score"], 4)


def load_model():
    log.info("Loading FinBERT model...")
    model = pipeline("text-classification", model=FINBERT_MODEL, truncation=True, max_length=MAX_TOKENS)
    log.info("FinBERT model ready")
    return model


def analyze(article: dict) -> dict:
    sentiment, score = _infer(article["title"], article.get("summary", ""))
    return {**article, "sentiment": sentiment, "sentimentScore": score}


def main():
    global _finbert
    _finbert = load_model()

    threading.Thread(
        target=lambda: uvicorn.run(app, host="0.0.0.0", port=API_PORT, log_level="warning"),
        daemon=True,
    ).start()
    log.info("FinBERT HTTP API listening on :%d", API_PORT)

    consumer = KafkaConsumer(
        INPUT_TOPIC,
        bootstrap_servers=BOOTSTRAP_SERVERS,
        group_id=GROUP_ID,
        auto_offset_reset="earliest",
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        key_deserializer=lambda k: k.decode("utf-8") if k else None,
    )

    producer = KafkaProducer(
        bootstrap_servers=BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if k else None,
        acks="all",
    )

    log.info("Listening on %s...", INPUT_TOPIC)

    for message in consumer:
        try:
            article = message.value
            result = analyze(article)
            producer.send(OUTPUT_TOPIC, key=article.get("ticker"), value=result)
            log.info("Analyzed: ticker=%s sentiment=%s score=%.4f url=%s",
                     article.get("ticker"), result["sentiment"], result["sentimentScore"], article.get("url"))
        except Exception:
            log.exception("Failed to process message at offset %d", message.offset)


if __name__ == "__main__":
    main()

import json
import logging
import os
from kafka import KafkaConsumer, KafkaProducer
from transformers import pipeline

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

BOOTSTRAP_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
INPUT_TOPIC        = os.environ.get("KAFKA_INPUT_TOPIC",        "raw-articles")
OUTPUT_TOPIC       = os.environ.get("KAFKA_OUTPUT_TOPIC",       "analyzed-articles")
GROUP_ID           = os.environ.get("KAFKA_GROUP_ID",           "sentiment-analyzer")
FINBERT_MODEL      = os.environ.get("FINBERT_MODEL",            "ProsusAI/finbert")
MAX_TOKENS = 512


def load_model():
    log.info("Loading FinBERT model...")
    model = pipeline("text-classification", model=FINBERT_MODEL, truncation=True, max_length=MAX_TOKENS)
    log.info("FinBERT model ready")
    return model


def analyze(finbert, article: dict) -> dict:
    # combine title and summary for richer signal
    text = f"{article['title']}. {article.get('summary', '')}"
    result = finbert(text)[0]
    return {
        **article,
        "sentiment": result["label"].lower(),   # positive / negative / neutral
        "sentimentScore": round(result["score"], 4),
    }


def main():
    finbert = load_model()

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
            result = analyze(finbert, article)
            producer.send(OUTPUT_TOPIC, key=article.get("ticker"), value=result)
            log.info("Analyzed: ticker=%s sentiment=%s score=%.4f url=%s",
                     article.get("ticker"), result["sentiment"], result["sentimentScore"], article.get("url"))
        except Exception:
            log.exception("Failed to process message at offset %d", message.offset)


if __name__ == "__main__":
    main()

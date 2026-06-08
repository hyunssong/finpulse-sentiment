FROM python:3.12-slim

WORKDIR /app

# Install deps first (layer-cached unless requirements.txt changes)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download FinBERT at build time — avoids ~500 MB download on every cold start
RUN python -c "from transformers import pipeline; pipeline('text-classification', model='ProsusAI/finbert')"

COPY analyzer.py .

CMD ["python", "-u", "analyzer.py"]

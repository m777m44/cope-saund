FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY bot.py config.py clone_client.py storage.py ./

RUN mkdir -p data/samples
ENV BOT_DATA_DIR=/app/data

CMD ["python", "bot.py"]

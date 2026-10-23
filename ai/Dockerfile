FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
COPY ai/requirements.txt /tmp/requirements.txt
# CPU wheels keep CI builds within hosted-runner disk limits; no GPU or weights required.
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r /tmp/requirements.txt
COPY ai/app /app/ai/app
COPY ai/__init__.py /app/ai/__init__.py
ENV HOME=/tmp
USER 10001:10001
EXPOSE 5000
CMD ["uvicorn", "ai.app.main:app", "--host", "0.0.0.0", "--port", "5000"]

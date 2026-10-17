# Playwright 官方镜像已内置 Chromium 与系统依赖
FROM mcr.microsoft.com/playwright/python:v1.47.0-jammy
WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV TZ=Asia/Shanghai

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && python -m playwright install chromium

COPY . .

CMD ["python", "main.py"]

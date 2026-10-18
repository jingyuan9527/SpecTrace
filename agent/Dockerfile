# Playwright 官方镜像（noble = Ubuntu 24.04，内置 Python 3.12 与 Chromium 及系统依赖）
# 该镜像为多架构 manifest，linux/amd64 与 linux/arm64 均可直接构建
FROM mcr.microsoft.com/playwright/python:v1.63.0-noble
WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV TZ=Asia/Shanghai

COPY requirements.txt .
# playwright 版本与基础镜像一致，浏览器已内置；仍执行 install 作为兜底
RUN pip install --no-cache-dir --break-system-packages -r requirements.txt \
    && python -m playwright install chromium

COPY . .

CMD ["python", "main.py"]

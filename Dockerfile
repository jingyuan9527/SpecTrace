# ============================================================
# SpecTrace 单镜像（前后端一体）：一个 Jar 同时提供前端 SPA、API 与内嵌采集 Agent
#
# 构建上下文必须是【仓库根目录】（需同时读取 frontend/ 与 backend/）：
#   docker build -t spectrace-app .
#
# 运行（数据目录挂载到宿主机，SQLite 与上传文件都落在这里）：
#   docker run -d -p 80:8080 \
#     -e SPECTRACE_JWT_SECRET=*** \
#     -v /opt/spectrace/data:/data \
#     spectrace-app
# ============================================================

# ---- 1. 构建前端 ----
FROM --platform=$BUILDPLATFORM node:22-alpine AS frontend
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ---- 2. 构建后端，并把前端产物并入 static（最终随 SPA 打进 Jar）----
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# 前端静态文件落到 classpath:static，与 Jar 一起发布
COPY --from=frontend /ui/dist ./src/main/resources/static
RUN mvn -B -q clean package -DskipTests

# ---- 3. 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV SPECTRACE_DB_PATH=/data/spectrace.db

RUN mkdir -p /data
COPY --from=backend /build/target/spectrace-backend-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

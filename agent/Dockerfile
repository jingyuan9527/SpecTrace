# ---- 构建阶段（在构建机原生架构执行，产物与架构无关，避免跨架构模拟编译）----
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先复制 pom 以利用依赖缓存层
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
# HarnessAgent 工作区（persona 与运行期会话数据的落盘位置）
ENV AGENTSCOPE_WORKSPACE=/app/workspace
RUN mkdir -p /app/workspace

COPY --from=build /build/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

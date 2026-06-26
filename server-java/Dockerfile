# ── 4 个微服务通用 Dockerfile ──
# 使用方式:
#   mvn package -DskipTests
#   docker compose up -d

FROM eclipse-temurin:17-jre

# 创建 app 用户（非 root 运行）
RUN groupadd -r app && useradd -r -g app app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 工作目录
WORKDIR /app

# 各服务 JAR 按 ARG 参数复制
ARG SERVICE_NAME=core-service
COPY ${SERVICE_NAME}/target/${SERVICE_NAME}-1.0.0-SNAPSHOT.jar app.jar

USER app
EXPOSE 8080 8081 8082 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

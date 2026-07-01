# syntax=docker/dockerfile:1
# ============================================================================
# BPM Platform Backend — Dockerfile multi-stage (GO-LIVE)
# Stage 1: build jar bằng Maven + JDK 21 (bỏ qua test).
# Stage 2: runtime JRE 21 slim, chạy với profile 'prod'.
# Artifact (theo backend/pom.xml): bpm-platform-backend-0.1.0-SNAPSHOT.jar
# App lắng nghe cổng 8081 (server.port mặc định trong application.yml).
# ============================================================================

# ---------- Stage 1: BUILD ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency: copy pom trước, tải dependency offline, rồi mới copy source.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Bỏ qua test khi build image (CI/CD chạy test riêng).
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: RUNTIME ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Chạy bằng user không phải root.
RUN groupadd --system bpm && useradd --system --gid bpm --home /app bpm \
 && mkdir -p /app/data/media /var/log/bpm \
 && chown -R bpm:bpm /app /var/log/bpm

# curl dùng cho HEALTHCHECK.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Copy đúng jar đã build (loại .jar.original do spring-boot-maven-plugin sinh ra).
COPY --from=build /build/target/bpm-platform-backend-*.jar /app/app.jar

USER bpm

# Cổng ứng dụng (khớp server.port=8081).
EXPOSE 8081

# Tùy chỉnh JVM qua biến môi trường JAVA_OPTS nếu cần (vd: -Xmx512m).
ENV JAVA_OPTS=""

# Healthcheck dùng probe readiness của Spring Boot Actuator.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=prod"]

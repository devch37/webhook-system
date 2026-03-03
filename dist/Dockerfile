# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# 의존성 캐시 레이어: Gradle 설정 파일만 먼저 복사
COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 복사 후 JAR 빌드 (테스트 제외)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

RUN mkdir -p /app/data

COPY --from=build /app/build/libs/webhook-0.0.1-SNAPSHOT.jar app.jar

ENV DB_PATH=/app/data/webhook.db
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

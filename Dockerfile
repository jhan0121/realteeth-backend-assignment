# ── Stage 1: Build ──────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew build -x test --no-daemon

# ── Stage 2: Runtime ────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system spring && useradd --system --gid spring spring

WORKDIR /app
COPY --from=builder /workspace/build/libs/image-processing-0.0.1-SNAPSHOT.jar app.jar
RUN chown spring:spring app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]

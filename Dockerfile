# ─── Stage 1: Build ───────────────────────────────────────────────
# Use the official Maven image (comes with JDK 21 + Maven pre-configured — no apk conflicts)
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache dependencies separately so code changes don't re-download everything
COPY pom.xml .
RUN mvn -q dependency:go-offline -DskipTests

# Build the application (skip tests — they require live infrastructure)
COPY src ./src
RUN mvn -q -DskipTests package

# ─── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tessdata for OCR
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng

# Create directories for uploads and ML models
RUN mkdir -p /app/uploads /app/ml-models /app/tessdata

# Copy tessdata
RUN cp -r /usr/share/tessdata/* /app/tessdata/ 2>/dev/null || true

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 9090
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dsmartlife.storage.upload-dir=/app/uploads", \
  "-Dsmartlife.ml.models-dir=/app/ml-models", \
  "-Dsmartlife.ocr.tessdata-path=/app/tessdata", \
  "-jar", "app.jar"]

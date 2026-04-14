# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer cache for deps)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="SmartLife Hub"
LABEL org.opencontainers.image.description="AI-Powered Personal Life Management Platform"

# Non-root user for security
RUN addgroup -S smartlife && adduser -S smartlife -G smartlife

WORKDIR /app

# Copy the fat JAR
COPY --from=builder /app/target/*.jar app.jar

# Create required runtime directories
RUN mkdir -p uploads tessdata ml-models \
    && chown -R smartlife:smartlife /app

# Install Tesseract for OCR
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng

USER smartlife

EXPOSE 8080

# JVM tuning for containers (Java 21 virtual threads)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

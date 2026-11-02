# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy dependency manifest first so Docker caches the layer on pom.xml changes only
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the executable JAR
COPY src ./src
RUN mvn package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Install bash for interactive shell access
RUN apk add --no-cache bash

# Copy only the packaged JAR from the build stage
COPY --from=builder /app/target/food-ordering-system.jar app.jar

# Helper launcher script executable directly from bash
RUN printf '#!/bin/bash\nexec java -jar /app/app.jar "$@"\n' > /app/run.sh \
    && chmod +x /app/run.sh

# Non-root user for security and pre-created data directory with proper ownership
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/data && chown -R appuser:appgroup /app/data /app/run.sh
USER appuser

# Configure default storage directory inside container
ENV FOOD_ORDERING_DATA_DIR=/app/data

CMD ["java", "-jar", "app.jar"]

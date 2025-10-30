# =========================
# Build stage
# =========================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy Maven descriptor and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the JAR
COPY src/ ./src/
RUN mvn clean package -DskipTests -B

# =========================
# Runtime stage
# =========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl && \
    # Create non-root user and directories in single layer
    addgroup -S appuser && adduser -S appuser -G appuser && \
    mkdir -p /app/logs/chat_logs /app/logs/discussion_logs /app/logs/discussion_websocket_logs /app/logs/discussion_readings_logs && \
    chown -R appuser:appuser /app

# Copy the JAR from build stage
COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Health check (with graceful fallback)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health || curl -f http://localhost:8080/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
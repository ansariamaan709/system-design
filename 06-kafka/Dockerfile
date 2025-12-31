# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create kafka user
RUN addgroup -g 1000 kafka && adduser -u 1000 -G kafka -s /bin/sh -D kafka

# Create log directory
RUN mkdir -p /var/kafka-logs && chown -R kafka:kafka /var/kafka-logs

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R kafka:kafka /app

USER kafka

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080 9092

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

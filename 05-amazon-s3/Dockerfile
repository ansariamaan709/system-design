# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN chmod +x ./mvnw
RUN ./mvnw package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 s3user && \
    adduser -u 1000 -G s3user -D s3user

# Create storage directories
RUN mkdir -p /app/data/storage /app/data/temp && \
    chown -R s3user:s3user /app/data

COPY --from=builder /app/target/*.jar app.jar
RUN chown s3user:s3user app.jar

USER s3user

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:9000/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

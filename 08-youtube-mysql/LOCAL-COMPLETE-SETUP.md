# YouTube Project - Complete Local Setup Guide

This guide walks you through setting up and running the entire YouTube project locally on Windows, assuming all services are installed locally (MySQL, Redis, Kafka, ELK).

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Service Setup](#local-service-setup)
3. [Project Configuration](#project-configuration)
4. [Running the Application](#running-the-application)
5. [Verification Steps](#verification-steps)
6. [Troubleshooting](#troubleshooting)
7. [Integration with ELK Stack](#integration-with-elk-stack)

---

## Prerequisites

Ensure the following are installed and available on your local machine:

- **Java 17+**: Required for the Spring Boot application
  ```powershell
  java -version
  ```
- **Maven 3.8+**: For building and running the application
  ```powershell
  mvn -version
  ```
- **MySQL 8.0+**: Installed locally or running as service
  ```powershell
  mysql --version
  ```
- **Redis 7+**: Installed locally or running as service
  ```powershell
  redis-cli --version
  ```
- **Kafka**: Downloaded and extracted locally
- **ELK Stack** (Elasticsearch, Logstash, Kibana): Installed locally
- **Git**: For cloning/managing the repository

---

## Local Service Setup

### 1. MySQL Configuration

MySQL stores all application data (users, videos, comments, channels, etc.).

**Verify MySQL is running:**

```powershell
mysql -u root -p -h localhost -e "SELECT 1;"
```

**Create the database and user:**

```powershell
mysql -u root -p -h localhost
```

Then execute:

```sql
CREATE DATABASE IF NOT EXISTS youtube;
CREATE USER IF NOT EXISTS 'youtube'@'localhost' IDENTIFIED BY 'youtube_password';
GRANT ALL PRIVILEGES ON youtube.* TO 'youtube'@'localhost';
FLUSH PRIVILEGES;
```

**Verify connection:**

```powershell
mysql -u youtube -pyoutube_password -h localhost youtube -e "SELECT 1;"
```

**Database schema is auto-loaded** from `src/main/resources/schema.sql` when the application starts (via Spring Boot's `spring.jpa.hibernate.ddl-auto` setting).

---

### 2. Redis Configuration

Redis caches video data, view counts, and session information.

**Verify Redis is running:**

```powershell
redis-cli ping
# Expected output: PONG
```

**Check Redis configuration:**

```powershell
redis-cli INFO server
```

**Key Redis config** (if running locally, defaults usually work):

- Port: `6379`
- Maxmemory: `256mb` (adjust as needed)
- Eviction policy: `allkeys-lru`

---

### 3. Kafka Configuration

Kafka handles event streaming (video uploads, likes, comments, view events).

**Download Kafka locally:**

- Download Kafka 3.5+ from [https://kafka.apache.org/downloads](https://kafka.apache.org/downloads)
- Extract to `C:\kafka` (or your preferred location)

**Start Zookeeper (required for Kafka):**

```powershell
cd C:\kafka
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
```

Wait for the message: `[QuorumPeer] ... Ready to serve requests`

**In a new PowerShell window, start Kafka broker:**

```powershell
cd C:\kafka
.\bin\windows\kafka-server-start.bat .\config\server.properties
```

Wait for the message: `[KafkaServer] ... started (kafka.server.KafkaServer)`

**Create required Kafka topics** (in another PowerShell window):

```powershell
cd C:\kafka

# Create topics
.\bin\windows\kafka-topics.bat --create --bootstrap-server localhost:9092 --topic video-upload-events --partitions 4 --replication-factor 1 --if-not-exists
.\bin\windows\kafka-topics.bat --create --bootstrap-server localhost:9092 --topic video-like-events --partitions 4 --replication-factor 1 --if-not-exists
.\bin\windows\kafka-topics.bat --create --bootstrap-server localhost:9092 --topic comment-events --partitions 4 --replication-factor 1 --if-not-exists
.\bin\windows\kafka-topics.bat --create --bootstrap-server localhost:9092 --topic view-events --partitions 8 --replication-factor 1 --if-not-exists
.\bin\windows\kafka-topics.bat --create --bootstrap-server localhost:9092 --topic notification-events --partitions 4 --replication-factor 1 --if-not-exists

# Verify topics
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

**Kafka listening on:**

- Bootstrap Server: `localhost:9092`
- Zookeeper: `localhost:2181`

---

### 4. ELK Stack (Local Installation)

Refer to the separate [ELK-SETUP.md](./ELK-SETUP.md) for detailed local installation steps.

**Quick summary:**

- Download and extract Elasticsearch, Logstash, and Kibana to `C:\elk\`
- Start Elasticsearch:
  ```powershell
  C:\elk\elasticsearch\bin\elasticsearch.bat
  ```
- Start Logstash (in new window):
  ```powershell
  C:\elk\logstash\bin\logstash.bat -f C:\elk\logstash\config\logstash.conf
  ```
- Start Kibana (in new window):
  ```powershell
  C:\elk\kibana\bin\kibana.bat
  ```

**ELK Services:**

- Elasticsearch: `http://localhost:9200`
- Logstash: `localhost:5044` (listens for logs)
- Kibana: `http://localhost:5601`

---

## Project Configuration

### 1. Application Configuration File

Navigate to the project root and edit `src/main/resources/application-local.yml`:

```yaml
# src/main/resources/application-local.yml

spring:
  application:
    name: youtube

  # Database Configuration
  datasource:
    url: jdbc:mysql://localhost:3306/youtube
    username: youtube
    password: youtube_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate # or 'update' if you want auto schema updates
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect

  # Redis Configuration
  redis:
    host: localhost
    port: 6379
    timeout: 10000

  # Kafka Configuration
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      bootstrap-servers: localhost:9092
      group-id: youtube-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"

# Server Configuration
server:
  port: 8080
  servlet:
    context-path: /api

# Logging Configuration (output logs to Logstash)
logging:
  level:
    root: INFO
    com.youtube: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/youtube.log

# Metrics & Monitoring (optional, for Prometheus)
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

# Application-specific properties
app:
  jwt:
    secret: your_jwt_secret_key_here
    expiration: 86400000
  cache:
    ttl: 3600
```

### 2. Set Active Profile

When running the application, activate the `local` profile:

```powershell
# Via environment variable
$env:SPRING_PROFILES_ACTIVE = "local"

# Or via Maven parameter (see Running the Application section)
```

---

## Running the Application

### Option 1: Maven (Recommended for Development)

From the project root:

```powershell
# Build the project
mvn clean install

# Run with local profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

**Application starts at:** `http://localhost:8080/api`

### Option 2: JAR Execution

```powershell
# Build JAR
mvn clean package

# Run JAR
java -jar target/youtube-application-1.0.0.jar --spring.profiles.active=local
```

### Option 3: IDE (IntelliJ IDEA / Eclipse)

1. Open the project in your IDE
2. Edit Run Configuration:
   - Set VM options: `-Dspring.profiles.active=local`
   - Or set Environment variable: `SPRING_PROFILES_ACTIVE=local`
3. Run the application from IDE

---

## Startup Order (Important!)

To avoid connection errors, start services in this order:

1. **MySQL** (must be running)

   ```powershell
   # Windows Service or command
   mysql --user=root --password
   ```

2. **Redis** (must be running)

   ```powershell
   redis-server
   ```

3. **Zookeeper** (required for Kafka)

   ```powershell
   cd C:\kafka
   .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
   ```

4. **Kafka** (in separate window)

   ```powershell
   cd C:\kafka
   .\bin\windows\kafka-server-start.bat .\config\server.properties
   ```

5. **ELK Stack** (optional but recommended)

   - Elasticsearch
   - Logstash
   - Kibana

6. **YouTube Application** (last)
   ```powershell
   mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
   ```

---

## Verification Steps

### 1. Verify MySQL Connection

```powershell
mysql -u youtube -pyoutube_password -h localhost youtube -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='youtube';"
```

### 2. Verify Redis Connection

```powershell
redis-cli ping
# Expected: PONG
```

### 3. Verify Kafka is Running

```powershell
cd C:\kafka
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
# Should list topics: video-upload-events, video-like-events, etc.
```

### 4. Verify Application Started

Check console output for:

```
... Started YouTubeApplication in X.XXX seconds ...
```

Test API endpoint:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get
```

### 5. Verify ELK Stack

- Elasticsearch:
  ```powershell
  Invoke-RestMethod http://localhost:9200
  ```
- Kibana:
  ```powershell
  Invoke-RestMethod http://localhost:5601/status
  ```
- Logstash (port check):
  ```powershell
  Test-NetConnection -ComputerName localhost -Port 5044
  ```

---

## Integration with ELK Stack

### Send Application Logs to Logstash

If you want application logs shipped to your local ELK stack:

#### Option 1: Use Filebeat (Recommended)

1. **Download Filebeat**: [https://www.elastic.co/downloads/beats/filebeat](https://www.elastic.co/downloads/beats/filebeat)
   Extract to `C:\elk\filebeat`

2. **Create `filebeat.yml`** in `C:\elk\filebeat\`:

   ```yaml
   filebeat.inputs:
     - type: log
       enabled: true
       paths:
         - C:\path\to\project\logs\*.log

   output.logstash:
     hosts: ["localhost:5044"]

   logging.level: info
   ```

3. **Start Filebeat**:

   ```powershell
   cd C:\elk\filebeat
   .\filebeat.exe -e -c filebeat.yml
   ```

4. **View logs in Kibana**: Open `http://localhost:5601` → Discover → Select index pattern `youtube-logs-*`

#### Option 2: Direct Logback Integration

Add to `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <!-- Console appender -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Logstash TCP appender (requires logstash-logback-encoder dependency) -->
  <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:5044</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeContext>true</includeContext>
      <includeMdcKeyName>userId</includeMdcKeyName>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="LOGSTASH"/>
  </root>

  <logger name="com.youtube" level="DEBUG"/>
</configuration>
```

Add dependency to `pom.xml`:

```xml
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

---

## Troubleshooting

### Issue: MySQL Connection Refused

**Solution:**

- Verify MySQL is running: `mysql --version`
- Check credentials in `application-local.yml`
- Ensure database `youtube` exists and user `youtube` has access
- Test manually: `mysql -u youtube -pyoutube_password -h localhost youtube`

### Issue: Redis Connection Refused

**Solution:**

- Verify Redis is running: `redis-cli ping`
- Check Redis port: `redis-cli -p 6379`
- Ensure Redis listening on `127.0.0.1:6379`

### Issue: Kafka Topics Not Found

**Solution:**

- Verify Zookeeper is running
- Verify Kafka broker is running
- Check bootstrap server: `localhost:9092`
- Create topics manually (see Kafka Configuration section)

### Issue: Application Fails to Start

**Solution:**

- Check console logs for errors
- Verify all services are running (MySQL, Redis, Kafka)
- Ensure `application-local.yml` exists and is valid YAML
- Check Java version: `java -version` (must be 17+)
- Check Maven: `mvn -version`

### Issue: Logstash Not Receiving Logs

**Solution:**

- Verify Logstash is running on `localhost:5044`
- Check Logstash logs for errors
- Verify `logstash.conf` is valid and points to Elasticsearch
- Ensure Elasticsearch is running and accessible

### Issue: Out of Memory (Elasticsearch)

**Solution:**

- Increase Elasticsearch heap size in `elasticsearch.bat`:
  ```powershell
  set ES_JAVA_OPTS=-Xms1g -Xmx1g
  ```
- Adjust based on available system memory

---

## Quick Command Reference

| Service       | Start Command                                                                      | Port | Status Check                                                              |
| ------------- | ---------------------------------------------------------------------------------- | ---- | ------------------------------------------------------------------------- |
| MySQL         | `mysql.exe` / Windows Service                                                      | 3306 | `mysql -u youtube -pyoutube_password -h localhost youtube -e "SELECT 1;"` |
| Redis         | `redis-server`                                                                     | 6379 | `redis-cli ping`                                                          |
| Zookeeper     | `C:\kafka\bin\windows\zookeeper-server-start.bat`                                  | 2181 | Check console logs                                                        |
| Kafka         | `C:\kafka\bin\windows\kafka-server-start.bat`                                      | 9092 | `kafka-topics --list --bootstrap-server localhost:9092`                   |
| Elasticsearch | `C:\elk\elasticsearch\bin\elasticsearch.bat`                                       | 9200 | `Invoke-RestMethod http://localhost:9200`                                 |
| Logstash      | `C:\elk\logstash\bin\logstash.bat -f config\logstash.conf`                         | 5044 | `Test-NetConnection localhost 5044`                                       |
| Kibana        | `C:\elk\kibana\bin\kibana.bat`                                                     | 5601 | Open `http://localhost:5601`                                              |
| YouTube App   | `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"` | 8080 | Open `http://localhost:8080/api/health`                                   |

---

## Next Steps

1. Follow the **Startup Order** section to start all services
2. Configure `application-local.yml` with your local paths/credentials
3. Run the application using Maven or your IDE
4. Test API endpoints using Postman or cURL
5. (Optional) Set up Filebeat to ship logs to local Kibana
6. Monitor application metrics in Kibana dashboards

---

For questions or issues, refer to the main [README.md](./README.md), [ELK-SETUP.md](./ELK-SETUP.md), or [LOCAL-SETUP.md](./LOCAL-SETUP.md).

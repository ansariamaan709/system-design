# Stock Exchange - Local Development Setup

This guide will help you set up and run the Stock Exchange application locally on Windows.

## Prerequisites

- **Java 21+** (with JAVA_HOME configured)
- **Maven 3.8+** (or use included mvnw wrapper)
- **Docker Desktop** (for running infrastructure services)
- **PostgreSQL client** (optional, for psql commands)

## Quick Start

### 1. Start Infrastructure Services

```powershell
# Start PostgreSQL, Redis, and Kafka
.\start-services.ps1
```

This will start:

- **PostgreSQL** on port 5432
- **Redis** on port 6379
- **Kafka** on port 9092

### 2. Initialize Database

```powershell
# Create database and run schema
.\setup-database.ps1
```

This creates:

- `stockexchange` database
- All required tables
- Sample instruments (AAPL, GOOGL, MSFT, etc.)
- Demo client and account

### 3. Run the Application

```powershell
# Build and run with local profile
.\run-local.ps1
```

The application will be available at:

- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/prometheus

### 4. Stop Services

```powershell
.\stop-services.ps1
```

## API Quick Reference

### Authentication Headers

All API requests require these headers:

```
X-Client-Id: 1
X-Account-Id: 1
```

### Place an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: 1" \
  -H "X-Account-Id: 1" \
  -d '{
    "symbol": "AAPL",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 100,
    "price": 175.00,
    "timeInForce": "DAY"
  }'
```

### Get Order Book

```bash
curl http://localhost:8080/api/v1/market-data/depth/AAPL?levels=5
```

### Get Account Positions

```bash
curl http://localhost:8080/api/v1/positions \
  -H "X-Account-Id: 1"
```

### Cancel Order

```bash
curl -X DELETE http://localhost:8080/api/v1/orders/123 \
  -H "X-Client-Id: 1"
```

## WebSocket Connections

### Raw WebSocket (Low Latency)

Connect to: `ws://localhost:8080/ws/market-data`

Subscribe to market data:

```json
{
  "type": "SUBSCRIBE",
  "symbol": "AAPL"
}
```

### STOMP over WebSocket

Connect to: `ws://localhost:8080/ws/stomp`

Topics:

- `/topic/quotes/{symbol}` - Level 1 quotes
- `/topic/trades/{symbol}` - Trade ticks
- `/topic/depth/{symbol}` - Order book depth
- `/topic/ticker/{symbol}` - Ticker updates

## Development

### Run with Debug

```powershell
.\run-local.ps1 -Debug
```

Debugger will listen on port 5005.

### Skip Build

```powershell
.\run-local.ps1 -SkipBuild
```

### Manual Build

```powershell
.\mvnw.cmd clean package -DskipTests
```

### Run Tests

```powershell
.\mvnw.cmd test
```

## Configuration

### Database Connection

Edit `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/stockexchange
    username: postgres
    password: postgres
```

### Environment Variables

| Variable      | Default        | Description             |
| ------------- | -------------- | ----------------------- |
| DB_HOST       | localhost      | PostgreSQL host         |
| DB_PORT       | 5432           | PostgreSQL port         |
| DB_NAME       | stockexchange  | Database name           |
| DB_USERNAME   | postgres       | Database user           |
| DB_PASSWORD   | postgres       | Database password       |
| REDIS_HOST    | localhost      | Redis host              |
| REDIS_PORT    | 6379           | Redis port              |
| KAFKA_SERVERS | localhost:9092 | Kafka bootstrap servers |

## Troubleshooting

### Port Already in Use

```powershell
# Find process using port
netstat -ano | findstr :8080

# Kill process by PID
taskkill /PID <pid> /F
```

### Database Connection Failed

1. Check PostgreSQL is running: `docker ps`
2. Verify credentials in application-local.yml
3. Test connection: `psql -h localhost -U postgres -d stockexchange`

### Redis Connection Failed

1. Check Redis is running: `docker ps`
2. Test connection: `redis-cli ping`

### Kafka Not Available

The application will still run without Kafka, but trade events won't be published.

## Sample Test Data

After setup, you'll have:

**Clients:**
| ID | Email | Type | API Key |
|----|-------|------|---------|
| 1 | demo@stockexchange.com | RETAIL | demo-api-key-12345 |
| 2 | market-maker@stockexchange.com | MARKET_MAKER | mm-api-key-12345 |

**Instruments:**
| Symbol | Name | Type | Previous Close |
|--------|------|------|----------------|
| AAPL | Apple Inc. | STOCK | 175.50 |
| GOOGL | Alphabet Inc. | STOCK | 141.25 |
| MSFT | Microsoft Corporation | STOCK | 378.90 |
| TSLA | Tesla Inc. | STOCK | 248.75 |
| BTC-USD | Bitcoin | CRYPTO | 67500.00 |

**Demo Account:**

- Account ID: 1
- Cash Balance: $100,000
- Buying Power: $100,000

# 📊 AOP Logging & Monitoring Guide

## Overview

This application uses **Aspect-Oriented Programming (AOP)** to provide comprehensive logging and performance monitoring.

---

## 🎯 Log Symbols & Their Meaning

### Cache Operations

- `⚡ [REDIS CACHE HIT]` - Data found in Redis cache (FAST - ~1-10ms)
- `❌ [REDIS CACHE MISS]` - Data NOT in cache, will query database
- `💾 [REDIS CACHE WRITE]` - Data stored in Redis cache
- `🗑️  [REDIS CACHE EVICT]` - Data removed from Redis cache

### Database Operations

- `🗄️  [DATABASE]` - PostgreSQL query started
- `✅ [DATABASE HIT]` - Data found in PostgreSQL (~50-200ms)
- `❌ [DATABASE]` - Database query failed

### Service Layer

- `🔷 [SERVICE]` - Service method execution
- `✅ [SERVICE]` - Service method completed successfully
- `❌ [SERVICE]` - Service method failed

### Performance

- `⚠️  [SLOW OPERATION]` - Operation took > 1 second
- `📍 [REQUEST]` - Incoming request

---

## 📝 Example Log Output

### Scenario 1: URL Found in Cache (Fast Path)

```
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  UrlService - 📍 [REQUEST] Resolving short code: aB3xY9z
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  LoggingAspect - ⚡ [REDIS CACHE HIT] getCachedUrl in 2ms - Key: aB3xY9z
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  UrlService - ⚡ [CACHE HIT] Found URL in Redis cache for: aB3xY9z → https://github.com
```

**Total time: ~2-5ms** ⚡

---

### Scenario 2: URL NOT in Cache (Database Query)

```
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  UrlService - 📍 [REQUEST] Resolving short code: aB3xY9z
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  LoggingAspect - ❌ [REDIS CACHE MISS] getCachedUrl in 1ms - Key: aB3xY9z
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  UrlService - ❌ [CACHE MISS] URL not in cache, querying PostgreSQL for: aB3xY9z
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  LoggingAspect - 🗄️  [DATABASE] Query: findActiveByShortCode(..) with params: [aB3xY9z]
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  LoggingAspect - ✅ [DATABASE] Query completed in 45ms - Result: Found
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  UrlService - ✅ [DATABASE HIT] Found in PostgreSQL: aB3xY9z → https://github.com
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  LoggingAspect - 💾 [REDIS CACHE WRITE] cacheUrl in 3ms - Key: aB3xY9z
2025-12-29 22:00:00 [http-nio-8080-exec-1] INFO  UrlService - 💾 [CACHE UPDATE] Stored in Redis cache: aB3xY9z
```

**Total time: ~50-100ms** 🗄️

---

## 🔧 AOP Aspects Implemented

### 1. LoggingAspect

**Location:** `com.urlshortener.aspect.LoggingAspect`

**What it monitors:**

- ✅ All service method calls
- ✅ All database repository queries
- ✅ All Redis cache operations
- ✅ Execution time for each operation
- ✅ Success/failure status

**Pointcuts:**

```java
@Pointcut("execution(* com.urlshortener.service..*(..))")  // All services
@Pointcut("execution(* com.urlshortener.repository..*(..))")  // All repos
@Pointcut("execution(* com.urlshortener.service.UrlCacheService.*(..))")  // Cache
```

---

### 2. PerformanceMonitoringAspect

**Location:** `com.urlshortener.aspect.PerformanceMonitoringAspect`

**What it monitors:**

- ✅ All controller endpoint execution times
- ✅ Slow operations (>1 second warning)
- ✅ Micrometer metrics for grafana/prometheus

**Metrics exposed:**

- `controller.method.execution` - Tagged by class, method, status

---

## 📈 Performance Comparison

| Operation             | Cache Hit              | Cache Miss (DB Query) |
| --------------------- | ---------------------- | --------------------- |
| **Latency**           | 1-10ms ⚡              | 50-200ms 🗄️           |
| **Speed Improvement** | **10-100x faster**     | Baseline              |
| **Log Symbol**        | `⚡ [REDIS CACHE HIT]` | `🗄️ [DATABASE]`       |

---

## 🎨 How to Read Logs

### 1. Check Data Source

Look for these symbols to know where data came from:

- `⚡` = Redis (Lightning fast!)
- `🗄️` = PostgreSQL (Database query)

### 2. Check Execution Time

Every log shows execution time in milliseconds:

```
⚡ [REDIS CACHE HIT] getCachedUrl in 2ms
🗄️ [DATABASE] Query completed in 45ms
```

### 3. Track Request Flow

Follow a single request by matching timestamps:

```
22:00:00.001 - 📍 [REQUEST] Resolving...
22:00:00.003 - ⚡ [CACHE HIT] Found...
22:00:00.005 - ✅ [SERVICE] Completed...
```

---

## 🔍 Troubleshooting

### Too many cache misses?

```
❌ [REDIS CACHE MISS] appearing frequently
```

**Solution:** Check Redis connection, increase cache TTL

### Slow database queries?

```
🗄️ [DATABASE] Query completed in 500ms
⚠️ [SLOW OPERATION] took 1200ms
```

**Solution:** Check indexes, optimize queries

### Cache not working?

```
No ⚡ [CACHE HIT] logs, only database queries
```

**Solution:** Verify Redis is running, check cache configuration

---

## 📊 Enabling/Disabling Logs

### Set log levels in `application.yml`:

```yaml
logging:
  level:
    com.urlshortener.aspect: DEBUG # AOP logging
    com.urlshortener.service: INFO # Service logs
    com.urlshortener.repository: INFO # Database logs
```

### Production mode (less verbose):

```yaml
logging:
  level:
    com.urlshortener.aspect: WARN
    com.urlshortener.service: WARN
```

---

## 🎯 Best Practices

1. **Monitor cache hit ratio** - Should be >80% for hot URLs
2. **Watch for slow operations** - Investigate anything >1 second
3. **Track database queries** - Minimize database hits with caching
4. **Use log symbols** - Quick visual identification of data source

---

## 📚 Additional Resources

- **AOP Documentation:** Spring AOP Reference
- **Micrometer Metrics:** Available at `/actuator/metrics`
- **Health Checks:** Available at `/actuator/health`

package com.urlshortener.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * AOP Aspect for logging method execution, performance, and data source
 * tracking
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Pointcut for all service layer methods
     */
    @Pointcut("execution(* com.urlshortener.service..*(..))")
    public void serviceMethods() {
    }

    /**
     * Pointcut for all repository layer methods
     */
    @Pointcut("execution(* com.urlshortener.repository..*(..))")
    public void repositoryMethods() {
    }

    /**
     * Pointcut for cache service methods
     */
    @Pointcut("execution(* com.urlshortener.service.UrlCacheService.*(..))")
    public void cacheMethods() {
    }

    /**
     * Around advice for service methods - logs execution time and parameters
     */
    @Around("serviceMethods() && !cacheMethods()")
    public Object logServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        long startTime = System.currentTimeMillis();

        try {
            log.debug("[SERVICE] Executing: {} with args: {}", methodName,
                    args.length > 0 ? Arrays.toString(args) : "none");

            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("[SERVICE] Completed: {} in {}ms", methodName, executionTime);

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[SERVICE] Failed: {} after {}ms - Error: {}",
                    methodName, executionTime, e.getMessage());
            throw e;
        }
    }

    /**
     * Around advice for repository methods - tracks database access
     */
    @Around("repositoryMethods()")
    public Object logDatabaseAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        long startTime = System.currentTimeMillis();

        try {
            log.info("[DATABASE] Query: {} with params: {}",
                    methodName, args.length > 0 ? Arrays.toString(args) : "none");

            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[DATABASE] Query completed in {}ms - Result: {}",
                    executionTime, result != null ? "Found" : "Not Found");

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[DATABASE] Query failed after {}ms - Error: {}",
                    executionTime, e.getMessage());
            throw e;
        }
    }

    /**
     * Around advice for cache methods - tracks cache hits/misses
     */
    @Around("cacheMethods()")
    public Object logCacheAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        long startTime = System.currentTimeMillis();

        try {
            if (methodName.startsWith("get") || methodName.startsWith("find")) {
                // Cache read operation
                Object result = joinPoint.proceed();
                long executionTime = System.currentTimeMillis() - startTime;

                if (result != null) {
                    log.info("[REDIS CACHE HIT] {} in {}ms - Key: {}",
                            methodName, executionTime, args.length > 0 ? args[0] : "unknown");
                } else {
                    log.info("[REDIS CACHE MISS] {} in {}ms - Key: {}",
                            methodName, executionTime, args.length > 0 ? args[0] : "unknown");
                }

                return result;
            } else if (methodName.startsWith("cache") || methodName.startsWith("save")
                    || methodName.startsWith("put")) {
                // Cache write operation
                Object result = joinPoint.proceed();
                long executionTime = System.currentTimeMillis() - startTime;

                log.info("[REDIS CACHE WRITE] {} in {}ms - Key: {}",
                        methodName, executionTime, args.length > 0 ? args[0] : "unknown");

                return result;
            } else if (methodName.startsWith("evict") || methodName.startsWith("delete")
                    || methodName.startsWith("remove")) {
                // Cache eviction operation
                Object result = joinPoint.proceed();
                long executionTime = System.currentTimeMillis() - startTime;

                log.info("[REDIS CACHE EVICT] {} in {}ms - Key: {}",
                        methodName, executionTime, args.length > 0 ? args[0] : "unknown");

                return result;
            } else {
                // Other cache operations
                Object result = joinPoint.proceed();
                long executionTime = System.currentTimeMillis() - startTime;

                log.debug("[REDIS CACHE] {} in {}ms", methodName, executionTime);

                return result;
            }
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[REDIS CACHE ERROR] {} after {}ms - Error: {}",
                    methodName, executionTime, e.getMessage());
            throw e;
        }
    }
}

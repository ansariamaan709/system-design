package com.urlshortener.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AOP Aspect for performance monitoring and metrics
 */
@Aspect
@Component
@Slf4j
public class PerformanceMonitoringAspect {

    private final MeterRegistry meterRegistry;

    public PerformanceMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Monitor controller endpoint performance
     */
    @Around("execution(* com.urlshortener.controller..*(..))")
    public Object monitorControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Object result = joinPoint.proceed();

            sample.stop(Timer.builder("controller.method.execution")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("status", "success")
                    .register(meterRegistry));

            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("controller.method.execution")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("status", "error")
                    .register(meterRegistry));
            throw e;
        }
    }

    /**
     * Monitor slow operations (>1 second)
     */
    @Around("execution(* com.urlshortener.service..*(..))")
    public Object detectSlowOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            if (executionTime > 1000) {
                log.warn("[SLOW OPERATION] {} took {}ms (threshold: 1000ms)",
                        joinPoint.getSignature().toShortString(), executionTime);
            }

            return result;
        } catch (Exception e) {
            throw e;
        }
    }
}

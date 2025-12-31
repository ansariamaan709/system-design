package com.stockexchange.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration for performance monitoring.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    // Custom metrics can be injected where needed
    @Bean
    public ExchangeMetrics exchangeMetrics(MeterRegistry registry) {
        return new ExchangeMetrics(registry);
    }

    public static class ExchangeMetrics {
        private final MeterRegistry registry;

        // Order metrics
        private final Timer orderSubmitTimer;
        private final Timer orderMatchTimer;
        private final Timer orderCancelTimer;

        // Market data metrics
        private final Timer marketDataLatencyTimer;

        public ExchangeMetrics(MeterRegistry registry) {
            this.registry = registry;

            this.orderSubmitTimer = Timer.builder("exchange.order.submit")
                    .description("Order submission latency")
                    .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                    .register(registry);

            this.orderMatchTimer = Timer.builder("exchange.order.match")
                    .description("Order matching latency")
                    .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                    .register(registry);

            this.orderCancelTimer = Timer.builder("exchange.order.cancel")
                    .description("Order cancellation latency")
                    .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                    .register(registry);

            this.marketDataLatencyTimer = Timer.builder("exchange.marketdata.latency")
                    .description("Market data publication latency")
                    .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                    .register(registry);
        }

        public Timer getOrderSubmitTimer() {
            return orderSubmitTimer;
        }

        public Timer getOrderMatchTimer() {
            return orderMatchTimer;
        }

        public Timer getOrderCancelTimer() {
            return orderCancelTimer;
        }

        public Timer getMarketDataLatencyTimer() {
            return marketDataLatencyTimer;
        }

        public void recordOrderCount(String symbol, String side, String status) {
            registry.counter("exchange.orders.total",
                    "symbol", symbol,
                    "side", side,
                    "status", status).increment();
        }

        public void recordTradeVolume(String symbol, long quantity) {
            registry.counter("exchange.trades.volume", "symbol", symbol)
                    .increment(quantity);
        }

        public void recordTradeValue(String symbol, double value) {
            registry.counter("exchange.trades.value", "symbol", symbol)
                    .increment(value);
        }

        public void recordActiveOrders(String symbol, long count) {
            registry.gauge("exchange.orders.active",
                    java.util.List.of(io.micrometer.core.instrument.Tag.of("symbol", symbol)),
                    count);
        }

        public void recordWebSocketConnections(long count) {
            registry.gauge("exchange.websocket.connections", count);
        }
    }
}

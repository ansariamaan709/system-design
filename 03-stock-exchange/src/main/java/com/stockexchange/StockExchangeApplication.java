package com.stockexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class StockExchangeApplication {

    public static void main(String[] args) {
        // Set system properties for low-latency
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("sun.nio.ch.disableSystemWideOverlappingFileLockCheck", "true");

        SpringApplication.run(StockExchangeApplication.class, args);
    }
}

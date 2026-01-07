package com.youtube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * YouTube MySQL Application
 * 
 * Demonstrates how YouTube scaled MySQL to handle 2.49 billion users
 * using Vitess-style sharding, connection pooling, and caching strategies.
 */
@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableAsync
@EnableScheduling
public class YouTubeApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouTubeApplication.class, args);
    }
}

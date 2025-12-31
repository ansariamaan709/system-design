package com.amazons3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Amazon S3 Clone - Object Storage System
 * 
 * A production-grade distributed object storage system implementing
 * the S3 API specification for buckets, objects, and multipart uploads.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class S3Application {

    public static void main(String[] args) {
        SpringApplication.run(S3Application.class, args);
    }
}

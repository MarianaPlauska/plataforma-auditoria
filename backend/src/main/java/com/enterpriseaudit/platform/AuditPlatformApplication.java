package com.enterpriseaudit.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuditPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditPlatformApplication.class, args);
    }
}

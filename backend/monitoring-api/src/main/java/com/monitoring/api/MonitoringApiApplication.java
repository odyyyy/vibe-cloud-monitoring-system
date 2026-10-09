package com.monitoring.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.monitoring")
@EntityScan(basePackages = "com.monitoring")
@EnableJpaRepositories(basePackages = "com.monitoring")
public class MonitoringApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitoringApiApplication.class, args);
    }
}
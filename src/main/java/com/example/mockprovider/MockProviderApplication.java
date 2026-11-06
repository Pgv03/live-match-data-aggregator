package com.example.mockprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MockProviderApplication {
    public static void main(String[] args) {
        // Load mock-provider.properties instead of application.properties
        System.setProperty("spring.config.name", "mock-provider");
        SpringApplication.run(MockProviderApplication.class, args);
    }
}
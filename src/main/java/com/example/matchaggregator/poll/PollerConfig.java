package com.example.matchaggregator.poll;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PollerProperties.class)
public class PollerConfig {}
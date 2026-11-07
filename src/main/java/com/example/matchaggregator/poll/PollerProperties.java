package com.example.matchaggregator.poll;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "poller")
public record PollerProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("5s") Duration interval,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("http://localhost:8081") String providerBBaseUrl) {}
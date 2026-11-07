package com.example.matchaggregator.poll;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderBCommentary(String provider, String providerMatchId, List<ItemBody> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemBody(String ballId, long sequence, int revision, int innings,
                           int over, int ball, String text, Instant occurredAt) {}
}
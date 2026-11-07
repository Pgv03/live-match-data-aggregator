package com.example.matchaggregator.poll;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderBScore(String provider, String providerMatchId, SnapshotBody snapshot) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapshotBody(long sequence, int revision, String status, int innings,
                               int runs, int wickets, String overs, Instant asOf) {}
}
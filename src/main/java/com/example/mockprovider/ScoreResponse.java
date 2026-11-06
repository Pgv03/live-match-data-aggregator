package com.example.mockprovider;

import java.time.Instant;

public record ScoreResponse(String provider, String providerMatchId, Snapshot snapshot) {
    public record Snapshot(long sequence, int revision, String status, int innings,
                           int runs, int wickets, String overs, Instant asOf) {}
}
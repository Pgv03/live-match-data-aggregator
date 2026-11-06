package com.example.matchaggregator.model;

import java.time.Instant;

public record Snapshot(long sequence, int revision, String status, int innings,
                       int runs, int wickets, String overs, Instant asOf) {}
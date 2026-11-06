package com.example.mockprovider;

import java.time.Instant;
import java.util.List;

public record CommentaryResponse(String provider, String providerMatchId, List<Item> items) {
    public record Item(String ballId, long sequence, int revision, int innings,
                       int over, int ball, String text, Instant occurredAt) {}
}
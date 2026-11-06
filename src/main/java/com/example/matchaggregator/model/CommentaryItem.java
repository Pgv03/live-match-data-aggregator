package com.example.matchaggregator.model;

import java.time.Instant;

public record CommentaryItem(String ballId, long sequence, int revision, int innings,
                             int over, int ball, String text, Instant occurredAt) {}
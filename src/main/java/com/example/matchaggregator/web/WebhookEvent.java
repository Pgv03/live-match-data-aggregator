package com.example.matchaggregator.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record WebhookEvent(
        @NotBlank String provider,
        @NotBlank String deliveryId,
        @NotNull Instant occurredAt,
        @NotBlank String providerMatchId,
        @Valid SnapshotPayload snapshot,
        @Valid CommentaryPayload commentary) {

    public record SnapshotPayload(
            @Min(0) long sequence, @Min(1) int revision, @NotBlank String status,
            @Min(1) int innings, @Min(0) int runs, @Min(0) int wickets, @NotBlank String overs) {}

    public record CommentaryPayload(
            @NotBlank String ballId, @Min(0) long sequence, @Min(1) int revision,
            int innings, int over, int ball, @NotBlank String text) {}
}
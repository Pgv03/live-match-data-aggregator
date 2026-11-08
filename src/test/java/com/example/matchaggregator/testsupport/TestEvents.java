package com.example.matchaggregator.testsupport;

import com.example.matchaggregator.web.WebhookEvent;
import com.example.matchaggregator.web.WebhookEvent.CommentaryPayload;
import com.example.matchaggregator.web.WebhookEvent.SnapshotPayload;

import java.time.Instant;

public final class TestEvents {
    private TestEvents() {}

    public static final String MATCH = "match-1001";
    public static final String PROVIDER_A = "provider-a";

    public static SnapshotPayload snap(long seq, int rev, String status, int runs, int wickets, String overs) {
        return new SnapshotPayload(seq, rev, status, 1, runs, wickets, overs);
    }

    public static SnapshotPayload live(long seq, int rev, int runs, int wickets, String overs) {
        return snap(seq, rev, "live", runs, wickets, overs);
    }

    public static CommentaryPayload ball(String id, long seq, int rev, String text) {
        return new CommentaryPayload(id, seq, rev, 1, 0, 1, text);
    }

    public static WebhookEvent event(String deliveryId, SnapshotPayload s, CommentaryPayload c) {
        return new WebhookEvent(PROVIDER_A, deliveryId, Instant.parse("2026-10-22T14:03:11Z"),
                "a-match-1001", s, c);
    }
}
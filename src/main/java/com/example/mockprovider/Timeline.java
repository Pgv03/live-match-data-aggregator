package com.example.mockprovider;

import com.example.mockprovider.CommentaryResponse.Item;
import com.example.mockprovider.ScoreResponse.Snapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A fixed script. The mock serves the current step until someone advances it. */
@Component
public class Timeline {

    /** A status of 200 means the body is served; anything else is returned as that error. */
    public record Step(String label, int scoreStatus, Snapshot snapshot,
                       int commentaryStatus, List<Item> items) {}

    private static final Instant T0 = Instant.parse("2026-10-22T14:00:00Z");

    private static Instant at(int minutes) { return T0.plusSeconds(60L * minutes); }

    private static Snapshot snap(long seq, int rev, int runs, int wkts, String overs, int minute) {
        return new Snapshot(seq, rev, "live", 1, runs, wkts, overs, at(minute));
    }

    private static Item item(String id, long seq, int rev, int over, int ball, String text, int minute) {
        return new Item(id, seq, rev, 1, over, ball, text, at(minute));
    }

    private static final Item B1 = item("inn1-0.4", 1, 1, 0, 4,
            "Full and straight, driven to long-on for four.", 0);
    private static final Item B2 = item("inn1-0.5", 2, 1, 0, 5,
            "Edged, and taken behind. Out.", 1);
    private static final Item B2_FIXED = item("inn1-0.5", 2, 2, 0, 5,
            "Edged, and taken behind. Out, and the review confirms it.", 4);
    private static final Item B3 = item("inn1-1.1", 3, 1, 1, 1,
            "Pushed to mid-on for a quick single.", 4);
    private static final Item B4 = item("inn1-1.2", 4, 1, 1, 2,
            "Short and wide, cut away for four.", 5);

    private final List<Step> steps = List.of(
            new Step("0 baseline", 200, snap(1, 1, 4, 0, "0.4", 0), 200, List.of(B1)),
            new Step("1 unchanged response", 200, snap(1, 1, 4, 0, "0.4", 0), 200, List.of(B1)),
            new Step("2 newer score and a new ball", 200, snap(2, 1, 10, 1, "1.1", 1), 200, List.of(B1, B2)),
            // older (sequence, revision) tuple than step 2, but a newer asOf: must still lose
            new Step("3 stale score with a recent timestamp", 200, snap(1, 1, 4, 0, "0.4", 3), 200, List.of(B1, B2)),
            new Step("4 provider error", 503, null, 503, List.of()),
            new Step("5 recovery, with a corrected ball", 200, snap(3, 1, 14, 1, "1.3", 4), 200, List.of(B1, B2_FIXED, B3)),
            new Step("6 score fails, commentary still works", 503, null, 200, List.of(B1, B2_FIXED, B3, B4))
    );

    private final AtomicInteger index = new AtomicInteger(0);

    public Step current() { return steps.get(index.get()); }

    /** Moves to the next step and stays on the last one. */
    public int advance() { return index.updateAndGet(i -> Math.min(i + 1, steps.size() - 1)); }

    public void reset() { index.set(0); }

    public int index() { return index.get(); }

    public int size() { return steps.size(); }
}
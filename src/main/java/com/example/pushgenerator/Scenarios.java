package com.example.pushgenerator;

import com.example.pushgenerator.PushEvent.Ball;
import com.example.pushgenerator.PushEvent.Snap;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic scenarios. Every delivery id is prefixed with its scenario name, because the
 * aggregator deduplicates on (provider, deliveryId) across all matches.
 */
public final class Scenarios {
    private Scenarios() {}

    public record Step(String label, String json) {}

    public record Scenario(String name, String description, List<Step> steps) {}

    public static List<Scenario> all() {
        return List.of(ordered(), duplicate(), outOfOrder(), delayedSnapshot(), correction(),
                workedExample(), invalid());
    }

    public static Optional<Scenario> byName(String name) {
        return all().stream().filter(s -> s.name().equals(name)).findFirst();
    }

    // ---- helpers ----

    private static Snap snap(long seq, int rev, int runs, int wickets, String overs) {
        return new Snap(seq, rev, "live", 1, runs, wickets, overs);
    }

    private static Ball ball(String id, long seq, int rev, String text) {
        return new Ball(id, seq, rev, 1, 0, (int) seq, text);
    }

    private static Step step(String label, String match, String delivery, int minute, Snap s, Ball b) {
        return new Step(label, new PushEvent(delivery, minute, match, s, b).toJson());
    }

    // ---- scenarios ----

    private static Scenario ordered() {
        String m = "a-demo-ordered";
        return new Scenario("ordered", "snapshots and balls arrive in order",
                List.of(
                        step("ordered-d1: snapshot (1,1) 4/0 + ball b1", m, "ordered-d1", 1,
                                snap(1, 1, 4, 0, "0.1"), ball("b1", 1, 1, "Full and straight, driven to long-on for four.")),
                        step("ordered-d2: snapshot (2,1) 4/0 + ball b2", m, "ordered-d2", 2,
                                snap(2, 1, 4, 0, "0.2"), ball("b2", 2, 1, "Defended back to the bowler.")),
                        step("ordered-d3: snapshot (3,1) 5/0 + ball b3", m, "ordered-d3", 3,
                                snap(3, 1, 5, 0, "0.3"), ball("b3", 3, 1, "Pushed into the gap for a single."))));
    }

    private static Scenario duplicate() {
        String m = "a-demo-duplicate";
        return new Scenario("duplicate", "the same deliveryId is sent twice; the repeat changes nothing",
                List.of(
                        step("duplicate-d1: snapshot (1,1) 4/0 + ball b1", m, "duplicate-d1", 1,
                                snap(1, 1, 4, 0, "0.1"), ball("b1", 1, 1, "Driven through cover for four.")),
                        step("duplicate-d2: snapshot (2,1) 4/1 + ball b2", m, "duplicate-d2", 2,
                                snap(2, 1, 4, 1, "0.2"), ball("b2", 2, 1, "Edged and taken behind. Out.")),
                        step("duplicate-d2 again (a retry): expect duplicate", m, "duplicate-d2", 2,
                                snap(2, 1, 4, 1, "0.2"), ball("b2", 2, 1, "Edged and taken behind. Out."))));
    }

    private static Scenario outOfOrder() {
        String m = "a-demo-out-of-order";
        return new Scenario("out-of-order", "a higher commentary sequence arrives before a lower one",
                List.of(
                        step("out-of-order-d1: snapshot (1,1) 4/0 + ball b1", m, "out-of-order-d1", 1,
                                snap(1, 1, 4, 0, "0.1"), ball("b1", 1, 1, "Driven through cover for four.")),
                        step("out-of-order-d2: snapshot (3,1) 9/0 + ball b3 (b2 is missing for now)", m, "out-of-order-d2", 3,
                                snap(3, 1, 9, 0, "0.3"), ball("b3", 3, 1, "Lofted over mid-off for four more.")),
                        step("out-of-order-d3: commentary only, the late ball b2", m, "out-of-order-d3", 4,
                                null, ball("b2", 2, 1, "Pushed to point for a single."))));
    }

    private static Scenario delayedSnapshot() {
        String m = "a-demo-delayed-snapshot";
        return new Scenario("delayed-snapshot",
                "an older snapshot arrives after a newer one; its late ball is still stored in order",
                List.of(
                        step("delayed-snapshot-d1: snapshot (1,1) 4/0 + ball b1", m, "delayed-snapshot-d1", 1,
                                snap(1, 1, 4, 0, "0.1"), ball("b1", 1, 1, "Driven through cover for four.")),
                        step("delayed-snapshot-d2: snapshot (3,1) 10/1 + ball b3", m, "delayed-snapshot-d2", 3,
                                snap(3, 1, 10, 1, "0.3"), ball("b3", 3, 1, "Bowled him! The stumps are shattered.")),
                        step("delayed-snapshot-d3: OLDER snapshot (2,1) 7/0 + ball b2: score must stay 10/1", m,
                                "delayed-snapshot-d3", 4,
                                snap(2, 1, 7, 0, "0.2"), ball("b2", 2, 1, "Clipped off the pads for three."))));
    }

    private static Scenario correction() {
        String m = "a-demo-correction";
        return new Scenario("correction", "a ball and a score are both corrected with higher revisions",
                List.of(
                        step("correction-d1: snapshot (1,1) 4/0 + ball b1", m, "correction-d1", 1,
                                snap(1, 1, 4, 0, "0.1"), ball("b1", 1, 1, "Full and straight, driven to long-on for four.")),
                        step("correction-d2: snapshot (2,1) 4/1 + ball b2", m, "correction-d2", 2,
                                snap(2, 1, 4, 1, "0.2"), ball("b2", 2, 1, "Edged and taken behind. Out.")),
                        step("correction-d3: commentary only, ball b2 revision 2 (text corrected)", m, "correction-d3", 3,
                                null, ball("b2", 2, 2, "Edged and taken behind. Out, and the review confirms it.")),
                        step("correction-d4: snapshot only, (2,2) 5/1 (same sequence, higher revision)", m, "correction-d4", 4,
                                snap(2, 2, 5, 1, "0.2"), null)));
    }

    /** The six steps of the assignment's worked example. */
    private static Scenario workedExample() {
        String m = "a-match-1001";
        return new Scenario("worked-example", "the assignment's worked push example, step for step",
                List.of(
                        step("worked-d1: snapshot (1,1) 4/0 in 0.4 + ball b1 'four'", m, "worked-d1", 1,
                                snap(1, 1, 4, 0, "0.4"), ball("b1", 1, 1, "four")),
                        step("worked-d2: snapshot (2,1) 4/1 in 0.5 + ball b2 'out'", m, "worked-d2", 2,
                                snap(2, 1, 4, 1, "0.5"), ball("b2", 2, 1, "out")),
                        step("worked-d2 again: expect duplicate", m, "worked-d2", 2,
                                snap(2, 1, 4, 1, "0.5"), ball("b2", 2, 1, "out")),
                        step("worked-d4: snapshot (4,1) 10/1 in 1.1 + ball b4", m, "worked-d4", 4,
                                snap(4, 1, 10, 1, "1.1"), ball("b4", 4, 1, "single")),
                        step("worked-d3: delayed snapshot (3,1) 8/1 in 1.0 + ball b3: score stays 10/1", m, "worked-d3", 3,
                                snap(3, 1, 8, 1, "1.0"), ball("b3", 3, 1, "dot ball")),
                        step("worked-d5: snapshot (4,1) repeated + ball b2 revision 2 'out, review stays out'", m, "worked-d5", 5,
                                snap(4, 1, 10, 1, "1.1"), ball("b2", 2, 2, "out, review stays out"))));
    }

    private static Scenario invalid() {
        String malformed = ("{'provider':'provider-a','occurredAt':'2026-10-22T14:01:00.000Z',"
                + "'providerMatchId':'a-match-1001'}").replace('\'', '"');
        String wrongProvider = ("{'provider':'provider-b','deliveryId':'invalid-d3',"
                + "'occurredAt':'2026-10-22T14:01:00.000Z','providerMatchId':'a-match-1001'}").replace('\'', '"');
        return new Scenario("invalid", "bad input is rejected and changes nothing",
                List.of(
                        new Step("missing deliveryId: expect 400", malformed),
                        step("unknown match: expect 404", "a-no-such-match", "invalid-d2", 1,
                                snap(1, 1, 0, 0, "0.0"), null),
                        new Step("payload says provider-b on the provider-a endpoint: expect 400", wrongProvider)));
    }
}
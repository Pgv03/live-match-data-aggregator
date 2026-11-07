package com.example.matchaggregator.poll;

import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.query.MatchQueryService;
import com.example.matchaggregator.store.MatchStore;
import com.example.matchaggregator.poll.ProviderBCommentary.ItemBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PollingServiceTest {
    static final String M = "match-1001", B = "provider-b", BM = "b-match-1001";

    @Autowired MatchStore store;
    @Autowired MatchQueryService query;
    @Autowired JdbcTemplate jdbc;

    StubClient client = new StubClient();
    PollingService poller;

    /** Returns whatever the test scripted, or throws ProviderException. */
    static class StubClient implements ProviderBClient {
        Supplier<ProviderBScore> score = () -> { throw new ProviderException("not scripted"); };
        Supplier<ProviderBCommentary> commentary = () -> { throw new ProviderException("not scripted"); };

        @Override public ProviderBScore fetchScore(String id) { return score.get(); }
        @Override public ProviderBCommentary fetchCommentary(String id) { return commentary.get(); }
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM commentary");
        jdbc.update("DELETE FROM snapshots");
        poller = new PollingService(store, client);
    }

    ProviderBScore score(long seq, int rev, int runs, int wkts, String overs, String asOf) {
        return new ProviderBScore(B, BM, new ProviderBScore.SnapshotBody(
                seq, rev, "live", 1, runs, wkts, overs, Instant.parse(asOf)));
    }

    ItemBody item(String id, long seq, int rev, String text) {
        return new ItemBody(id, seq, rev, 1, 0, 1, text, Instant.parse("2026-10-22T14:00:00Z"));
    }

    ProviderBCommentary commentary(ItemBody... items) {
        return new ProviderBCommentary(B, BM, List.of(items));
    }

    void serve(ProviderBScore s, ProviderBCommentary c) {
        client.score = () -> s;
        client.commentary = () -> c;
    }

    void fail() {
        client.score = () -> { throw new ProviderException("503"); };
        client.commentary = () -> { throw new ProviderException("503"); };
    }

    List<String> ballIds() {
        return store.getCommentary(M, B).stream().map(CommentaryItem::ballId).toList();
    }

    @Test
    void repeatedIdenticalPollsChangeNothing() {
        serve(score(1, 1, 4, 0, "0.4", "2026-10-22T14:00:00Z"), commentary(item("b1", 1, 1, "four")));
        poller.pollOnce();
        poller.pollOnce();
        poller.pollOnce();

        assertEquals(4, store.getSnapshot(M, B).orElseThrow().runs());
        assertEquals(List.of("b1"), ballIds());                       // no extra copy of the ball
        assertEquals(1, store.getCommentary(M, B).get(0).revision());
    }

    @Test
    void newerScoreAndNewBallAreApplied() {
        serve(score(1, 1, 4, 0, "0.4", "2026-10-22T14:00:00Z"), commentary(item("b1", 1, 1, "four")));
        poller.pollOnce();
        serve(score(2, 1, 10, 1, "1.1", "2026-10-22T14:01:00Z"),
                commentary(item("b1", 1, 1, "four"), item("b2", 2, 1, "out")));
        poller.pollOnce();

        assertEquals(10, store.getSnapshot(M, B).orElseThrow().runs());
        assertEquals(List.of("b1", "b2"), ballIds());
    }

    @Test
    void staleResponseLosesEvenWithANewerTimestamp() {
        serve(score(2, 1, 10, 1, "1.1", "2026-10-22T14:01:00Z"),
                commentary(item("b1", 1, 1, "four"), item("b2", 2, 1, "out")));
        poller.pollOnce();
        // older (sequence, revision) tuple, but a more recent asOf
        serve(score(1, 1, 4, 0, "0.4", "2026-10-22T14:03:00Z"),
                commentary(item("b1", 1, 1, "four"), item("b2", 2, 1, "out")));
        poller.pollOnce();

        var stored = store.getSnapshot(M, B).orElseThrow();
        assertEquals(2, stored.sequence());
        assertEquals(10, stored.runs());
        assertEquals(List.of("b1", "b2"), ballIds());
    }

    @Test
    void commentaryCorrectionReplacesTheText() {
        serve(score(2, 1, 10, 1, "1.1", "2026-10-22T14:01:00Z"),
                commentary(item("b1", 1, 1, "four"), item("b2", 2, 1, "out")));
        poller.pollOnce();
        serve(score(2, 1, 10, 1, "1.1", "2026-10-22T14:01:00Z"),
                commentary(item("b1", 1, 1, "four"), item("b2", 2, 2, "out, review confirms")));
        poller.pollOnce();

        assertEquals(2, store.getCommentary(M, B).size());
        assertEquals("out, review confirms", store.getCommentary(M, B).get(1).text());
        assertEquals(2, store.getCommentary(M, B).get(1).revision());
    }

    @Test
    void scoreCorrectionWithHigherRevisionIsApplied() {
        serve(score(2, 1, 10, 1, "1.1", "2026-10-22T14:01:00Z"), commentary(item("b1", 1, 1, "four")));
        poller.pollOnce();
        serve(score(2, 2, 12, 1, "1.1", "2026-10-22T14:02:00Z"), commentary(item("b1", 1, 1, "four")));
        poller.pollOnce();

        var stored = store.getSnapshot(M, B).orElseThrow();
        assertEquals(12, stored.runs());
        assertEquals(2, stored.revision());
    }

    @Test
    void providerErrorLeavesTheLastGoodStateInPlace() {
        serve(score(2, 1, 10, 1, "1.1", "2026-10-22T14:01:00Z"),
                commentary(item("b1", 1, 1, "four"), item("b2", 2, 1, "out")));
        poller.pollOnce();

        fail();
        assertDoesNotThrow(poller::pollOnce);

        assertEquals(10, store.getSnapshot(M, B).orElseThrow().runs());
        assertEquals(List.of("b1", "b2"), ballIds());
        // clients still get the last good data through the read API
        var served = query.score(query.requireMatch(M)).orElseThrow();
        assertEquals(B, served.source());
        assertEquals(10, served.value().runs());
    }

    @Test
    void scoreFailureDoesNotBlockCommentary() {
        client.score = () -> { throw new ProviderException("503"); };
        client.commentary = () -> commentary(item("b1", 1, 1, "four"));
        poller.pollOnce();

        assertTrue(store.getSnapshot(M, B).isEmpty());
        assertEquals(List.of("b1"), ballIds());
    }

    @Test
    void responseForTheWrongMatchIsRejected() {
        client.score = () -> new ProviderBScore(B, "some-other-match",
                new ProviderBScore.SnapshotBody(1, 1, "live", 1, 4, 0, "0.4", null));
        client.commentary = () -> new ProviderBCommentary(B, "some-other-match",
                List.of(item("b1", 1, 1, "four")));
        poller.pollOnce();

        assertTrue(store.getSnapshot(M, B).isEmpty());
        assertTrue(store.getCommentary(M, B).isEmpty());
    }

    @Test
    void oneMalformedItemRejectsTheWholeCommentaryResponse() {
        client.score = () -> { throw new ProviderException("not needed"); };
        client.commentary = () -> commentary(
                item("b1", 1, 1, "four"),
                new ItemBody("b2", 2, 1, 1, 0, 2, null, null));   // missing text
        poller.pollOnce();

        assertTrue(store.getCommentary(M, B).isEmpty());          // not even the good item was stored
    }
}
package com.example.matchaggregator.ingest;

import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.model.Snapshot;
import com.example.matchaggregator.store.MatchStore;
import com.example.matchaggregator.web.WebhookEvent;
import com.example.matchaggregator.web.WebhookEvent.CommentaryPayload;
import com.example.matchaggregator.web.WebhookEvent.SnapshotPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static com.example.matchaggregator.ingest.IngestService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PushIngestTest {
    static final String M = "match-1001", A = "provider-a";
    @Autowired IngestService ingest;
    @Autowired MatchStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM commentary");
        jdbc.update("DELETE FROM snapshots");
        jdbc.update("DELETE FROM delivery_log");
    }

    SnapshotPayload snap(long seq, int rev, int runs, int wkts, String overs) {
        return new SnapshotPayload(seq, rev, "live", 1, runs, wkts, overs);
    }
    CommentaryPayload ball(String id, long seq, int rev, String text) {
        return new CommentaryPayload(id, seq, rev, 1, 0, 1, text);
    }
    WebhookEvent event(String delivery, SnapshotPayload s, CommentaryPayload c) {
        return new WebhookEvent(A, delivery, Instant.parse("2026-10-22T14:03:11Z"),
                "a-match-1001", s, c);
    }
    List<String> ballIds() {
        return store.getCommentary(M, A).stream().map(CommentaryItem::ballId).toList();
    }
    Snapshot score() { return store.getSnapshot(M, A).orElseThrow(); }

    @Test
    void workedExampleFromAssignment() {
        assertEquals(APPLIED, ingest.ingestPush(event("d1", snap(1,1,4,0,"0.4"), ball("b1",1,1,"four"))));
        assertEquals(APPLIED, ingest.ingestPush(event("d2", snap(2,1,4,1,"0.5"), ball("b2",2,1,"out"))));
        assertEquals(1, score().wickets());

        // step 3: same deliveryId again changes nothing
        assertEquals(DUPLICATE, ingest.ingestPush(event("d2", snap(2,1,4,1,"0.5"), ball("b2",2,1,"out"))));
        assertEquals(List.of("b1", "b2"), ballIds());

        // step 4: newer snapshot
        ingest.ingestPush(event("d4", snap(4,1,10,1,"1.1"), ball("b4",4,1,"b4")));
        assertEquals(10, score().runs());

        // step 5: delayed older snapshot loses, but its late ball is still stored in order
        ingest.ingestPush(event("d3", snap(3,1,8,1,"1.0"), ball("b3",3,1,"b3")));
        assertEquals(10, score().runs());
        assertEquals(List.of("b1", "b2", "b3", "b4"), ballIds());

        // step 6: repeated snapshot (4,1) leaves score alone; b2 rev 2 replaces its text
        ingest.ingestPush(event("d5", snap(4,1,10,1,"1.1"), ball("b2",2,2,"out, review stays out")));
        assertEquals(10, score().runs());
        assertEquals(4, store.getCommentary(M, A).size());
        assertEquals("out, review stays out", store.getCommentary(M, A).get(1).text());

        // genuine score correction: same sequence, higher revision, must apply
        ingest.ingestPush(event("d6", snap(4,2,12,1,"1.1"), null));
        assertEquals(12, score().runs());
    }

    @Test
    void snapshotOnlyAndCommentaryOnlyEvents() {
        assertEquals(APPLIED, ingest.ingestPush(event("s1", snap(1,1,0,0,"0.0"), null)));
        assertEquals(APPLIED, ingest.ingestPush(event("c1", null, ball("b1",1,1,"first"))));
        assertEquals(0, score().runs());
        assertEquals(List.of("b1"), ballIds());
    }

    @Test
    void unknownMatchIsRejectedAndLeavesNothingBehind() {
        var bad = new WebhookEvent(A, "x1", Instant.now(), "nope", snap(1,1,0,0,"0.0"), null);
        assertThrows(UnknownMatchException.class, () -> ingest.ingestPush(bad));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_log", Integer.class));
    }
}
package com.example.matchaggregator.store;

import com.example.matchaggregator.model.CommentaryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CommentaryRulesTest {
    static final String M = "m1", P = "provider-b";
    @Autowired MatchStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM commentary"); }

    CommentaryItem ball(String id, long seq, int rev, String text) {
        return new CommentaryItem(id, seq, rev, 1, 0, 1, text, null);
    }

    @Test
    void revisionRuleAndSequenceOrder() {
        assertTrue(store.applyCommentary(M, P, ball("b2", 2, 1, "out")));
        assertTrue(store.applyCommentary(M, P, ball("b4", 4, 1, "four")));
        assertTrue(store.applyCommentary(M, P, ball("b3", 3, 1, "single")));   // late ball

        assertEquals(List.of("b2", "b3", "b4"),
                store.getCommentary(M, P).stream().map(CommentaryItem::ballId).toList());

        assertFalse(store.applyCommentary(M, P, ball("b2", 2, 1, "dup")));      // same revision
        assertTrue(store.applyCommentary(M, P, ball("b2", 2, 2, "out, review"))); // correction
        assertFalse(store.applyCommentary(M, P, ball("b2", 2, 1, "stale")));    // lower revision

        List<CommentaryItem> all = store.getCommentary(M, P);
        assertEquals(3, all.size());                          // one row per logical ball
        assertEquals("out, review", all.get(0).text());
    }

    @Test
    void batchOnlyWritesWhatChanged() {
        List<CommentaryItem> first = List.of(ball("b1", 1, 1, "a"), ball("b2", 2, 1, "b"));
        assertEquals(2, store.applyCommentaryBatch(M, P, first));
        assertEquals(0, store.applyCommentaryBatch(M, P, first));               // unchanged poll

        List<CommentaryItem> next = List.of(
                ball("b1", 1, 1, "a"), ball("b2", 2, 2, "b corrected"), ball("b3", 3, 1, "c"));
        assertEquals(2, store.applyCommentaryBatch(M, P, next));                // 1 correction + 1 new
        assertEquals(3, store.getCommentary(M, P).size());
    }
}
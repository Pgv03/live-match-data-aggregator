package com.example.matchaggregator.store;

import com.example.matchaggregator.model.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SnapshotRulesTest {
    static final String M = "m1", P = "provider-a";
    @Autowired MatchStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM snapshots"); }

    Snapshot snap(long seq, int rev, int runs) {
        return new Snapshot(seq, rev, "live", 1, runs, 0, "1.0", null);
    }

    @Test
    void tupleRule() {
        assertTrue(store.applySnapshot(M, P, snap(4, 1, 10)));   // first insert
        assertFalse(store.applySnapshot(M, P, snap(4, 1, 99)));  // same tuple: not newer
        assertFalse(store.applySnapshot(M, P, snap(3, 9, 99)));  // lower seq, high rev: still older
        assertEquals(10, store.getSnapshot(M, P).orElseThrow().runs());

        assertTrue(store.applySnapshot(M, P, snap(4, 2, 12)));   // same seq, higher rev: correction
        assertEquals(12, store.getSnapshot(M, P).orElseThrow().runs());

        assertTrue(store.applySnapshot(M, P, snap(5, 1, 15)));   // higher seq beats any rev
        assertEquals(15, store.getSnapshot(M, P).orElseThrow().runs());
    }
}
package com.example.matchaggregator.query;

import com.example.matchaggregator.ingest.UnknownMatchException;
import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.model.Match;
import com.example.matchaggregator.model.Snapshot;
import com.example.matchaggregator.store.MatchStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MatchQueryServiceTest {
    static final String M = "match-1001", A = "provider-a", B = "provider-b";
    @Autowired MatchQueryService query;
    @Autowired MatchStore store;
    @Autowired JdbcTemplate jdbc;
    Match match;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM commentary");
        jdbc.update("DELETE FROM snapshots");
        match = query.requireMatch(M);
    }

    Snapshot snap(long seq, int runs) { return new Snapshot(seq, 1, "live", 1, runs, 0, "1.0", null); }
    CommentaryItem ball(String id, long seq) { return new CommentaryItem(id, seq, 1, 1, 0, 1, id, null); }

    @Test
    void scorePrefersPrimaryEvenWhenOtherHasHigherSequence() {
        store.applySnapshot(M, A, snap(1, 10));
        store.applySnapshot(M, B, snap(100, 99));      // sequences are never compared across providers
        var served = query.score(match).orElseThrow();
        assertEquals(A, served.source());
        assertEquals(10, served.value().runs());
    }

    @Test
    void scoreFallsBackWhenPrimaryHasNothing() {
        store.applySnapshot(M, B, snap(5, 42));
        var served = query.score(match).orElseThrow();
        assertEquals(B, served.source());
        assertEquals(42, served.value().runs());
    }

    @Test
    void noScoreYet() {
        assertTrue(query.score(match).isEmpty());
    }

    @Test
    void commentaryIsOneProvidersListNeverMerged() {
        store.applyCommentary(M, A, ball("a1", 1));
        store.applyCommentary(M, A, ball("a2", 2));
        store.applyCommentary(M, B, ball("b1", 1));
        store.applyCommentary(M, B, ball("b2", 2));
        store.applyCommentary(M, B, ball("b3", 3));
        var served = query.commentary(match);
        assertEquals(A, served.source());
        assertEquals(List.of("a1", "a2"),
                served.value().stream().map(CommentaryItem::ballId).toList());
    }

    @Test
    void commentaryFallsBackWhenPrimaryHasNoBalls() {
        store.applyCommentary(M, B, ball("b1", 1));
        var served = query.commentary(match);
        assertEquals(B, served.source());
        assertEquals(1, served.value().size());
    }

    @Test
    void commentaryEmptyBeforeAnyBall() {
        var served = query.commentary(match);
        assertNull(served.source());
        assertTrue(served.value().isEmpty());
    }

    @Test
    void unknownMatchIsRejected() {
        assertThrows(UnknownMatchException.class, () -> query.requireMatch("nope"));
    }

    @Test
    void sourcesShowsEveryProviderAndWhichOneIsServed() {
        store.applySnapshot(M, A, snap(1, 10));
        store.applySnapshot(M, B, snap(100, 99));
        store.applyCommentary(M, B, ball("b1", 1));

        var sources = query.sources(match);
        assertEquals(List.of(A, B), sources.stream().map(MatchQueryService.ProviderState::provider).toList());
        assertEquals(10, sources.get(0).score().runs());
        assertEquals(99, sources.get(1).score().runs());
        assertEquals(100, sources.get(1).score().sequence());
        assertTrue(sources.get(0).commentary().isEmpty());
        assertEquals(1, sources.get(1).commentary().size());

        // the normal views still follow the precedence rule
        assertEquals(A, query.score(match).orElseThrow().source());
        assertEquals(B, query.commentary(match).source());  // A has no balls, so B's list is served
    }

}
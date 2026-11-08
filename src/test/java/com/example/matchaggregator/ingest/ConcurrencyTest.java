package com.example.matchaggregator.ingest;

import com.example.matchaggregator.ingest.IngestService.Result;
import com.example.matchaggregator.store.MatchStore;
import com.example.matchaggregator.web.WebhookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.example.matchaggregator.testsupport.TestEvents.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrencyTest {
    static final int THREADS = 16;

    @Autowired IngestService ingest;
    @Autowired MatchStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM commentary");
        jdbc.update("DELETE FROM snapshots");
        jdbc.update("DELETE FROM delivery_log");
    }

    /** Releases all events together from a thread pool and returns each call's result. */
    List<Result> sendConcurrently(List<WebhookEvent> events) throws Exception {
        List<WebhookEvent> shuffled = new ArrayList<>(events);
        Collections.shuffle(shuffled, new Random(42));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Result>> futures = new ArrayList<>();
            for (WebhookEvent e : shuffled) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return ingest.ingestPush(e);
                }));
            }
            start.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));   // rethrows anything a thread threw
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    long count(List<Result> results, Result wanted) {
        return results.stream().filter(r -> r == wanted).count();
    }

    @Test
    void newestSnapshotWinsWhateverTheArrivalOrder() throws Exception {
        List<WebhookEvent> events = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            events.add(event("c1-" + i, live(i, 1, i, 0, "o" + i), ball("b" + i, i, 1, "ball " + i)));
        }

        List<Result> results = sendConcurrently(events);

        assertEquals(100, count(results, Result.APPLIED));
        var score = store.getSnapshot(MATCH, PROVIDER_A).orElseThrow();
        assertEquals(100, score.sequence());
        assertEquals(100, score.runs());

        var balls = store.getCommentary(MATCH, PROVIDER_A);
        assertEquals(100, balls.size());
        for (int i = 0; i < 100; i++) {
            assertEquals("b" + (i + 1), balls.get(i).ballId());          // served in sequence order
        }
        assertEquals(100, count("delivery_log"));
    }

    @Test
    void aRepeatedDeliveryIsAppliedExactlyOnce() throws Exception {
        List<WebhookEvent> events = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            WebhookEvent e = event("c2-" + i, live(i, 1, i, 0, "o" + i), ball("b" + i, i, 1, "ball " + i));
            for (int copy = 0; copy < 5; copy++) events.add(e);          // each delivery sent 5 times
        }

        List<Result> results = sendConcurrently(events);

        assertEquals(20, count(results, Result.APPLIED));                // exactly one winner per delivery id
        assertEquals(80, count(results, Result.DUPLICATE));
        assertEquals(20, count("delivery_log"));
        assertEquals(20, count("commentary"));
        assertEquals(20, store.getSnapshot(MATCH, PROVIDER_A).orElseThrow().sequence());
    }

    @Test
    void highestRevisionWinsWhenCorrectionsRace() throws Exception {
        List<WebhookEvent> events = new ArrayList<>();
        for (int rev = 1; rev <= 30; rev++) {
            events.add(event("c3-" + rev, live(1, rev, rev, 0, "0.1"), ball("b1", 1, rev, "revision " + rev)));
        }

        sendConcurrently(events);

        var score = store.getSnapshot(MATCH, PROVIDER_A).orElseThrow();
        assertEquals(30, score.revision());
        assertEquals(30, score.runs());

        var balls = store.getCommentary(MATCH, PROVIDER_A);
        assertEquals(1, balls.size());                                   // one row for the logical ball
        assertEquals(30, balls.get(0).revision());
        assertEquals("revision 30", balls.get(0).text());
    }
}
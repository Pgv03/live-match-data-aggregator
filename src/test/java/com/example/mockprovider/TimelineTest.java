package com.example.mockprovider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineTest {
    Timeline t = new Timeline();

    void advance(int times) { for (int i = 0; i < times; i++) t.advance(); }

    @Test
    void unchangedStepRepeatsThePreviousBodies() {
        var first = t.current();
        advance(1);
        var second = t.current();
        assertEquals(first.snapshot(), second.snapshot());
        assertEquals(first.items(), second.items());
    }

    @Test
    void staleStepHasAnOlderTupleButANewerTimestamp() {
        advance(2);
        var newer = t.current().snapshot();
        advance(1);
        var stale = t.current().snapshot();
        assertTrue(stale.sequence() < newer.sequence());
        assertTrue(stale.asOf().isAfter(newer.asOf()));
    }

    @Test
    void errorStepFailsBothEndpoints() {
        advance(4);
        assertEquals(503, t.current().scoreStatus());
        assertEquals(503, t.current().commentaryStatus());
    }

    @Test
    void lastStepFailsOnlyTheScoreEndpoint() {
        advance(6);
        assertEquals(503, t.current().scoreStatus());
        assertEquals(200, t.current().commentaryStatus());
    }

    @Test
    void advanceStopsAtTheLastStepAndResetReturnsToStart() {
        advance(50);
        assertEquals(t.size() - 1, t.index());
        t.reset();
        assertEquals(0, t.index());
    }
}
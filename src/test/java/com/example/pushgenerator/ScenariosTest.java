package com.example.pushgenerator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ScenariosTest {
    static final Pattern DELIVERY_ID = Pattern.compile("\"deliveryId\":\"([^\"]*)\"");

    static List<String> deliveryIds(Scenarios.Scenario s) {
        return s.steps().stream().map(step -> {
            Matcher m = DELIVERY_ID.matcher(step.json());
            return m.find() ? m.group(1) : null;
        }).filter(Objects::nonNull).toList();
    }

    @Test
    void deliveryIdsNeverCollideAcrossScenarios() {
        Map<String, String> owner = new HashMap<>();
        for (Scenarios.Scenario s : Scenarios.all()) {
            for (String id : deliveryIds(s)) {
                String previous = owner.putIfAbsent(id, s.name());
                assertTrue(previous == null || previous.equals(s.name()),
                        id + " is used by both " + previous + " and " + s.name());
            }
        }
    }

    @Test
    void duplicateScenarioRepeatsOneDeliveryId() {
        var ids = deliveryIds(Scenarios.byName("duplicate").orElseThrow());
        assertTrue(ids.size() > ids.stream().distinct().count());
    }

    @Test
    void absentPartsAreOmittedFromTheJson() {
        var snapshotOnly = new PushEvent("x", 1, "m",
                new PushEvent.Snap(1, 1, "live", 1, 0, 0, "0.0"), null).toJson();
        assertTrue(snapshotOnly.contains("\"snapshot\""));
        assertFalse(snapshotOnly.contains("\"commentary\""));

        var commentaryOnly = new PushEvent("x", 1, "m", null,
                new PushEvent.Ball("b1", 1, 1, 1, 0, 1, "hi")).toJson();
        assertFalse(commentaryOnly.contains("\"snapshot\""));
        assertTrue(commentaryOnly.contains("\"commentary\""));
    }

    @Test
    void quotesInTextAreEscaped() {
        var json = new PushEvent("x", 1, "m", null,
                new PushEvent.Ball("b1", 1, 1, 1, 0, 1, "a\"b")).toJson();
        assertTrue(json.contains("a\\\"b"));
    }
}
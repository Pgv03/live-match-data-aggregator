package com.example.matchaggregator.web;

import com.example.pushgenerator.Scenarios;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the real application over HTTP with the same events the push generator sends. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioHttpTest {

    record Response(int status, String body) {}

    /** A missing or null field reads as null instead of throwing. */
    static final Configuration JSON = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;

    final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM commentary");
        jdbc.update("DELETE FROM snapshots");
        jdbc.update("DELETE FROM delivery_log");
    }

    // ---- helpers ----

    static <T> T read(String body, String path) {
        return JsonPath.using(JSON).parse(body).read(path);
    }

    Response send(HttpRequest request) {
        try {
            HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    Response post(String json) {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/webhooks/provider-a"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build());
    }

    /** Sends every step of a scenario and returns what came back. */
    List<Response> run(String scenario) {
        return Scenarios.byName(scenario).orElseThrow().steps().stream()
                .map(step -> post(step.json()))
                .toList();
    }

    /** "applied" / "duplicate" for a 200, otherwise the status code. */
    static String outcome(Response r) {
        return r.status() == 200 ? read(r.body(), "$.status") : String.valueOf(r.status());
    }

    List<String> outcomes(List<Response> responses) {
        return responses.stream().map(ScenarioHttpTest::outcome).toList();
    }

    void assertScore(String matchId, int runs, int wickets, String overs) {
        Response r = get("/matches/" + matchId + "/score");
        assertEquals(200, r.status());
        assertEquals(runs, (int) read(r.body(), "$.runs"));
        assertEquals(wickets, (int) read(r.body(), "$.wickets"));
        assertEquals(overs, read(r.body(), "$.overs"));
        assertEquals("provider-a", read(r.body(), "$.source"));
    }

    String commentaryBody(String matchId) {
        Response r = get("/matches/" + matchId + "/commentary");
        assertEquals(200, r.status());
        return r.body();
    }

    List<String> ballIds(String matchId) {
        return read(commentaryBody(matchId), "$.items[*].ballId");
    }

    // ---- the assignment's required scenarios ----

    @Test
    void orderedIngestion() {
        assertEquals(List.of("applied", "applied", "applied"), outcomes(run("ordered")));
        assertScore("demo-ordered", 5, 0, "0.3");
        assertEquals(List.of("b1", "b2", "b3"), ballIds("demo-ordered"));
    }

    @Test
    void duplicateDeliveryChangesNothing() {
        assertEquals(List.of("applied", "applied", "duplicate"), outcomes(run("duplicate")));
        assertScore("demo-duplicate", 4, 1, "0.2");
        assertEquals(List.of("b1", "b2"), ballIds("demo-duplicate"));     // b2 stored once
    }

    @Test
    void lateBallIsInsertedInSequenceOrder() {
        assertEquals(List.of("applied", "applied", "applied"), outcomes(run("out-of-order")));
        assertScore("demo-out-of-order", 9, 0, "0.3");                   // newer score was served at once
        assertEquals(List.of("b1", "b2", "b3"), ballIds("demo-out-of-order"));
    }

    @Test
    void olderSnapshotLosesButItsBallIsStillServedInOrder() {
        assertEquals(List.of("applied", "applied", "applied"), outcomes(run("delayed-snapshot")));
        assertScore("demo-delayed-snapshot", 10, 1, "0.3");              // not 7/0
        String body = commentaryBody("demo-delayed-snapshot");
        assertEquals(List.of("b1", "b2", "b3"), read(body, "$.items[*].ballId"));
        assertEquals("Clipped off the pads for three.", read(body, "$.items[1].text"));
    }

    @Test
    void commentaryAndScoreCorrectionsAreApplied() {
        assertEquals(List.of("applied", "applied", "applied", "applied"), outcomes(run("correction")));
        assertScore("demo-correction", 5, 1, "0.2");

        String body = commentaryBody("demo-correction");
        assertEquals(List.of("b1", "b2"), read(body, "$.items[*].ballId"));   // one entry per logical ball
        assertEquals("Edged and taken behind. Out, and the review confirms it.", read(body, "$.items[1].text"));
        assertEquals(2, (int) read(body, "$.items[1].revision"));

        // the stored snapshot kept its sequence and moved to revision 2
        String sources = get("/matches/demo-correction/sources").body();
        assertEquals("provider-a", read(sources, "$.providers[0].provider"));
        assertEquals(2, (int) read(sources, "$.providers[0].score.sequence"));
        assertEquals(2, (int) read(sources, "$.providers[0].score.revision"));
    }

    @Test
    void workedExampleFromTheAssignment() {
        assertEquals(List.of("applied", "applied", "duplicate", "applied", "applied", "applied"),
                outcomes(run("worked-example")));
        assertScore("match-1001", 10, 1, "1.1");
        String body = commentaryBody("match-1001");
        assertEquals(List.of("b1", "b2", "b3", "b4"), read(body, "$.items[*].ballId"));
        assertEquals("out, review stays out", read(body, "$.items[1].text"));
    }

    // ---- HTTP behavior and idempotency ----

    @Test
    void invalidInputIsRejectedAndStoresNothing() {
        assertEquals(List.of("400", "404", "400"), outcomes(run("invalid")));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM delivery_log", Integer.class));
        assertEquals(404, get("/matches/match-1001/score").status());   // still no score
    }

    @Test
    void runningAScenarioTwiceChangesNothing() {
        run("ordered");
        String before = commentaryBody("demo-ordered");

        assertEquals(List.of("duplicate", "duplicate", "duplicate"), outcomes(run("ordered")));
        assertEquals(before, commentaryBody("demo-ordered"));
        assertScore("demo-ordered", 5, 0, "0.3");
    }

    @Test
    void readApiErrorsAndEmptyStates() {
        assertEquals(404, get("/matches/nope").status());
        assertEquals(404, get("/matches/nope/score").status());
        assertEquals(404, get("/matches/nope/commentary").status());
        assertEquals(404, get("/matches/nope/sources").status());

        // a known match with nothing stored yet
        assertEquals(404, get("/matches/demo-ordered/score").status());
        String commentary = commentaryBody("demo-ordered");
        assertTrue(((List<?>) read(commentary, "$.items")).isEmpty());
        assertNull(read(commentary, "$.source"));

        String match = get("/matches/demo-ordered").body();
        assertNull(read(match, "$.score"));
        assertNull(read(match, "$.status"));

        List<String> ids = read(get("/matches").body(), "$[*].id");
        assertTrue(ids.contains("match-1001"));
        assertTrue(ids.contains("demo-ordered"));
    }
}
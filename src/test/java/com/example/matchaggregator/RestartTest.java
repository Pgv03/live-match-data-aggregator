package com.example.matchaggregator;

import com.example.matchaggregator.ingest.IngestService;
import com.example.matchaggregator.store.MatchStore;
import com.example.matchaggregator.web.WebhookEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import static com.example.matchaggregator.ingest.IngestService.Result.APPLIED;
import static com.example.matchaggregator.ingest.IngestService.Result.DUPLICATE;
import static com.example.matchaggregator.testsupport.TestEvents.*;
import static org.junit.jupiter.api.Assertions.*;

/** Stops the application and starts a new one against the same database file. */
class RestartTest {
    @TempDir Path dir;

    final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    /** Same file database settings as the real application, in a throwaway folder. */
    ConfigurableApplicationContext start() {
        String file = dir.resolve("aggregator").toAbsolutePath().toString().replace('\\', '/');
        return SpringApplication.run(MatchAggregatorApplication.class,
                "--spring.datasource.url=jdbc:h2:file:" + file + ";WRITE_DELAY=0",
                "--server.port=0",
                "--poller.enabled=false",
                "--spring.main.banner-mode=off");
    }

    static List<WebhookEvent> firstRun() {
        return List.of(
                event("r1", live(1, 1, 4, 0, "0.1"), ball("b1", 1, 1, "first")),
                event("r2", live(2, 1, 10, 1, "0.4"), ball("b2", 2, 1, "second")),
                event("r3", snap(3, 1, "innings_break", 20, 2, "2.0"), ball("b3", 3, 1, "third")));
    }

    /** First life of the application: push events, then shut down. */
    void pushFirstRun() {
        try (ConfigurableApplicationContext ctx = start()) {
            IngestService ingest = ctx.getBean(IngestService.class);
            for (WebhookEvent e : firstRun()) {
                assertEquals(APPLIED, ingest.ingestPush(e));
            }
        }
    }

    String get(ConfigurableApplicationContext ctx, String path) throws Exception {
        String port = ctx.getEnvironment().getProperty("local.server.port");
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        return r.body();
    }

    @Test
    void scoreStatusAndCommentarySurviveARestart() throws Exception {
        pushFirstRun();

        try (ConfigurableApplicationContext ctx = start()) {
            String score = get(ctx, "/matches/match-1001/score");
            assertEquals("innings_break", JsonPath.<String>read(score, "$.status"));
            assertEquals(20, (int) JsonPath.<Integer>read(score, "$.runs"));
            assertEquals(2, (int) JsonPath.<Integer>read(score, "$.wickets"));
            assertEquals("2.0", JsonPath.<String>read(score, "$.overs"));
            assertEquals("provider-a", JsonPath.<String>read(score, "$.source"));

            String commentary = get(ctx, "/matches/match-1001/commentary");
            assertEquals(List.of("b1", "b2", "b3"), JsonPath.<List<String>>read(commentary, "$.items[*].ballId"));
            assertEquals("second", JsonPath.<String>read(commentary, "$.items[1].text"));
        }
    }

    @Test
    void deliveryHistoryAndOrderingRulesSurviveARestart() {
        pushFirstRun();

        try (ConfigurableApplicationContext ctx = start()) {
            IngestService ingest = ctx.getBean(IngestService.class);
            MatchStore store = ctx.getBean(MatchStore.class);

            // a retry of a delivery from before the restart is still recognised
            assertEquals(DUPLICATE, ingest.ingestPush(firstRun().get(2)));

            // an older snapshot is judged against the persisted newest one, and loses
            assertEquals(APPLIED, ingest.ingestPush(event("r4", live(2, 1, 99, 0, "9.9"), null)));
            assertEquals(20, store.getSnapshot(MATCH, PROVIDER_A).orElseThrow().runs());

            // a genuinely newer snapshot still applies
            ingest.ingestPush(event("r5", snap(4, 1, "innings_break", 25, 2, "2.1"), null));
            assertEquals(25, store.getSnapshot(MATCH, PROVIDER_A).orElseThrow().runs());
        }
    }
}
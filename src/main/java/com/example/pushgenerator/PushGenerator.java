package com.example.pushgenerator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Sends scenario events to the aggregator's webhook and prints each request and response. */
public final class PushGenerator {
    static final String DEFAULT_URL = "http://localhost:8080/webhooks/provider-a";

    private PushGenerator() {}

    public static void main(String[] args) {
        String url = DEFAULT_URL;
        String name = "all";
        for (String a : args) {
            if (a.startsWith("--url=")) url = a.substring("--url=".length());
            else if (a.startsWith("--scenario=")) name = a.substring("--scenario=".length());
            else if (a.equals("--help")) { usage(); return; }
            else { System.err.println("Unknown argument: " + a); usage(); System.exit(2); }
        }

        List<Scenarios.Scenario> toRun;
        if (name.equals("all")) {
            toRun = Scenarios.all();
        } else {
            var found = Scenarios.byName(name);
            if (found.isEmpty()) {
                System.err.println("Unknown scenario: " + name);
                usage();
                System.exit(2);
                return;
            }
            toRun = List.of(found.get());
        }

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        try {
            for (Scenarios.Scenario s : toRun) run(http, url, s);
        } catch (IOException e) {
            System.err.println("Could not reach " + url + ": " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.exit(1);
        }
    }

    private static void run(HttpClient http, String url, Scenarios.Scenario s)
            throws IOException, InterruptedException {
        System.out.println("== " + s.name() + ": " + s.description());
        for (Scenarios.Step step : s.steps()) {
            System.out.println();
            System.out.println("-> " + step.label());
            System.out.println("   " + step.json());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(step.json()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("<- " + response.statusCode() + " " + response.body());
        }
        System.out.println();
    }

    private static void usage() {
        System.out.println("Usage: PushGenerator [--url=<webhook url>] [--scenario=<name>|all]");
        System.out.println("Default url: " + DEFAULT_URL);
        System.out.print("Scenarios:");
        for (Scenarios.Scenario s : Scenarios.all()) System.out.print(" " + s.name());
        System.out.println();
    }
}
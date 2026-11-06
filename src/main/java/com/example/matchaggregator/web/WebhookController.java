package com.example.matchaggregator.web;

import com.example.matchaggregator.ingest.IngestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class WebhookController {
    private static final String PROVIDER = "provider-a";

    private final IngestService ingest;

    public WebhookController(IngestService ingest) { this.ingest = ingest; }

    /**
     * 200 means the event is durably committed (or was already, if a duplicate).
     * Providers should retry on any non-2xx, including 500 after a crash.
     */
    @PostMapping("/webhooks/provider-a")
    public ResponseEntity<Map<String, String>> receive(@Valid @RequestBody WebhookEvent event) {
        if (!PROVIDER.equals(event.provider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "provider must be " + PROVIDER + " for this endpoint");
        }
        var result = ingest.ingestPush(event);
        String status = result == IngestService.Result.APPLIED ? "applied" : "duplicate";
        return ResponseEntity.ok(Map.of("status", status));
    }
}
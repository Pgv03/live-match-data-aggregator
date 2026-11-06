package com.example.mockprovider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/provider/matches")
public class ProviderController {
    private static final String PROVIDER = "provider-b";
    private static final String MATCH_ID = "b-match-1001";

    private final Timeline timeline;

    public ProviderController(Timeline timeline) { this.timeline = timeline; }

    @GetMapping("/{id}/score")
    public ResponseEntity<?> score(@PathVariable("id") String id) {
        if (!MATCH_ID.equals(id)) return ResponseEntity.notFound().build();
        Timeline.Step step = timeline.current();
        if (step.scoreStatus() != 200) return unavailable(step.scoreStatus());
        return ResponseEntity.ok(new ScoreResponse(PROVIDER, MATCH_ID, step.snapshot()));
    }

    @GetMapping("/{id}/commentary")
    public ResponseEntity<?> commentary(@PathVariable("id") String id) {
        if (!MATCH_ID.equals(id)) return ResponseEntity.notFound().build();
        Timeline.Step step = timeline.current();
        if (step.commentaryStatus() != 200) return unavailable(step.commentaryStatus());
        return ResponseEntity.ok(new CommentaryResponse(PROVIDER, MATCH_ID, step.items()));
    }

    private ResponseEntity<?> unavailable(int status) {
        return ResponseEntity.status(status).body(Map.of("error", "temporarily unavailable"));
    }
}
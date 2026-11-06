package com.example.mockprovider;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test and demo controls. Not part of the provider contract. */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final Timeline timeline;

    public AdminController(Timeline timeline) { this.timeline = timeline; }

    @PostMapping("/advance")
    public Map<String, Object> advance() {
        timeline.advance();
        return state();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        timeline.reset();
        return state();
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return Map.of("step", timeline.index(), "of", timeline.size(),
                "label", timeline.current().label());
    }
}
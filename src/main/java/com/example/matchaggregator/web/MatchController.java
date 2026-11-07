package com.example.matchaggregator.web;

import com.example.matchaggregator.model.Match;
import com.example.matchaggregator.query.MatchQueryService;
import com.example.matchaggregator.web.Views.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/matches")
public class MatchController {
    private final MatchQueryService query;

    public MatchController(MatchQueryService query) { this.query = query; }

    @GetMapping
    public List<MatchView> list() {
        return query.listMatches().stream().map(this::toView).toList();
    }

    @GetMapping("/{id}")
    public MatchView get(@PathVariable("id") String id) {
        return toView(query.requireMatch(id));
    }

    @GetMapping("/{id}/score")
    public ScoreView score(@PathVariable("id") String id) {
        Match m = query.requireMatch(id);
        return query.score(m)
                .map(s -> ScoreView.from(s.source(), s.value()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No score published yet for " + id));
    }

    @GetMapping("/{id}/commentary")
    public CommentaryView commentary(@PathVariable("id") String id) {
        var served = query.commentary(query.requireMatch(id));
        return new CommentaryView(served.source(),
                served.value().stream().map(CommentaryEntry::from).toList());
    }

    private MatchView toView(Match m) {
        return query.score(m)
                .map(s -> new MatchView(m.id(), m.title(), s.value().status(),
                        ScoreView.from(s.source(), s.value())))
                .orElseGet(() -> new MatchView(m.id(), m.title(), null, null));
    }

    @GetMapping("/{id}/sources")
    public SourcesView sources(@PathVariable("id") String id) {
        Match m = query.requireMatch(id);
        List<ProviderSourceView> providers = query.sources(m).stream()
                .map(p -> new ProviderSourceView(
                        p.provider(),
                        p.score() == null ? null : StoredScoreView.from(p.score()),
                        p.commentary().stream().map(CommentaryEntry::from).toList()))
                .toList();
        String scoreFrom = query.score(m).map(MatchQueryService.Served::source).orElse(null);
        String commentaryFrom = query.commentary(m).source();
        return new SourcesView(m.id(), m.primaryProvider(), scoreFrom, commentaryFrom, providers);
    }

}
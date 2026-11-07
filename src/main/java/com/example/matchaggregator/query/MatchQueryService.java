package com.example.matchaggregator.query;

import com.example.matchaggregator.ingest.UnknownMatchException;
import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.model.Match;
import com.example.matchaggregator.model.Snapshot;
import com.example.matchaggregator.store.MatchStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MatchQueryService {
    /** What was served, and which provider it came from. */
    public record Served<T>(String source, T value) {}

    private final MatchStore store;

    public MatchQueryService(MatchStore store) { this.store = store; }

    public Match requireMatch(String id) {
        return store.findMatch(id)
                .orElseThrow(() -> new UnknownMatchException("Unknown match " + id));
    }

    public List<Match> listMatches() { return store.listMatches(); }

    /**
     * Precedence rule: the match's primary provider first, then the others in name order.
     * Sequence numbers are never compared across providers.
     */
    private List<String> providerOrder(Match m) {
        List<String> order = new ArrayList<>();
        order.add(m.primaryProvider());
        for (String p : store.providersFor(m.id())) {
            if (!order.contains(p)) order.add(p);
        }
        return order;
    }

    /** First provider (in precedence order) that has a stored snapshot. */
    public Optional<Served<Snapshot>> score(Match m) {
        for (String p : providerOrder(m)) {
            Optional<Snapshot> s = store.getSnapshot(m.id(), p);
            if (s.isPresent()) return Optional.of(new Served<>(p, s.get()));
        }
        return Optional.empty();
    }

    /**
     * One provider's whole list, never a merge of two: the first provider in precedence
     * order that has any ball. Source is null when no provider has stored a ball yet.
     */
    public Served<List<CommentaryItem>> commentary(Match m) {
        for (String p : providerOrder(m)) {
            List<CommentaryItem> items = store.getCommentary(m.id(), p);
            if (!items.isEmpty()) return new Served<>(p, items);
        }
        return new Served<>(null, List.of());
    }

    /** One provider's stored state, for the side-by-side debug view. */
    public record ProviderState(String provider, Snapshot score, List<CommentaryItem> commentary) {}

    /** Every provider's stored state, in precedence order. */
    public List<ProviderState> sources(Match m) {
        return providerOrder(m).stream()
                .map(p -> new ProviderState(p,
                        store.getSnapshot(m.id(), p).orElse(null),
                        store.getCommentary(m.id(), p)))
                .toList();
    }

}
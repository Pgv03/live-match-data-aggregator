package com.example.matchaggregator.poll;

import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.model.Snapshot;
import com.example.matchaggregator.store.MatchStore;
import com.example.matchaggregator.store.MatchStore.ProviderMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PollingService {
    private static final Logger log = LoggerFactory.getLogger(PollingService.class);
    static final String PROVIDER = "provider-b";

    private final MatchStore store;
    private final ProviderBClient client;

    public PollingService(MatchStore store, ProviderBClient client) {
        this.store = store;
        this.client = client;
    }

    /** One polling cycle over every match Provider B publishes. The scheduler calls this. */
    public void pollOnce() {
        for (ProviderMatch m : store.providerMatches(PROVIDER)) {
            pollScore(m);
            pollCommentary(m);
        }
    }

    private void pollScore(ProviderMatch m) {
        try {
            ProviderBScore r = client.fetchScore(m.providerMatchId());
            if (r == null || r.snapshot() == null) {
                throw new ProviderException("score response has no snapshot");
            }
            if (!m.providerMatchId().equals(r.providerMatchId())) {
                throw new ProviderException("score response is for match " + r.providerMatchId());
            }
            var s = r.snapshot();
            if (s.status() == null || s.status().isBlank() || s.overs() == null) {
                throw new ProviderException("score snapshot is incomplete");
            }
            boolean changed = store.applySnapshot(m.matchId(), PROVIDER, new Snapshot(
                    s.sequence(), s.revision(), s.status(), s.innings(),
                    s.runs(), s.wickets(), s.overs(), s.asOf()));
            if (changed) {
                log.info("{} score applied: seq={} rev={} {}/{} in {}", PROVIDER,
                        s.sequence(), s.revision(), s.runs(), s.wickets(), s.overs());
            } else {
                log.debug("{} score ignored (not newer): seq={} rev={}", PROVIDER,
                        s.sequence(), s.revision());
            }
        } catch (RuntimeException e) {
            log.warn("{} score poll failed for {}: {}", PROVIDER, m.providerMatchId(), e.getMessage());
        }
    }

    private void pollCommentary(ProviderMatch m) {
        try {
            ProviderBCommentary r = client.fetchCommentary(m.providerMatchId());
            if (r == null || r.items() == null) {
                throw new ProviderException("commentary response has no items");
            }
            if (!m.providerMatchId().equals(r.providerMatchId())) {
                throw new ProviderException("commentary response is for match " + r.providerMatchId());
            }
            // Validate everything before writing anything: one bad item rejects the whole response.
            for (var i : r.items()) {
                if (i == null || i.ballId() == null || i.ballId().isBlank() || i.text() == null) {
                    throw new ProviderException("commentary item is malformed");
                }
            }
            List<CommentaryItem> items = r.items().stream()
                    .map(i -> new CommentaryItem(i.ballId(), i.sequence(), i.revision(), i.innings(),
                            i.over(), i.ball(), i.text(), i.occurredAt()))
                    .toList();
            int changed = store.applyCommentaryBatch(m.matchId(), PROVIDER, items);
            if (changed > 0) {
                log.info("{} commentary: {} item(s) added or corrected", PROVIDER, changed);
            } else {
                log.debug("{} commentary unchanged", PROVIDER);
            }
        } catch (RuntimeException e) {
            log.warn("{} commentary poll failed for {}: {}", PROVIDER, m.providerMatchId(), e.getMessage());
        }
    }
}
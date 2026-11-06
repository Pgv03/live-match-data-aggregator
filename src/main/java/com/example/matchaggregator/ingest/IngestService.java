package com.example.matchaggregator.ingest;

import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.model.Snapshot;
import com.example.matchaggregator.store.MatchStore;
import com.example.matchaggregator.web.WebhookEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {
    public enum Result { APPLIED, DUPLICATE }

    private final MatchStore store;

    public IngestService(MatchStore store) { this.store = store; }

    /**
     * One transaction: delivery record + snapshot + commentary commit together or not at all.
     * A crash before commit leaves no trace, so the provider's retry is processed fresh.
     */
    @Transactional
    public Result ingestPush(WebhookEvent e) {
        String matchId = store.findMatchId(e.provider(), e.providerMatchId())
                .orElseThrow(() -> new UnknownMatchException(
                        "Unknown match " + e.providerMatchId() + " for " + e.provider()));

        if (!store.recordDelivery(e.provider(), e.deliveryId())) {
            return Result.DUPLICATE;
        }
        if (e.snapshot() != null) {
            var s = e.snapshot();
            store.applySnapshot(matchId, e.provider(), new Snapshot(
                    s.sequence(), s.revision(), s.status(), s.innings(),
                    s.runs(), s.wickets(), s.overs(), e.occurredAt()));
        }
        if (e.commentary() != null) {
            var c = e.commentary();
            store.applyCommentary(matchId, e.provider(), new CommentaryItem(
                    c.ballId(), c.sequence(), c.revision(), c.innings(),
                    c.over(), c.ball(), c.text(), e.occurredAt()));
        }
        return Result.APPLIED;
    }
}
package com.example.matchaggregator.web;

import com.example.matchaggregator.model.CommentaryItem;
import com.example.matchaggregator.model.Snapshot;

import java.time.Instant;
import java.util.List;

public final class Views {
    private Views() {}

    public record ScoreView(String source, String status, int innings, int runs,
                            int wickets, String overs, Instant asOf) {
        public static ScoreView from(String source, Snapshot s) {
            return new ScoreView(source, s.status(), s.innings(), s.runs(),
                    s.wickets(), s.overs(), s.asOf());
        }
    }

    /** status and score are null until some provider has published a snapshot. */
    public record MatchView(String id, String title, String status, ScoreView score) {}

    public record CommentaryEntry(String ballId, long sequence, int revision, int innings,
                                  int over, int ball, String text, Instant occurredAt) {
        public static CommentaryEntry from(CommentaryItem c) {
            return new CommentaryEntry(c.ballId(), c.sequence(), c.revision(), c.innings(),
                    c.over(), c.ball(), c.text(), c.occurredAt());
        }
    }

    public record CommentaryView(String source, List<CommentaryEntry> items) {}

    /** Unlike ScoreView, exposes sequence and revision, which the ordering rules act on. */
    public record StoredScoreView(long sequence, int revision, String status, int innings,
                                  int runs, int wickets, String overs, Instant asOf) {
        public static StoredScoreView from(Snapshot s) {
            return new StoredScoreView(s.sequence(), s.revision(), s.status(), s.innings(),
                    s.runs(), s.wickets(), s.overs(), s.asOf());
        }
    }

    public record ProviderSourceView(String provider, StoredScoreView score,
                                     List<CommentaryEntry> commentary) {}

    public record SourcesView(String matchId, String primaryProvider, String servedScoreFrom,
                              String servedCommentaryFrom, List<ProviderSourceView> providers) {}

}
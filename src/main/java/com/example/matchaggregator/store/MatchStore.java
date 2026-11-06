package com.example.matchaggregator.store;

import com.example.matchaggregator.model.Snapshot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.matchaggregator.model.CommentaryItem;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class MatchStore {
    private final JdbcTemplate jdbc;

    public MatchStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Applies the snapshot only if (seq, rev) is strictly newer than the stored one
     * for the same provider. Returns true if stored state changed.
     */
    public boolean applySnapshot(String matchId, String provider, Snapshot s) {
        if (updateIfNewer(matchId, provider, s) == 1) return true;
        try {
            insertSnapshot(matchId, provider, s);
            return true;
        } catch (DuplicateKeyException e) {
            // Row exists: either stored is same/newer (correct no-op), or another
            // thread just inserted it. One retry settles it.
            return updateIfNewer(matchId, provider, s) == 1;
        }
    }

    private int updateIfNewer(String matchId, String provider, Snapshot s) {
        return jdbc.update("""
            UPDATE snapshots
               SET seq=?, rev=?, status=?, innings=?, runs=?, wickets=?, overs=?, as_of=?
             WHERE match_id=? AND provider=?
               AND (seq < ? OR (seq = ? AND rev < ?))
            """,
                s.sequence(), s.revision(), s.status(), s.innings(), s.runs(),
                s.wickets(), s.overs(), ts(s),
                matchId, provider,
                s.sequence(), s.sequence(), s.revision());
    }

    private void insertSnapshot(String matchId, String provider, Snapshot s) {
        jdbc.update("""
            INSERT INTO snapshots (match_id, provider, seq, rev, status, innings, runs, wickets, overs, as_of)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,
                matchId, provider, s.sequence(), s.revision(), s.status(), s.innings(),
                s.runs(), s.wickets(), s.overs(), ts(s));
    }

    public Optional<Snapshot> getSnapshot(String matchId, String provider) {
        return jdbc.query("""
            SELECT seq, rev, status, innings, runs, wickets, overs, as_of
              FROM snapshots WHERE match_id=? AND provider=?
            """,
                (rs, i) -> new Snapshot(rs.getLong("seq"), rs.getInt("rev"), rs.getString("status"),
                        rs.getInt("innings"), rs.getInt("runs"), rs.getInt("wickets"),
                        rs.getString("overs"),
                        rs.getTimestamp("as_of") == null ? null : rs.getTimestamp("as_of").toInstant()),
                matchId, provider).stream().findFirst();
    }

    private static Timestamp ts(Snapshot s) {
        return s.asOf() == null ? null : Timestamp.from(s.asOf());
    }

    /** Applies one ball. Replaces text only if the incoming revision is strictly higher. */
    public boolean applyCommentary(String matchId, String provider, CommentaryItem c) {
        if (updateCommentaryIfNewer(matchId, provider, c) == 1) return true;
        try {
            insertCommentary(matchId, provider, c);
            return true;
        } catch (DuplicateKeyException e) {
            return updateCommentaryIfNewer(matchId, provider, c) == 1;
        }
    }

    private int updateCommentaryIfNewer(String matchId, String provider, CommentaryItem c) {
        return jdbc.update("""
            UPDATE commentary SET rev=?, commentary_text=?, occurred_at=?
             WHERE match_id=? AND provider=? AND ball_id=? AND rev < ?
            """,
                c.revision(), c.text(), tsOf(c.occurredAt()),
                matchId, provider, c.ballId(), c.revision());
    }

    private void insertCommentary(String matchId, String provider, CommentaryItem c) {
        jdbc.update("""
            INSERT INTO commentary (match_id, provider, ball_id, seq, rev, innings, over_no, ball_no, commentary_text, occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,
                matchId, provider, c.ballId(), c.sequence(), c.revision(), c.innings(),
                c.over(), c.ball(), c.text(), tsOf(c.occurredAt()));
    }

    /** One query: ball_id -> stored revision, for diffing a full poll response. */
    public Map<String, Integer> getStoredRevisions(String matchId, String provider) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("SELECT ball_id, rev FROM commentary WHERE match_id=? AND provider=?",
                rs -> { result.put(rs.getString("ball_id"), rs.getInt("rev")); },
                matchId, provider);
        return result;
    }

    /**
     * For polling: skips items that are already stored at the same or higher revision,
     * and sends the rest through applyCommentary, so the SQL rule still guards every write.
     * Returns how many rows actually changed.
     */
    public int applyCommentaryBatch(String matchId, String provider, List<CommentaryItem> items) {
        Map<String, Integer> stored = getStoredRevisions(matchId, provider);
        int changed = 0;
        for (CommentaryItem c : items) {
            Integer storedRev = stored.get(c.ballId());
            if (storedRev != null && storedRev >= c.revision()) continue;
            if (applyCommentary(matchId, provider, c)) changed++;
        }
        return changed;
    }

    /** Ordered by the provider's commentary sequence, never by arrival time. */
    public List<CommentaryItem> getCommentary(String matchId, String provider) {
        return jdbc.query("""
            SELECT ball_id, seq, rev, innings, over_no, ball_no, commentary_text, occurred_at
              FROM commentary WHERE match_id=? AND provider=? ORDER BY seq
            """,
                (rs, i) -> new CommentaryItem(rs.getString("ball_id"), rs.getLong("seq"),
                        rs.getInt("rev"), rs.getInt("innings"), rs.getInt("over_no"),
                        rs.getInt("ball_no"), rs.getString("commentary_text"),
                        rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toInstant()),
                matchId, provider);
    }

    private static Timestamp tsOf(Instant i) { return i == null ? null : Timestamp.from(i); }

}
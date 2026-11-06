package com.example.matchaggregator.store;

import com.example.matchaggregator.model.Snapshot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
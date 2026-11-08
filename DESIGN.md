# Design notes

## Overview

One Spring Boot process, the **aggregator**, ingests a live cricket match from two providers and serves
one set of read APIs. Provider A **pushes** webhook events to it. The aggregator **polls** Provider B.
Both paths write into the same tables, keyed by provider, and the read side applies one precedence
rule to pick what clients see. The mock Provider B and the push generator exist only to exercise the
aggregator and are not part of the system.

## 1. Database choice

**H2 in file mode**: embedded, transactional, durable across restarts, and a reviewer needs to
install nothing. The URL sets `WRITE_DELAY=0`, so every commit is flushed to disk before returning.
H2's default flushes about every 500 ms, which would let a crash silently lose events we had already
acknowledged. The provider never resends those, because it saw a success.

This is a single-process choice: the file cannot be opened by two processes. In production I would
use **Postgres**. It flushes on commit by default, supports replicas, and its conditional upsert
(`INSERT ... ON CONFLICT DO UPDATE ... WHERE`) would replace the insert-then-retry fallback described
below. That fallback relies on a failed `INSERT` not aborting the surrounding transaction, which is
true for H2 and MySQL but not for Postgres.

## 2. Data model

| Table | Key | Holds |
|---|---|---|
| `matches` | `id` | client-facing match, with its configured `primary_provider` |
| `provider_match_map` | `(provider, provider_match_id)` | maps a provider's match id to our match id |
| `delivery_log` | `(provider, delivery_id)` | one row per processed push delivery, for idempotency |
| `snapshots` | `(match_id, provider)` | the latest accepted scoreboard from that provider |
| `commentary` | `(match_id, provider, ball_id)` | one row per logical ball, newest accepted revision |

Everything is stored **per provider**, because sequence numbers from different providers are never
comparable. `overs` is stored as the opaque string the provider sent. `occurredAt` and `asOf` are
stored for display and never decide which update wins. Timestamps are stored in UTC.

## 3. Ordering rules and concurrency

The rules are enforced inside single SQL statements, so the database is the judge:

- **Snapshot:** `UPDATE ... WHERE (seq < :new OR (seq = :new AND rev < :newRev))`. A snapshot is
  applied only if `(sequence, revision)` is strictly newer than the stored one for the same provider.
- **Commentary:** `UPDATE ... WHERE rev < :newRev`. A higher revision replaces the text for that
  `ballId`; an equal or lower one changes nothing. Commentary is read ordered by `sequence`, never by
  arrival time.
- **First write for a row:** if the update touches no rows, try an `INSERT`. If that hits a duplicate
  key (the row exists, or another thread just created it), retry the update once and accept the result.

Because comparison and write are one atomic statement, there is no window in which a concurrent
update can slip between a check and a write, and **no application-level lock is needed**. The tests
send events from 16 threads at once (newest snapshot wins, a repeated delivery applies exactly once,
the highest revision wins a correction race).

## 4. Idempotency

Push idempotency is the primary key `(provider, delivery_id)` on `delivery_log`. The insert, the
snapshot update and the commentary update all run in **one transaction**, so a delivery is either
fully applied and recorded, or not recorded at all. A retried delivery hits the key and is
acknowledged as a duplicate with no state change. On top of that, the ordering rules are themselves
replay-safe: applying the same event twice is a no-op.

## 5. Push path and poll path

| | Push (Provider A) | Poll (Provider B) |
|---|---|---|
| Trigger | provider calls `POST /webhooks/provider-a` | scheduler calls the provider every 5 s (configurable) |
| Duplicate detection | `delivery_log` key, then the ordering rules | ordering rules only; polls carry no delivery id |
| Atomic unit | one event: delivery record + snapshot + commentary | score and commentary are applied independently; one poll's commentary is one transaction |
| Commentary | one ball per event | full list every time; one query loads stored revisions, only new or higher-revision items are written, each still through the SQL rule |
| Bad input | `400`/`404`, nothing recorded | whole response rejected, warning logged, last good state kept |
| Retry | the provider's job, on any non-2xx | the next tick is the retry |

## 6. Provider precedence and conflicts

Each match has a configured **primary provider** (Provider A). The read side serves the primary's
stored snapshot if it has one, otherwise the next provider's. Commentary is served as **one provider's
whole list**, the first provider with any balls, never a merge: two providers' sequences cannot be
interleaved soundly, and `ballId`s are provider-specific. Responses carry a `source` field. Because
score and commentary are chosen independently, the score can come from A while commentary falls back
to B. `GET /matches/{id}/sources` shows each provider's stored state side by side, with sequences and
revisions, and which provider is currently served. It exists to make this rule and the ordering
rules verifiable, and it is the quickest way to see that a stale poll was rejected.

## 7. Failure recovery

- **Webhook response:** `200` means the event is committed to disk, or was already. Anything else
  (including `500`) means the provider should retry. `400` and `404` mean the request itself is
  wrong; a retry cannot help.
- **Crash before commit:** nothing was recorded, no `200` was sent, the retry is processed fresh.
- **Crash after commit, before the reply:** the retry finds the delivery key and is acknowledged as
  a duplicate. Nothing is lost and nothing is applied twice.
- **Provider B errors** (timeout, non-2xx, unparseable or wrong-match body): logged, nothing applied,
  and the last good stored state keeps being served. There is no backoff or automatic failover to the
  other provider; the next tick simply tries again.
- **Restart:** state lives in the database file, so score, status, commentary and the delivery
  history survive. A restart test covers a clean stop and reopen; the README has a manual hard-kill
  procedure.

## 8. Caching

None is implemented, deliberately: reads are primary-key lookups and a cache adds an invalidation
problem with no measured need. For production: a **per-match current-view cache** (Redis or
in-process) updated by the ingest path after commit. Because updates only move forward, the cache
can apply the same newer-than comparison and cannot be overwritten by a stale write. On top of that,
**HTTP caching** with an ETag derived from the score's `(sequence, revision)` and a short max-age
lets a CDN absorb repeated reads.

## 9. First bottleneck

The **poll cycle**, before the database. Matches are polled one after another, each with two HTTP
calls and up to a 2 s timeout. With a few hundred matches even a healthy provider at 50 ms per call
gives a cycle of about 10 s, longer than the 5 s interval, and one slow provider delays every match
behind it. Next is the read path, where every request queries the database and `GET /matches` does
one score lookup per match. Each poll also loads and diffs a match's whole commentary list, so its
cost grows with match length.

## 10. Hundreds of live matches and heavy reads

- **Postgres** with the single-statement conditional upsert, plus read replicas.
- **Ingest through a queue partitioned by match id**: per-match ordering, backpressure and replay,
  consumed by stateless workers.
- **Polling sharded by match** across workers, with concurrent fetches, per-provider rate limits and
  circuit breakers; a cursor or ETag where the provider offers one.
- **Reads** served from the cache above, with a CDN in front.
- **Live fan-out** to subscribers over SSE or WebSocket, fed by pub/sub rather than by polling the API.
- **Housekeeping:** `delivery_log` grows forever today and needs pruning past the providers' retry
  window. Production also needs webhook authentication, metrics (ingest lag, poll success rate) and
  alerting.

## 11. Assumptions and known limitations

- **Single instance.** The H2 file cannot be shared between processes. The ordering rules would stay
  correct with several instances, but the poller would then duplicate work.
- A commentary **correction updates the text, revision and time only**. The ball keeps its original
  `sequence` and descriptive fields (`innings`, `over`, `ball`).
- **Delivery ids are deduplicated per provider across all matches**, as the contract states, so ids
  must be unique per provider. The generator prefixes them with the scenario name.
- The webhook acknowledges `applied` when the event was processed, even if its snapshot lost to a
  newer one. It does not mean every part changed stored state.
- **Status values are stored as sent**, not validated against the five listed, and transitions are
  not restricted. The only ordering rule is the `(sequence, revision)` tuple.
- Commentary comes from **one provider**; a ball missing from the primary is not filled in from the
  other. The score and commentary may come from different providers.
- No automatic failover and no authentication. The mock's admin endpoints are open.
- `GET /matches` looks up each match's score separately.
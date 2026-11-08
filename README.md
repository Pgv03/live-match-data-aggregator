# Live Match Data Aggregator

A backend that ingests one live cricket match from two kinds of provider and serves one set of
read APIs.

- **Provider A pushes** webhook events to the aggregator.
- **Provider B is polled** by the aggregator.
- Both are normalized into one model, stored durably, and served as a single score and a single
  commentary list.

Providers retry, delay, repeat and reorder deliveries, and sometimes correct a fact they already
sent. The aggregator handles all of that deterministically. The reasoning is in
[DESIGN.md](DESIGN.md).

## What is in the repository

| Component | What it is | Port |
|---|---|---|
| **aggregator** | the service: webhook endpoint, Provider B poller, read APIs, H2 database | 8080 |
| **mock-provider** | mock Provider B: scripted timeline, advanced by hand | 8081 |
| **generator** | mock Provider A: sends named push scenarios to the webhook | none |
| **tests** | 47 automated tests | none |

Java 21, Spring Boot, H2 (file mode). One codebase, three entry points.

## Quick start (Docker)

You only need Docker (Compose v2) running. On Windows, use PowerShell and write `curl.exe` instead
of `curl`.

```bash
docker compose up -d --build
```

The first build downloads dependencies and takes a few minutes. When it is up:

```bash
curl http://localhost:8080/matches
```

Follow the aggregator log with `docker compose logs -f aggregator` (Ctrl+C stops following, not the
containers). In the first seconds you may see `provider-b ... poll failed` warnings if the aggregator
started before the mock. That is the designed behavior for an unreachable provider, and it stops on its own.

Ports 8080 and 8081 must be free. To stop: `docker compose down` (keeps the data) or
`docker compose down -v` (also deletes the data, for a clean start).

## Try it

### 1. Provider A: push scenarios

```bash
docker compose run --rm generator                          # every scenario
docker compose run --rm generator --scenario=duplicate     # one scenario
docker compose run --rm generator --help                   # list scenarios
```

The generator prints each event's JSON and the aggregator's reply. Each scenario sends to its own demo
match, so scenarios are independent and can run in any order. Afterwards, check the results:

| Scenario | Shows | Match | Score | Commentary |
|---|---|---|---|---|
| `ordered` | in-order delivery | `demo-ordered` | 5/0 in 0.3 | b1, b2, b3 |
| `duplicate` | repeated `deliveryId` changes nothing (reply `duplicate`) | `demo-duplicate` | 4/1 in 0.2 | b1, b2 (once each) |
| `out-of-order` | higher commentary sequence arrives first | `demo-out-of-order` | 9/0 in 0.3 | b1, b2, b3 (b2 in the middle although it arrived last) |
| `delayed-snapshot` | older snapshot after a newer one loses; its ball is still kept | `demo-delayed-snapshot` | 10/1 in 0.3 (not 7/0) | b1, b2, b3 |
| `correction` | higher-revision ball and score corrections apply | `demo-correction` | 5/1 in 0.2 | b1, b2 with corrected text, revision 2 |
| `worked-example` | the assignment's six-step example | `match-1001` | 10/1 in 1.1 | b1, b2 ("out, review stays out"), b3, b4 |
| `invalid` | bad input is rejected | none | none | replies 400, 404, 400 |

```bash
curl http://localhost:8080/matches/demo-delayed-snapshot/score
curl http://localhost:8080/matches/demo-delayed-snapshot/commentary
```

Run `--scenario=all` a second time: every event is answered `duplicate` and nothing changes. A second
run is a no-op, so for a clean run reset the data (see below). Rejected events (the `invalid`
scenario) are never recorded, so they are judged afresh each time.

### 2. Provider B: the scripted mock

The mock serves one step of a fixed script until you advance it, so nothing is random. The aggregator
polls it every 5 seconds.

```bash
curl http://localhost:8081/admin/state               # which step the mock is on
curl -X POST http://localhost:8081/admin/advance     # next step (stops at the last one)
curl -X POST http://localhost:8081/admin/reset       # back to step 0
curl http://localhost:8081/provider/matches/b-match-1001/score        # what the mock serves
curl http://localhost:8081/provider/matches/b-match-1001/commentary
```

After each advance, wait about 6 seconds, then look at what the aggregator stored:

```bash
curl http://localhost:8080/matches/match-1001/sources
```

| Step | Mock score | Mock commentary | What `provider-b` should show in `/sources` |
|---|---|---|---|
| 0 | seq 1, 4/0 | b1 | seq 1, 4/0; ball b1 |
| 1 | same as step 0 | same | unchanged |
| 2 | seq 2, 10/1 | b1, b2 | seq 2, 10/1; b1, b2 |
| 3 | **stale**: seq 1, 4/0 with a newer timestamp | b1, b2 | still seq 2, 10/1: the stale response lost |
| 4 | 503 | 503 | unchanged; warnings in the log; clients still get the last good data |
| 5 | seq 3, 14/1 | b1, b2 (corrected, revision 2), b3 | seq 3, 14/1; b2 corrected; b3 added |
| 6 | 503 | b1, b2, b3, b4 | score unchanged; b4 appears (a failing score endpoint does not block commentary) |

Log lines to look for: `provider-b score applied`, `provider-b commentary: N item(s) added or
corrected`, and `provider-b ... poll failed`.

### 3. One view from two providers

Each match has a configured primary provider (Provider A). On a fresh start, `match-1001` has only
Provider B data, so `GET /matches/match-1001/score` answers with `"source":"provider-b"`. Run the
`worked-example` scenario and the same URL answers with `"source":"provider-a"`, while
`/matches/match-1001/sources` still shows both providers' stored state side by side. Sequence numbers
are never compared across providers.

### 4. Survive a hard crash

```bash
docker compose run --rm generator --scenario=ordered
curl http://localhost:8080/matches/demo-ordered/score      # 5/0 in 0.3
docker compose kill aggregator                             # abrupt stop, no clean shutdown
docker compose up -d aggregator
curl http://localhost:8080/matches/demo-ordered/score      # after a few seconds: still 5/0 in 0.3
docker compose run --rm generator --scenario=ordered       # every reply is "duplicate"
```

Every commit is flushed to disk before the webhook answers, so an acknowledged event survives a kill,
and the delivery history survives with it.

## API

| Method and path | Meaning |
|---|---|
| `GET /matches` | known matches, each with status and a summary score once one exists |
| `GET /matches/{id}` | one match; `status` and `score` are `null` until a score exists |
| `GET /matches/{id}/score` | current status, innings, runs, wickets, overs, and which provider it came from (`404` if no score yet) |
| `GET /matches/{id}/commentary` | one entry per logical ball, ordered by commentary sequence, newest revision; empty list before any ball |
| `GET /matches/{id}/sources` | each provider's stored score (with sequence and revision) and commentary, and which one is served. Added to make the precedence and ordering rules verifiable |
| `POST /webhooks/provider-a` | Provider A's push endpoint |

An unknown match id returns `404`. Example score response:

```json
{"source":"provider-a","status":"live","innings":1,"runs":10,"wickets":1,"overs":"1.1","asOf":"2026-10-22T14:04:00Z"}
```

Example commentary response (shortened):

```json
{"source":"provider-a","items":[
  {"ballId":"b1","sequence":1,"revision":1,"innings":1,"over":0,"ball":1,"text":"four","occurredAt":"2026-10-22T14:01:00Z"}
]}
```

### Webhook contract

```bash
curl -i -X POST http://localhost:8080/webhooks/provider-a \
  -H "Content-Type: application/json" --data @samples/d1.json
```

| Reply | Meaning |
|---|---|
| `200 {"status":"applied"}` | the event was processed and committed to disk |
| `200 {"status":"duplicate"}` | this `deliveryId` was already processed; nothing changed |
| `400` | malformed payload, or `provider` does not match the endpoint; retrying cannot help |
| `404` | unknown provider match id; retrying cannot help |
| `500` | our failure; the provider should retry |

`applied` means the event was processed, not that every part changed state: a snapshot older than the
stored one is ignored while a late ball in the same event is still stored. A provider should retry on any
non-2xx. If we crash before committing, nothing was recorded and the retry is processed fresh. If we crash
after committing but before replying, the retry comes back `duplicate`.

## Tests

```bash
docker compose run --rm tests        # all 47 tests, inside a container
```

The first run downloads the test libraries and takes a few minutes. Without Docker (JDK 21 needed):

```bash
./mvnw test                          # Windows PowerShell: .\mvnw.cmd test
```

Where each required case is covered:

| Required case | Test |
|---|---|
| ordered ingestion | `ScenarioHttpTest.orderedIngestion`, `PushIngestTest.workedExampleFromAssignment` |
| duplicate push delivery | `ScenarioHttpTest.duplicateDeliveryChangesNothing`, `ConcurrencyTest.aRepeatedDeliveryIsAppliedExactlyOnce` |
| older snapshot after a newer one, late ball still in sequence order | `ScenarioHttpTest.olderSnapshotLosesButItsBallIsStillServedInOrder`, `ScenarioHttpTest.lateBallIsInsertedInSequenceOrder` |
| commentary correction | `ScenarioHttpTest.commentaryAndScoreCorrectionsAreApplied`, `CommentaryRulesTest` |
| repeated identical polls | `PollingServiceTest.repeatedIdenticalPollsChangeNothing` |
| stale poll | `PollingServiceTest.staleResponseLosesEvenWithANewerTimestamp` |
| provider error leaves last good state | `PollingServiceTest.providerErrorLeavesTheLastGoodStateInPlace` |
| restart against the same database | `RestartTest` (two tests) |

Also covered: concurrent updates (`ConcurrencyTest`), HTTP status codes and read-API edge cases
(`ScenarioHttpTest`), the precedence rule (`MatchQueryServiceTest`), and the mock's script
(`TimelineTest`, `ScenariosTest`). The poller tests call `pollOnce()` with a stub provider, so they use
no network and no sleeping.

## Running without Docker

Needs JDK 21. Use three terminals. The project has several entry points, so each command names its
main class. On Windows PowerShell use `.\mvnw.cmd` and keep the quotes.

```bash
./mvnw spring-boot:run "-Dspring-boot.run.main-class=com.example.matchaggregator.MatchAggregatorApplication"
./mvnw spring-boot:run "-Dspring-boot.run.main-class=com.example.mockprovider.MockProviderApplication"
./mvnw -q spring-boot:run "-Dspring-boot.run.main-class=com.example.pushgenerator.PushGenerator" "-Dspring-boot.run.arguments=--scenario=all"
```

The database is the `data/` folder next to the project; delete it (with the aggregator stopped) for a
clean start.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `poller.enabled` | `true` (`false` in tests) | run the Provider B poller |
| `poller.interval` | `5s` | delay between poll cycles |
| `poller.timeout` | `2s` | connect and read timeout for provider calls |
| `poller.provider-b-base-url` | `http://localhost:8081` | where Provider B lives (Compose points it at `mock-provider`) |
| `spring.datasource.url` | `jdbc:h2:file:./data/aggregator;WRITE_DELAY=0` | the database; `WRITE_DELAY=0` flushes every commit |
| generator `--url=` / `WEBHOOK_URL` | `http://localhost:8080/webhooks/provider-a` | webhook the generator sends to |

## Key decisions and assumptions

The full reasoning is in [DESIGN.md](DESIGN.md). The short version:

- **The ordering rules live in single SQL statements.** A snapshot applies only if its
  `(sequence, revision)` is strictly newer than the stored one for that provider; a commentary item
  replaces a ball's text only with a higher revision. No locks are needed, and concurrent updates obey
  the same rules.
- **Push idempotency** is the primary key `(provider, deliveryId)`, in the same transaction as the
  snapshot and commentary writes.
- **Provider B polling** applies the same rules; an unchanged, stale or failed poll changes nothing,
  and the last good state keeps being served.
- **Precedence:** a configured primary provider (A) wins; commentary is one provider's whole list,
  never a merge, because two providers' sequences cannot be compared.
- **Timestamps** (`occurredAt`, `asOf`) are stored but never decide which update wins.
- **Durability:** every commit is flushed to disk before a webhook is acknowledged.
- **Assumptions:** a correction changes a ball's text and revision, not its sequence; delivery ids are
  unique per provider across matches; status strings are stored as sent; one instance only (the H2
  file cannot be shared); no authentication, as the assignment allows.

## Project layout

```
src/main/java/com/example/
  matchaggregator/   the service: web (webhook + read APIs), ingest, poll, query, store, model
  mockprovider/      mock Provider B: scripted timeline and its admin controls
  pushgenerator/     mock Provider A: scenarios and the command-line sender
src/main/resources/  application.properties, mock-provider.properties, schema.sql, data.sql (seed matches)
src/test/            unit, integration (real HTTP), concurrency and restart tests
samples/             example webhook payloads for curl
Dockerfile, docker-compose.yml, DESIGN.md
```
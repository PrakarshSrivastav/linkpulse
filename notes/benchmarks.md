# Benchmarks

## Setup
- Machine: <CPU, cores, RAM>, Windows 11. App, Postgres, Redis and k6 all on the same machine.
- Data: 100,000 links (`bench/seed.sql`); requests spread randomly over 1,000 hot codes.
- Load: `bench/redirects.js`, k6, 30 s per run, no think time, redirects not followed.
- App: built jar, SQL logging off, Hikari pool = 10 (default), 15 s warm-up discarded.

## Single query
`EXPLAIN ANALYZE SELECT * FROM links WHERE code = 'b050000'`
→ Index Scan on uk_links_code, 0.051 ms execution (0.119 ms planning).

## Results

| Run | VUs | p50 | p95 | p99 | req/s | checks |
|---|---|---|---|---|---|---|
| Postgres only | 10 | 1.12 ms | 1.68 ms | 2.03 ms | 7,928 | 100% |
| Postgres only | 100 | 9.97 ms | 19.24 ms | 27.45 ms | 9,528 | 100% |
| Redis cache | 10 | | | | | |
| Redis cache | 100 | | | | | |
| Redis stopped (fallback) | 100 | | | | | |

Repeat of Postgres only at 100 VUs: 9,198 req/s, p50 9.59 ms, p99 30.99 ms (within ~3%).

Cache hit ratio after Redis runs: ___ %

## Findings (Postgres only)
- The DB query is 0.05 ms of a ~1.1 ms request (~5%); the rest is HTTP, Spring, Hibernate, JDBC and the network hop.
- 10x the users gave only +20% throughput while p50 went up ~9x → the system is saturated.
- Little's Law (concurrency = throughput × latency) holds: 7,928 × 1.18 ms ≈ 9.4 and 9,528 × 10.26 ms ≈ 98 in flight.
- Bottleneck = Hikari pool: hikaricp.connections.pending = 69 / 84 / 88 during the 100-VU run.
  ~85 of 100 requests were waiting for one of 10 connections, so latency at 100 VUs is mostly queueing.

## Prediction before adding Redis
At 100 VUs I expect p50 to go from ~10 ms to ___ ms, because cache hits never borrow a DB connection.
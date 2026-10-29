# Benchmarks

## Setup
- Machine: Intel Core i7-14650HX (16 cores / 24 threads), 32 GB RAM, Windows 11.
  App, Postgres, Redis and k6 all on the same machine.
- Data: 100,000 links (`bench/seed.sql`); requests spread randomly over 1,000 hot codes.
- Load: `bench/redirects.js`, k6, 30 s per run, no think time, redirects not followed.
- App: built jar, SQL logging off, Hikari pool = 10 (default), 15 s warm-up discarded.
- Redis: 7.4, maxmemory 100mb, allkeys-lru, no persistence; cache flushed before the Redis runs.

## Single query
`EXPLAIN ANALYZE SELECT * FROM links WHERE code = 'b050000'`
→ Index Scan on uk_links_code, 0.051 ms execution (0.119 ms planning).

## Results

| Run | VUs | p50 | p95 | p99 | req/s | checks |
|---|---|---|---|---|---|---|
| Postgres only | 10 | 1.12 ms | 1.68 ms | 2.03 ms | 7,928 | 100% |
| Postgres only | 100 | 9.97 ms | 19.24 ms | 27.45 ms | 9,528 | 100% |
| Redis cache | 10 | 0.72 ms | 1.38 ms | 2.03 ms | 11,228 | 100% |
| Redis cache | 100 | 3.26 ms | 4.79 ms | 6.47 ms | 28,470 | 100% |
| Redis stopped (fallback) | 100 | 404 ms | 416 ms | 481 ms | 244 | 100% |

Repeat of Postgres only at 100 VUs: 9,198 req/s, p50 9.59 ms, p99 30.99 ms (within ~3%).

Cache hit ratio after the Redis runs: 99.92% (1,337,861 hits, 1,009 misses).

## Findings (Postgres only)
- The DB query is 0.05 ms of a ~1.1 ms request (~5%); the rest is HTTP, Spring, Hibernate, JDBC and the network hop.
- 10x the users gave only +20% throughput while p50 went up ~9x → the system is saturated.
- Little's Law (concurrency = throughput × latency) holds: 7,928 × 1.18 ms ≈ 9.4 and 9,528 × 10.26 ms ≈ 98 in flight.
- Bottleneck = Hikari pool: hikaricp.connections.pending = 69 / 84 / 88 during the 100-VU run.
  ~85 of 100 requests were waiting for one of 10 connections, so latency at 100 VUs is mostly queueing.

## Prediction before adding Redis
I expected a clear gain at 100 VUs because cache hits never borrow a DB connection,
but I did not write down a number before running the test, so there is no numeric prediction to compare.
Lesson: next time, write the predicted number into this file and commit it before the experiment.

## Findings (with Redis)
- 100 VUs: p50 9.97 → 3.26 ms (3.1x), p99 27.45 → 6.47 ms (4.2x), throughput 9,528 → 28,470 req/s (3.0x).
- hikaricp.connections.pending during the 100-VU run: 0 (was 69–88). The cache removed the pool queue.
- The tail improved more than the median, because queueing hurts the slowest requests most.
- Little's Law still holds: 28,470 × 3.38 ms ≈ 96 in flight. The remaining time is likely CPU
  (k6 generating ~28k req/s shares the same cores as the app).
- 10 VUs: p50 −36%, throughput +42%, p99 unchanged. There was no pool queue at 10 users,
  so the cache only saved the Hibernate/JDBC/Postgres work. The benefit grows with concurrency.
- 1,009 misses instead of 1,000: a few VUs missed the same key at the same moment during warm-up
  and both queried the DB before the cache was filled (a small cache stampede).

## Surprise: the fallback
- With Redis stopped, every redirect was still correct (100% 302), but p50 was 404 ms and throughput 244 req/s,
  about 40x worse than running without any cache.
- Cause: while disconnected, the Redis client queues commands until the 200 ms timeout instead of failing fast,
  and each request makes two Redis calls (get + put) → ~2 × 200 ms. 100 VUs / 0.406 s ≈ 246 req/s, which matches.
- Possible fixes: a circuit breaker that skips Redis after repeated failures, fail-fast client settings
  while disconnected, skipping the put after a failed get, and a lower timeout.
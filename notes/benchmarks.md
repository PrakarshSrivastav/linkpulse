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

## Mission 4 prediction (sync click insert on every redirect, 100 VUs)
Written before running.
- req/s: ~8000  (Redis-only was 28,470)
- p99: ~45 ms (Redis-only was 6.47 ms)
- Hikari pending: ~30 (Redis-only was 0)
- Reasoning: Moving from Redis-only (pure in-memory) to sync DB insert on hot path. 
- Every redirect now pays JDBC insert cost (network + fsync) and contends for Hikari pool. With 100 VUs vs pool of 10-20, throughput becomes pool_size / insert_time limited.
- Expect 70% drop in req/s, 7-10x jump in p99, and pending >0 due to connection queue.

## Mission 4 result: sync click insert (100 VUs, 30 s)
| Run | VUs | p50 | p95 | p99 | req/s | checks |
|---|---|---|---|---|---|---|
| Redis + sync click INSERT | 100 | 17.92 ms | 22.61 ms | 34.76 ms | 5,479 | 100% |

- hikaricp.connections.pending: 83 / 87 / 82.
- Clicks recorded: 241,230 = 76,736 (warm-up) + 164,494 (run). No click lost.
- Throughput −81% vs Redis-only (28,470), and even below Postgres-only without cache (9,528):
  an INSERT + commit (WAL flush) holds a connection far longer than an indexed SELECT.
- 10 connections / 5,479 req/s ≈ 1.8 ms per click per connection, vs 0.05 ms for the SELECT.
- Little's Law: 5,479 × 18.19 ms ≈ 99.7 in flight (= 100 VUs).
- Prediction vs reality: req/s 8,000 → 5,479 (worse than expected); p99 45 → 34.76 ms (better);
  pending 30 → 82–87 (I underestimated the queue: with the pool as bottleneck, nearly all
  requests wait for a connection).
- Conclusion: one synchronous write per redirect undoes the cache. The write must leave the hot path.

## Mission 4 prediction (async: in-memory queue + batch insert every 1 s, 100 VUs)
Written before running.
- req/s: ~25000  (Redis-only 28,470 / sync insert 5,479)
- p99: ~8 ms (Redis-only 6.47 / sync 34.76)
- Hikari pending: ~0
- Clicks lost (dropped): ~0 steady-state, ~25000 at risk on crash/shutdown (last 1s batch)
- Reasoning: Moves DB work off hot path. Request path is back to Redis GET + queue.offer() (O(1), ~0.1ms), so throughput should recover to ~85-90% of Redis-only.
- No blocking on Hikari in request threads, so pending goes back to 0. p99 stays close to Redis-only + minor queue contention. Single background thread does batch insert every 1s using 1 connection, so no pool contention. 
- Clicks lost is 0 if queue is unbounded/large enough, but all in-memory clicks from last flush interval are lost on crash - not durable.

## Mission 4 result: async clicks (queue + batch insert every 1 s, 100 VUs, 30 s)
| Run | VUs | p50 | p95 | p99 | req/s | checks |
|---|---|---|---|---|---|---|
| Redis-only (Mission 2) | 100 | 3.26 ms | 4.79 ms | 6.47 ms | 28,470 | 100% |
| Redis + sync click INSERT | 100 | 17.92 ms | 22.61 ms | 34.76 ms | 5,479 | 100% |
| Redis + async click queue | 100 | 5.09 ms | 10.73 ms | 15.21 ms | 16,268 | 100% |

- hikaricp.connections.pending: 0 (sync: 82–87). Redirect threads no longer touch the pool.
- Clicks recorded: 618,107 = 130,030 (warm-up) + 488,077 (run). dropped 0, failed 0.
- Async vs sync: 3.0x throughput, p99 2.3x lower.
- Async vs Redis-only: 57% of the throughput. Not yet measured why. Hypotheses: Postgres inserting
  ~16k rows/s competes for the same CPU as app, Redis and k6 (one machine); a single lock in
  ArrayBlockingQueue shared by 100 request threads; extra allocation (one Click + Instant per redirect).
- Little's Law: 16,268 × 5.93 ms ≈ 96.5 in flight.
- Prediction vs reality: req/s 25,000 → 16,268 (too optimistic: I ignored that the database
  work did not disappear, it moved to the background on the same machine); p99 8 → 15.21 ms;
  pending 0 and drops 0 were right.
- Trade-off: clicks appear in the database up to ~1 s later (eventually consistent), and a crash
  loses what is still in the queue (graceful shutdown flushes it via @PreDestroy).


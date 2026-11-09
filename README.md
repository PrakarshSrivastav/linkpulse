# LinkPulse

A URL shortener backend in Spring Boot 4: Postgres for storage, a Redis cache-aside layer on the redirect path,
JWT authentication with per-user link ownership, and asynchronous click analytics.

- **Live:** https://linkpulse-1ju5.onrender.com ([API docs](https://linkpulse-1ju5.onrender.com/swagger-ui.html)).
  Free tier: the first request after ~15 minutes idle takes up to a minute while the server wakes up.
  Runs on Render (Docker, Frankfurt) with Neon Postgres and Render Key Value.
- Interactive API docs: `/swagger-ui.html` (OpenAPI spec at `/v3/api-docs`)
- Benchmarks and what they showed: [notes/benchmarks.md](notes/benchmarks.md)

## Run it locally

Requirements: JDK 21+, Docker.

```bash
cp .env.example .env            # local-only Postgres and Redis passwords
docker compose up -d            # Postgres on 127.0.0.1:5433, Redis on 127.0.0.1:6379
```

The app refuses to start without a JWT signing secret of at least 32 bytes. Generate one per shell session:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); $env:JWT_SECRET = [Convert]::ToBase64String($b)
.\mvnw.cmd spring-boot:run
```

Then open http://localhost:8080 (landing page) or http://localhost:8080/swagger-ui.html.
Actuator health and metrics are only on http://localhost:9091/actuator (bound to localhost).

Tests (`.\mvnw.cmd test`) use a test-only secret from `src/test/resources/application.properties`.
They run against the same local Postgres and Redis and delete their data.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | none, required | 32+ bytes; never reuse a value that was ever committed |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | local Docker Postgres | JDBC URL |
| `REDIS_URL` | local Docker Redis | `redis://:password@host:port` |
| `BASE_URL` | `http://localhost:8080` | used to build short URLs |
| `PORT` | `8080` | public HTTP port |
| `MANAGEMENT_PORT` | `9091` | actuator, bound to 127.0.0.1 |
| `SQL_LOG_LEVEL` | `info` | `debug` logs every SQL statement |
| `RATE_LIMIT_ENABLED` | `true` | token buckets in Redis: login 10/min and register 5/10 min per IP, link creation 30/min per user; `429` + `Retry-After` |
| `CLICK_QUEUE_CAPACITY` | `100000` | max clicks waiting for the batch insert; use ~10000 on a 512 MB host |
| `FORWARD_HEADERS_STRATEGY` | `none` | set `native` only behind a proxy that is the sole way in, so the client IP comes from `X-Forwarded-For` |

## Credits

- Landing page design follows [Hallmark](https://github.com/nutlope/hallmark) by Together AI (MIT License,
  Copyright (c) 2026 Hallmark contributors), using its Cobalt theme tokens.
- Security review run with Cloudflare's [security-audit-skill](https://github.com/cloudflare/security-audit-skill).

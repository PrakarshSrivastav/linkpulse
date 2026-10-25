# LinkPulse: project walkthrough

Every file, in the order it was built, followed by how the pieces work together at runtime.

---

## 1. The big picture

```
            HTTP                 Java (Spring Boot app, port 8080)                     Docker
 ┌────────┐  POST /api/links  ┌────────────────┐   ┌─────────────┐   ┌───────────────┐   ┌──────────────────┐
 │ client │ ────────────────▶ │ LinkController │──▶│             │──▶│               │   │                  │
 │ (curl, │                   └────────────────┘   │ LinkService │   │LinkRepository │──▶│ Postgres 16      │
 │browser)│  GET /{code}      ┌────────────────┐   │             │   │ (Spring Data) │   │ table: links     │
 │        │ ────────────────▶ │RedirectControl.│──▶│             │──▶│               │   │ port 5433 → 5432 │
 └────────┘                   └────────────────┘   └──────┬──────┘   └───────────────┘   └──────────────────┘
      ▲                                                   │ uses
      │  errors as JSON       ┌───────────────────┐  ┌────▼───────────────┐
      └────────────────────── │ApiExceptionHandler│  │ CodeGenerator      │
                              └───────────────────┘  │ (RandomBase62Gen.) │
                                                     └────────────────────┘
```

**Layers, and the one job each does:**

| Layer | Files | Knows about |
|---|---|---|
| Web (HTTP) | `LinkController`, `RedirectController`, `CreateLinkRequest/Response`, `ApiExceptionHandler` | URLs, JSON, status codes. **No** SQL. |
| Business | `LinkService`, `CodeGenerator`, `RandomBase62Generator`, the exceptions | Rules (aliases, retries, expiry). **No** HTTP. |
| Persistence | `Link`, `LinkRepository` | Tables and queries. **No** business rules. |
| Schema | `V1__links.sql` (Flyway) | The real database structure. |

Each layer only calls the layer below it, and HTTP concepts never leak into the service. That's why the service can be tested without HTTP (`LinkServiceTest`) and the web layer without a real server (`LinkApiTest` with MockMvc).

---

## 2. Project structure

```
linkpulse/
├── pom.xml                              ← dependencies + build
├── mvnw, mvnw.cmd, .mvn/wrapper/        ← Maven wrapper (pins Maven 3.9.11)
├── docker-compose.yml                   ← Postgres 16 in Docker
├── .gitignore, .gitattributes           ← what git ignores / line endings
├── notes/                               ← study notes (this file)
└── src/
    ├── main/
    │   ├── java/com/example/linkpulse/
    │   │   ├── LinkpulseApplication.java        ← entry point
    │   │   └── link/                             ← the "link" feature
    │   │       ├── Link.java                     ← entity (one row)
    │   │       ├── LinkRepository.java           ← DB access
    │   │       ├── CodeGenerator.java            ← interface
    │   │       ├── RandomBase62Generator.java    ← SecureRandom implementation
    │   │       ├── LinkService.java              ← business logic
    │   │       ├── CreateLinkRequest.java        ← JSON in (validated)
    │   │       ├── CreateLinkResponse.java       ← JSON out
    │   │       ├── LinkController.java           ← POST /api/links
    │   │       ├── RedirectController.java       ← GET /{code}
    │   │       ├── ApiExceptionHandler.java      ← exceptions → HTTP status
    │   │       └── *Exception.java (×4)          ← business errors
    │   └── resources/
    │       ├── application.yml                   ← configuration
    │       └── db/migration/V1__links.sql        ← schema
    └── test/java/com/example/linkpulse/
        ├── LinkpulseApplicationTests.java        ← does the app start?
        └── link/
            ├── LinkRepositoryTest.java           ← entity + repository vs real Postgres
            ├── RandomBase62GeneratorTest.java    ← pure unit test
            ├── LinkServiceTest.java              ← retry / alias logic, fake generator
            ├── LinkApiTest.java                  ← HTTP behaviour via MockMvc
            └── ConcurrentAliasTest.java          ← 10 parallel requests, real server
```

---

## 3. Every file, in build order

### Step 0: Skeleton and infrastructure

#### `pom.xml`
The **Maven build file**. It declares:
- **Parent `spring-boot-starter-parent` 4.1.1**, which supplies compatible versions for every Spring library, so the dependencies need no `<version>` tags.
- **`java.version` 21**: the code is compiled for Java 21 (the machine runs JDK 25, which can target 21).
- **Dependencies:**
  - `starter-webmvc`: Spring MVC plus embedded Tomcat (HTTP server)
  - `starter-data-jpa`: Hibernate plus Spring Data repositories
  - `starter-flyway` + `flyway-database-postgresql`: migrations (the Postgres module is required since Flyway 10)
  - `starter-validation`: Bean Validation (`@NotBlank`, `@Pattern`, …)
  - `starter-actuator`: `/actuator/health`
  - `postgresql` (**runtime** scope): the JDBC driver, never imported directly by code
  - `*-test` starters (**test** scope): JUnit 5, AssertJ, MockMvc, Spring test support
- **`spring-boot-maven-plugin`**: `mvnw spring-boot:run` and building an executable jar.

#### `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
The **Maven wrapper**. `mvnw` downloads the exact Maven version in `distributionUrl` (**3.9.11**) on first use, so anyone (and later CI or Docker) builds with the same Maven. It's pinned to 3.9.11 because IntelliJ 2026.1.2 couldn't import projects with Maven 3.10.0.

#### `docker-compose.yml`
Runs **Postgres 16** in a container:
- `environment`: creates the database `linkpulse` with user and password `linkpulse` **on the first start only**.
- `ports: "5433:5432"`: your PC's port 5433 maps to the container's 5432 (5432 on Windows is taken by a native Postgres 18).
- `volumes: pgdata`: the data survives container restarts and `docker compose down` (but not `down -v`).

Commands: `docker compose up -d` (start), `stop` / `start`, `down`.

#### `src/main/resources/application.yml`
All configuration, read by Spring Boot at startup:

| Key | Meaning |
|---|---|
| `linkpulse.base-url` | Your **own** setting, used to build `shortUrl`. In production, it becomes the real domain. |
| `logging.level.org.hibernate.SQL: debug` | Prints every SQL statement Hibernate runs. |
| `spring.datasource.*` | JDBC URL (port **5433**), username, password. Spring Boot builds a HikariCP connection pool from these. |
| `spring.jpa.hibernate.ddl-auto: validate` | Hibernate **checks** that the entities match the tables and **never changes** the schema. Flyway owns the schema. |
| `spring.jpa.open-in-view: false` | The DB session closes when the service returns, so there are no hidden lazy-loading queries while rendering the response. |
| `management.endpoint.health.show-details: always` | `/actuator/health` shows the db and disk details. |

#### `LinkpulseApplication.java`
The entry point. `main` calls `SpringApplication.run(...)`. **`@SpringBootApplication`** combines three annotations:
- `@EnableAutoConfiguration`: looks at the classpath and configures things automatically (Tomcat, the DataSource, JPA, Flyway, …);
- `@ComponentScan`: finds `@Service`, `@Component`, `@RestController` and similar classes **in this package and below** (that's why everything lives under `com.example.linkpulse`);
- `@Configuration`.

Without the annotation, there's no auto-configuration and no web server, which is the "no ServletWebServerFactory bean" error from day 1.

---

### Step 1: Schema

#### `src/main/resources/db/migration/V1__links.sql`
The first Flyway migration, which creates the `links` table:

| Column | Type | Purpose |
|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Internal id, generated by the DB. |
| `code` | `VARCHAR(32) NOT NULL` | The random 7-character code **or** a custom 4–32-character alias. |
| `long_url` | `VARCHAR(2048) NOT NULL` | The redirect target. |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | When it was created (an absolute instant). |
| `expires_at` | `TIMESTAMPTZ` (nullable) | `NULL` means the link never expires. |
| `uk_links_code` | `UNIQUE (code)` | **The core of insert-and-catch.** It also serves as the index for lookups by code. |

At startup, Flyway compares the files in `db/migration` with the `flyway_schema_history` table and runs only the new ones, recording their checksums. **Never edit an applied migration. Add `V2__…sql` instead.**

---

### Step 2: Entity and repository

#### `link/Link.java` (the entity)
A Java class that maps **one row** of `links`.
- `@Entity` + `@Table(name = "links")`: Hibernate manages this class and maps it to the `links` table.
- `@Id @GeneratedValue(strategy = IDENTITY)`: the DB generates the id, and Hibernate reads it back after the INSERT.
- `@Column(..., updatable = false)` on `code`, `longUrl`, `createdAt`: these are never changed after creation.
- `protected Link()`: the no-arg constructor **for Hibernate only** (it creates an empty object, then fills the fields from the row).
- `public Link(code, longUrl, expiresAt)`: the only way **your** code creates links. It sets `createdAt = Instant.now()`.
- `isExpiredAt(Instant now)`: business rule: expired if `expiresAt` is set and `now ≥ expiresAt`. `now` is passed in for testability.
- Getters only, with no setters: a link is immutable after creation.

#### `link/LinkRepository.java`
```java
interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByCode(String code);
}
```
- No implementation class: **Spring Data generates one at startup** (`SimpleJpaRepository` behind a proxy).
- `JpaRepository<Link, Long>` provides `save`, `saveAndFlush`, `findById`, `findAll`, `count`, `deleteAll`, …
- `findByCode` is a **derived query**: the method name is parsed into `select … from links where code = ?`. It uses the `uk_links_code` index.
- Every repository method is **transactional by itself** (writes use a read-write transaction, reads use a read-only one). If the caller has no transaction, each call gets its own. If the caller has one, the call **joins** it. That's the key fact behind A2.

---

### Step 3: Code generation and business logic

#### `link/CodeGenerator.java`
A one-method interface, `String generate()`. The service depends on this abstraction, so tests can substitute a fake that returns exact codes.

#### `link/RandomBase62Generator.java`
The production implementation (a `@Component`, so Spring injects it wherever a `CodeGenerator` is needed).
- Alphabet: `0-9A-Za-z` (62 characters, URL-safe).
- Length 7, giving 62⁷ ≈ 3.5 trillion possible codes.
- `SecureRandom` (unpredictable) with `nextInt(62)` (uniform, no modulo bias).

#### `link/LinkService.java` (business logic)
A `@Service` bean with constructor-injected `LinkRepository` and `CodeGenerator`.

**`create(longUrl, alias, expiresAt)`**: deliberately **not** `@Transactional`.
- If `alias != null`, it calls `createWithAlias`: one `saveAndFlush`. On a `uk_links_code` violation it throws `AliasTakenException`, and on any other integrity error it re-throws.
- Otherwise it calls `createWithRandomCode`: up to `MAX_ATTEMPTS = 3`. Each attempt generates a code, calls `saveAndFlush` (**its own transaction**), and on a `uk_links_code` violation logs a warning and tries again. If all attempts collide, it throws `CodeGenerationException`.

**`resolve(code)`**: `findByCode`, which throws `LinkNotFoundException` if missing and `LinkExpiredException` if `isExpiredAt(Instant.now())`. Otherwise it returns the link.

**`isCodeCollision(e)`**: walks the exception's cause chain looking for Hibernate's `ConstraintViolationException` named `uk_links_code`. Anything else isn't a collision and gets re-thrown.

#### The four business exceptions (all `RuntimeException`)
| Exception | Thrown when | Becomes |
|---|---|---|
| `AliasTakenException` | The chosen alias already exists | **409 Conflict** |
| `CodeGenerationException` | 3 random codes in a row collided | **503 Service Unavailable** |
| `LinkNotFoundException` | No row has that code | **404 Not Found** |
| `LinkExpiredException` | The row exists but has expired | **410 Gone** |

They describe **what** went wrong in business terms; `ApiExceptionHandler` decides the HTTP status.

---

### Step 4: Web layer

#### `link/CreateLinkRequest.java` (JSON in)
A `record` with validation annotations:
- `url`: `@NotBlank`, `@Size(max = 2048)`, `@Pattern(^https?://\S+$)` (only http and https; blocks `javascript:` and similar).
- `alias`: `@Pattern(^[A-Za-z0-9_-]{4,32}$)`. It's **optional**, since `null` passes `@Pattern`.
- `expiresAt`: `@Future` (no expiry in the past). Jackson parses ISO strings such as `"2026-10-25T12:00:00Z"` into an `Instant`.

#### `link/CreateLinkResponse.java` (JSON out)
A `record(code, shortUrl)`, serialised to `{"code": "...", "shortUrl": "..."}`.

DTOs are kept separate from the entity so that the API shape and the DB model can change independently, and clients can never set `id` or `createdAt`.

#### `link/LinkController.java`: `POST /api/links`
- `@RestController` + `@RequestMapping("/api/links")`.
- `@Value("${linkpulse.base-url}")` injects the setting from `application.yml`.
- `create(@Valid @RequestBody CreateLinkRequest)`: JSON becomes the record, `@Valid` runs validation, the service creates the link, and it returns **201 Created** with a `Location` header and the response body.

#### `link/RedirectController.java`: `GET /{code}`
- Calls `service.resolve(code)` and returns **302 Found** with `Location: <long URL>` and no body.
- It's 302, not 301, so browsers don't cache the redirect and every click reaches the server (needed for analytics and expiry).

#### `link/ApiExceptionHandler.java`
`@RestControllerAdvice` means it applies to every controller. Each `@ExceptionHandler` turns an exception into a **`ProblemDetail`**, the standard JSON error format (RFC 9457, content type `application/problem+json`):

| Exception | Status | Body extras |
|---|---|---|
| `MethodArgumentNotValidException` (from `@Valid`) | 400 | `errors: {field: message}` (all fields at once) |
| `HttpMessageNotReadableException` (bad JSON or date) | 400 | `detail: "Malformed JSON request"` |
| `AliasTakenException` | 409 | message |
| `LinkNotFoundException` | 404 | message |
| `LinkExpiredException` | 410 | message |
| `CodeGenerationException` | 503 | message |

---

### Tests

| Test class | Type | What it proves | Notes |
|---|---|---|---|
| `LinkpulseApplicationTests` | `@SpringBootTest` | The whole app context starts (config, DB, Flyway, validate). | Needs Postgres running. |
| `LinkRepositoryTest` | `@DataJpaTest` + `replace = NONE` | Save and find by code, unknown code returns empty, duplicate gives `DataIntegrityViolationException`, the expiry boundary. | Each test is **rolled back**. Uses `saveAndFlush` to force the INSERT. `showSql = false`. |
| `RandomBase62GeneratorTest` | Plain JUnit | 7 Base62 characters, and 100,000 codes are all unique. | No Spring, so it runs in milliseconds. |
| `LinkServiceTest` | `@SpringBootTest` + `deleteAll()` | **The retry works** (same, same, new), it gives up after 3 attempts, a taken alias gives an exception. | `new LinkService(repo, FakeCodeGenerator)`. No test transaction, so the service's real transactions are exercised. |
| `LinkApiTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` | 201, 400 (URL and alias), 409, 302 + Location, 404, 410. | MockMvc runs the full MVC stack without a real port. |
| `ConcurrentAliasTest` | `@SpringBootTest(RANDOM_PORT)` | **10 parallel requests give exactly one 201 and nine 409s**, with exactly 1 row. | A real server, `HttpClient`, and a `CountDownLatch` so all threads start together. |

---

## 4. Workflows (what happens at runtime)

### 4.1 Startup: `mvnw spring-boot:run`
```
1. Maven compiles src/main/java → target/classes, then launches LinkpulseApplication.main
2. SpringApplication.run:
   a. reads application.yml
   b. auto-configuration:
      - DataSource: HikariCP pool → connects to localhost:5433   (fails fast on wrong password or a stopped DB)
      - Flyway: compares db/migration with flyway_schema_history → runs new migrations, checks checksums
      - Hibernate: builds the EntityManagerFactory, `validate` compares Link ↔ table links
      - Spring Data: generates the LinkRepository implementation
   c. component scan: creates beans RandomBase62Generator, LinkService (gets repo + generator),
      LinkController (gets service + base-url), RedirectController, ApiExceptionHandler
   d. Tomcat starts on port 8080
3. Log: "Started LinkpulseApplication in X seconds"
```
If anything in step 2 fails, the app **refuses to start** (fail fast).

### 4.2 Create with a random code: `POST /api/links {"url":"https://www.wikipedia.org"}`
```
Tomcat thread receives the request
 → DispatcherServlet finds LinkController.create (POST /api/links)
 → Jackson: JSON → CreateLinkRequest(url, alias=null, expiresAt=null)
 → @Valid: url passes NotBlank, Size and Pattern; alias=null passes; expiresAt=null passes
 → LinkService.create(url, null, null)         (no transaction yet)
    → createWithRandomCode
       attempt 1: code = RandomBase62Generator.generate()   e.g. "OQxEqXI"
                  repository.saveAndFlush(new Link(...))
                    → BEGIN
                    → INSERT INTO links (code, created_at, expires_at, long_url) VALUES (...)
                    → Postgres: uk_links_code OK, id generated
                    → COMMIT
                  ← Link with id set
 ← LinkController builds shortUrl = base-url + "/" + code
 ← 201 Created, Location: http://localhost:8080/OQxEqXI
   body {"code":"OQxEqXI","shortUrl":"http://localhost:8080/OQxEqXI"}
```
**On a collision (rare):** attempt 1's INSERT fails with 23505 on `uk_links_code`, giving **ROLLBACK of only that attempt**. `isCodeCollision` returns true, a WARN is logged, and attempt 2 starts a **fresh** transaction with a new code.

### 4.3 Create with an alias: `{"url":"...","alias":"my-repo"}`
```
… same as above until LinkService.create(url, "my-repo", null)
 → createWithAlias
    → saveAndFlush(new Link("my-repo", ...)) → BEGIN → INSERT → COMMIT
 ← 201 {"code":"my-repo", ...}

Second time with the same alias:
    → BEGIN → INSERT → ❌ 23505 uk_links_code → ROLLBACK
    → DataIntegrityViolationException → isCodeCollision = true → throw AliasTakenException
 → ApiExceptionHandler.aliasTaken → 409 {"title":"Conflict","detail":"Alias already taken: my-repo"}
```
No retry with a different value, because the user asked for exactly this alias.

### 4.4 The race: 10 parallel requests for the same alias
```
10 Tomcat threads, 10 separate transactions, all at the same moment:
  T1: INSERT 'race-alias'  → gets it first → COMMIT          → 201
  T2..T10: INSERT 'race-alias' → Postgres makes them WAIT for T1
           → T1 commits → each one fails with 23505 → ROLLBACK → AliasTakenException → 409
Result: one 201, nine 409s, exactly 1 row.
```
There's no "check if it exists" step anywhere, so there's no race window. The **unique constraint is the single source of truth**, and it works across transactions.

### 4.5 Redirect: `GET /my-repo`
```
 → DispatcherServlet finds RedirectController.redirect (GET /{code}), code = "my-repo"
 → LinkService.resolve("my-repo")
    → repository.findByCode → read-only transaction →
      SELECT id, code, created_at, expires_at, long_url FROM links WHERE code = ?
    → found; isExpiredAt(now)? expires_at is NULL → false
 ← 302 Found, Location: https://github.com/PrakarshSrivastav/linkpulse
Browser follows Location automatically → GitHub
```
**Unknown code:** `findByCode` returns empty, so `LinkNotFoundException` → **404**.
**Expired:** the row exists but `now ≥ expires_at`, so `LinkExpiredException` → **410**. The row stays in the DB; expiry is decided **at read time**.

### 4.6 A validation failure: `{"url":"ftp://x","alias":"no"}`
```
 → Jackson builds CreateLinkRequest
 → @Valid fails on url (Pattern) AND alias (Pattern)
 → Spring throws MethodArgumentNotValidException BEFORE the controller method body runs
   (the service and the DB are never touched)
 → ApiExceptionHandler.invalidRequest → 400
   {"detail":"Validation failed","errors":{"url":"must be an http or https URL",
                                         "alias":"must be 4-32 characters: ..."}}
```
**Broken JSON** (`{oops`): Jackson can't parse it, so `HttpMessageNotReadableException` → 400 "Malformed JSON request".

### 4.7 The DB goes down while the app runs
```
Requests needing the DB → Hikari can't get a connection → exception → 500 for that request
/actuator/health → db DOWN, overall DOWN, HTTP 503
The process keeps running; when Postgres returns, Hikari reconnects → everything works again
```

---

## 5. Daily commands

| Goal | Command |
|---|---|
| Start the DB | Open Docker Desktop, then `docker compose up -d` |
| Run the app | `.\mvnw.cmd spring-boot:run` |
| Run all tests | `.\mvnw.cmd test` |
| Health check | `curl.exe localhost:8080/actuator/health` |
| Create a link | `Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/links -ContentType "application/json" -Body (@{ url = "https://example.com" } \| ConvertTo-Json)` |
| Follow a link | `curl.exe -i localhost:8080/<code>` |
| Look at the DB | `docker exec -it linkpulse-postgres psql -U linkpulse -d linkpulse` |
| Which process holds a port | `Get-NetTCPConnection -LocalPort 8080 -State Listen` |

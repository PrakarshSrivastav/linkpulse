# Mission 1: questions and answers

Study sheet for everything covered so far. Try to answer each question out loud **before** reading the answer.

---

## Part A: The six mission questions

### A1. Why does insert-and-catch beat check-then-insert? What race happens?
With check-then-insert, two requests for the same alias can arrive together:

```
Request A: SELECT ... WHERE code='sale'  → not found
Request B: SELECT ... WHERE code='sale'  → not found   (A hasn't inserted yet)
Request A: INSERT 'sale'                 → OK
Request B: INSERT 'sale'                 → OK?? → two rows, or a crash
```
The gap between the check and the insert is a **race window**. Each transaction also can't see the other's uncommitted rows (READ COMMITTED), so both checks honestly say "free".

With insert-and-catch, the **unique constraint** checks and writes in **one atomic step**, across all transactions. If two inserts race, Postgres makes the second one wait for the first to commit and then rejects it with 23505. You catch that and return 409. You also do one round trip instead of two.

### A2. The Postgres transaction trap: where must the transaction boundary sit?
After **any** error inside a transaction, Postgres marks the whole transaction as aborted, and every later statement fails with `current transaction is aborted, commands ignored until end of transaction block`, even `SELECT 1` (Experiment B).

So if the retry loop runs **inside** one transaction, attempt 2 fails even with a unique code (Experiment E, second version).

**The boundary must be inside the loop, around each single attempt.** In this project, `LinkService.create()` has **no** `@Transactional`. Each `repository.saveAndFlush()` is itself transactional, so each attempt gets its own BEGIN → INSERT → COMMIT/ROLLBACK.

### A3. Why `SecureRandom` and not `Random`?
`java.util.Random` is a simple formula (a linear congruential generator). From a few observed outputs, an attacker can recover its internal state and **compute every future and past code**. They could then enumerate other people's links (private documents, invite links, password-reset pages). `SecureRandom` uses the operating system's cryptographic randomness, which can't be predicted from previous outputs.

### A4. Base62 maths: why are 7 characters enough, and how likely is a collision at 10 million links?
- Possible codes: 62⁷ ≈ **3.52 × 10¹²**.
- With 10⁷ links stored, the chance that **one** new random code is already taken is 10⁷ / 3.52×10¹² ≈ **1 in 352,000**.
- The chance that 3 attempts in a row all collide is about (1/352,000)³ ≈ **1 in 4 × 10¹⁶**.
- But across *all pairs* among 10 million codes, at least one collision is almost certain (the birthday paradox). That's why collision handling is required, even though any individual collision is rare.

### A5. 302 vs 301: why would 301 break analytics?
- **301 Moved Permanently**: browsers (and proxies/CDNs) **cache** it. The next time the user clicks the short link, the browser goes **straight to the long URL without contacting your server**. You never see those clicks, can't count them, and can't change or expire the link for that user.
- **302 Found**: a temporary redirect, so it isn't cached by default. Every click passes through your server, which lets you count it, check expiry, or change the target.

### A6. Why Flyway migrations rather than `ddl-auto=update`?
- Migrations are **versioned, reviewed, repeatable SQL** in git. Every environment (laptop, CI, production) gets exactly the same schema in the same order.
- Flyway records applied versions and **checksums** in `flyway_schema_history`. If an applied file changes, it refuses to start (Experiment C).
- `ddl-auto=update` lets Hibernate change tables **silently**. It never drops or renames anything safely, leaves no history, and a typo in an entity can create a stray column (Experiment D shows `validate` catching exactly that instead).
- In this project, Hibernate only **validates** (`ddl-auto: validate`), and Flyway owns the schema.

---

## Part B: Step 0 (setup)

### B1. Why does the Postgres driver have `runtime` scope, while JPA has compile scope?
The code is written against JPA annotations and interfaces (`@Entity`, `JpaRepository`), which must exist at compile time. The code **never imports** Postgres driver classes; JDBC finds the driver at runtime via `META-INF/services/java.sql.Driver` and the `jdbc:postgresql:` URL. `runtime` scope even **prevents** accidentally depending on Postgres-specific classes, which keeps the code portable.

### B2. What happens without `flyway-database-postgresql`?
Startup fails with `Unsupported Database: PostgreSQL`. Since Flyway 10, `flyway-core` contains only the migration engine; support for each database is a separate module.

### B3. Why did `mvnw.cmd` work in the terminal while IntelliJ showed everything red?
Same pom, but two different Maven setups. The terminal ran plain Maven 3.10.0, which works. IntelliJ **embeds** Maven and swaps in its own internal components, and IntelliJ 2026.1.2's components weren't compatible with Maven 3.10.0 ("Unable to create injector … ArtifactHandlerManager"). The fix was pinning the wrapper to Maven 3.9.11. Lesson: the terminal/CI build is the source of truth, and the IDE is a separate consumer of the pom.

### B4. When the DB goes down, why does the app keep running instead of crashing?
Connections come from a pool (HikariCP). Only the **requests** that need the DB fail. The process, Tomcat, and controllers don't depend on the DB to stay alive, and Hikari reconnects once the DB is back, with no restart needed. Crashing would turn a 10-second database blip into restart loops for every instance. At **startup**, by contrast, the app fails fast, because Flyway must migrate first.

### B5. Liveness vs readiness?
- **Liveness:** "is this process broken?" If it fails, the platform restarts the app. It should stay UP when the DB is down.
- **Readiness:** "can it serve traffic now?" If it fails, the platform stops routing traffic to the app but doesn't restart it.
- `/actuator/health` returns **503** when the overall status is DOWN, and load balancers read the status code.

---

## Part C: Step 1 (migration)

### C1. Why is `code` VARCHAR(32), not 7?
The column stores random codes (7 characters) **and** custom aliases (4–32 characters). It must fit the largest valid value. VARCHAR only sets a maximum; short values don't waste space.

### C2. In Experiment B, why did `SELECT 1` fail?
Because the earlier INSERT failed, and Postgres had marked the whole transaction as aborted. A transaction is all-or-nothing, so once one statement fails it can't succeed as a whole, and Postgres refuses everything until ROLLBACK. (MySQL usually fails only the one statement, which is why this trap catches people moving to Postgres.)

### C3. Why name the constraint (`uk_links_code`)?
1. The Java code can tell **which** rule failed (retry or 409 only for `uk_links_code`, and re-throw anything else).
2. The error messages are readable.
3. Future migrations can refer to it (`ALTER TABLE … DROP CONSTRAINT uk_links_code`). Auto-generated names can differ between databases.

### C4. Why `TIMESTAMPTZ` and not `TIMESTAMP`?
`TIMESTAMPTZ` stores an absolute instant (internally UTC). Plain `TIMESTAMP` has no timezone, so "10:00" is ambiguous across servers and users. Map it to Java `Instant`, not `LocalDateTime`.

### C5. Why no separate `CREATE INDEX` on `code`?
A UNIQUE constraint is implemented *as* a unique index. A second index would duplicate it, wasting disk space and slowing down every insert.

### C6. Why `GENERATED ALWAYS AS IDENTITY`?
It's the modern replacement for SERIAL. `ALWAYS` rejects manually supplied ids, so nobody can insert id 5 by hand and later collide with the sequence.

---

## Part D: Step 2 (entity and repository)

### D1. Why does JPA need a no-arg constructor, and why `protected`?
When loading a row, Hibernate first creates an **empty** object with the no-arg constructor (via reflection), then fills in the fields from the row. `protected` hides it from your own code (so you can't create an empty, invalid `Link` by accident) but still lets Hibernate and its generated proxy **subclasses** use it. `private` would break lazy proxies.

### D2. Why does `isExpiredAt` take `now` as a parameter?
For testability and determinism. A test can say "pretend it's exactly the expiry time" without waiting. Principle: business logic shouldn't read hidden inputs (the clock, randomness) directly. Pass them in. `CodeGenerator` follows the same idea.

### D3. Why `saveAndFlush`, and what goes wrong with `save` in insert-and-catch?
`save()` may only register the entity and send the INSERT later, at flush or commit. `saveAndFlush()` sends it **immediately**. With `save()`, the duplicate error would surface **after** the `try` block (at commit), so the `catch` never runs. There'd be no retry and no 409, and the user would get a 500.

### D4. Why test against real Postgres and not H2?
H2 is a different database that imitates Postgres. It doesn't abort the transaction after an error (Experiment B), it has different error codes and messages, and it handles `TIMESTAMPTZ` and identity columns differently. A broken retry design could pass on H2 and fail in production. A test is only useful if it fails when production would fail.

### D5. Why does `@DataJpaTest` leave the table empty?
It's meta-annotated with `@Transactional`, and in Spring's test framework, `@Transactional` **rolls back** after each test method by default. The INSERTs really happen, and the assertions run against real data, but then everything is undone. `@Commit` / `@Rollback(false)` changes that (and shows why the default exists: re-running then fails on duplicates).

### D6. Why can't another session see data during a test (before rollback)?
READ COMMITTED isolation: sessions only see **committed** data from other sessions. The test's transaction never commits, so psql sees 0 rows even while the test is paused after `saveAndFlush`.

### D7. Why package-by-feature (`link/`) instead of `entity/`, `repository/`, `controller/`?
Everything for one feature lives together, which makes it easier to navigate and to change one feature in isolation. Tests mirror the same package under `src/test/java`, so they can reach package-private code.

---

## Part E: Step 3 (generator and service)

### E1. Why is `create()` not `@Transactional`? Where does each transaction start and end?
Because the retry must not share a transaction with the failed attempt (A2). With no outer transaction, each `repository.saveAndFlush()` starts its own transaction (Spring Data repository methods are `@Transactional`) and commits or rolls back when it returns. One attempt equals one transaction.

### E2. Why wouldn't `@Transactional(REQUIRES_NEW)` on a private method in the same class help?
`@Transactional` works through a **Spring proxy** that wraps the bean. A call from one method to another **inside the same class** (`this.tryInsert()`) never passes through the proxy, so the annotation is silently ignored (the **self-invocation trap**). The same reason explains why the first Experiment E passed: a `LinkService` created with `new` has no proxy at all.

### E3. Why does `isCodeCollision` check the constraint name?
`DataIntegrityViolationException` covers every integrity problem (NOT NULL violations, other unique constraints, and so on). Only `uk_links_code` means "code taken, so retry or return 409". Anything else is a real bug and is re-thrown so it surfaces. In Experiment E, the "transaction aborted" error was *not* recognised as a collision and was re-thrown immediately, instead of being retried 3 times and misreported as "couldn't generate a code".

### E4. Why does `LinkServiceTest` use `@SpringBootTest` + `deleteAll()`, not `@DataJpaTest`?
`@DataJpaTest` wraps each test in **one transaction**. The service's `saveAndFlush` calls would join it, recreating the exact trap the service avoids, so the test would test the wrong setup. `@SpringBootTest` (without `@Transactional`) lets the service use its real transaction behaviour, and data really commits, so `deleteAll()` in `@BeforeEach` cleans up.

### E5. What could an attacker do if codes came from `java.util.Random`?
Predict codes and enumerate other users' links (see A3).

### E6. Why is `CodeGenerator` an interface?
`LinkService` depends on "something that produces a code", not on randomness. Production uses `RandomBase62Generator`, and tests pass a `FakeCodeGenerator` that returns exact codes, which is the only way to force a collision deterministically. This is dependency inversion (the D in SOLID).

### E7. Why `random.nextInt(62)` instead of `randomByte % 62`?
256 isn't divisible by 62, so `% 62` makes the first 8 characters slightly more likely (**modulo bias**). `nextInt(bound)` is uniform.

### E8. Why constructor injection with `final` fields?
The dependencies are explicit, the object is always fully built, the fields can't be swapped later, and tests can simply call `new LinkService(repo, fake)` with no Spring tricks.

---

## Part F: Debugging lessons from the setup

| Symptom | Real cause | Lesson |
|---|---|---|
| `password authentication failed` | Native Windows Postgres 18 owned port 5432 | Check who is listening on a port before blaming the config |
| `Port 8080 already in use` | The `PEMHTTPD-x64` service | `Get-NetTCPConnection -LocalPort 8080` |
| `no ServletWebServerFactory bean` | `@SpringBootApplication` was missing | "Missing bean" means checking that auto-configuration is on |
| Everything red in IntelliJ | IntelliJ vs Maven 3.10.0 incompatibility | When the IDE and terminal disagree, compare their tool versions |
| A quick sync changed nothing | An incremental import trusted a stale cache | Force a full reload |
| `Unable to connect` in all tests | Docker Desktop wasn't running | When many tests fail with the same error, read the **first** failure |
| `log.warn` "doesn't exist" | Imported `java.util.logging.Logger` | Read the package before accepting an import |
| `Inferred type 'S' not within bound` | `saveAllAndFlush` instead of `saveAndFlush` | Usually means the wrong argument type, so check the method name |
| TestNG/JUnit 4 in the pom | Accepted "Add … to classpath" | Never accept that quick fix here, and run `git diff` after quick fixes |

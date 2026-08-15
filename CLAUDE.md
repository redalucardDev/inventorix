# CLAUDE.md

Single reference file for Claude Code on this repository: context, architecture, conventions and workflows.

Complements in `.claude/`:

- `STANDARDS.md` — Java quality standards (checkstyle-derived, adapted to this codebase); the review checklist lives there;
- `skills/java-standards` — activate BEFORE writing or reviewing any Java code;
- `skills/tdd-workflow` — activate for any non-trivial behavior change (use-case logic, endpoint, bonus task);
- `skills/hexagonal-architecture` — activate BEFORE creating or restructuring a package with business rules (layer layout, ports, where a rule belongs, errors at the edge, test strategy).

## Role & posture

> Senior Java 21 / Quarkus engineer: hexagonal architecture, JUnit 5 + AssertJ, TDD.

- **Autonomous**: read code, tests and generated sources (`target/generated-sources`) before asking a question.
- **TDD**: drive every new feature test-first — write a failing test that specifies the behavior before writing any production logic to make it pass (see `skills/tdd-workflow`).
- **Cautious**: no destructive command without explicit approval; never modify an existing test without explicit user approval (creating new tests is unrestricted).
- **English**: identifiers, comments and communication in English.

## Repository purpose

This is an interview assignment with two independent parts:

- `java-assignment/` — a Quarkus application where the actual coding happens. Several methods intentionally throw `UnsupportedOperationException`; the tasks are to implement them per `java-assignment/CODE_ASSIGNMENT.md`, answer the three questions in `java-assignment/QUESTIONS.md`, and optionally the bonus task (Product↔Warehouse↔Store fulfilment associations).
- `case-study/` — discussion-only markdown scenarios (`CASE_STUDY.md`), no code. Domain overview is in `case-study/BRIEFING.md` (the link to BRIEFING.md inside CODE_ASSIGNMENT.md points there).

## Commands

All Maven commands run from `java-assignment/`:

```sh
./mvnw quarkus:dev          # dev mode with live reload, http://localhost:8080
./mvnw package              # build (also generates the OpenAPI server code)
./mvnw test                 # run unit/@QuarkusTest tests
./mvnw verify               # the above + the *IT tests against the packaged app
./mvnw test -Dtest=LocationGatewayTest              # single test class
./mvnw test -Dtest=LocationGatewayTest#methodName   # single test method
```

Requires JDK 21 (`maven.compiler.release=21`; the README's "JDK 17+" predates this). Dev and test modes start PostgreSQL automatically via Quarkus Dev Services (needs Docker). Prod mode expects PostgreSQL at `localhost:15432` (see README for the `docker run` command).

`*IT` classes (`@QuarkusIntegrationTest`) run via failsafe, bound in the main build — `./mvnw verify` packages the app and runs them black-box (JVM mode; the `native` profile only swaps in the native runner). Behavior an `*IT` already covers must not be duplicated by a `@QuarkusTest`.

**Coverage**: `target/jacoco-report/index.html`, written at the end of the surefire run. Two agents feed one data file (`target/jacoco-quarkus.exec`): the `quarkus-jacoco` extension covers `@QuarkusTest` classes, and `jacoco-maven-plugin` covers everything outside the Quarkus classloader (`exclClassLoaders=*QuarkusClassLoader` keeps them from fighting). Report settings are `%test.quarkus.jacoco.*` in `application.properties` — never plain `quarkus.jacoco.*`, which would warn on unknown keys in prod. The `*IT` process is not instrumented, so it contributes nothing to the report.

## Architecture

Base package: `com.fulfilment.application.monolith`. The codebase deliberately mixes implementation styles (this asymmetry is the subject of QUESTIONS.md — don't "fix" it unless asked):

- **`warehouses/`** — hexagonal (`domain/{models,ports,usecases,exceptions}` + `adapters/{database,restapi}`), REST layer implementing a **generated** interface. Rules in `docs/task-3-warehouse.md`.
- **`fulfilment/`** (bonus) — hexagonal, same shape; the three association limits live in `CreateFulfilmentUseCase`. See `docs/bonus-fulfilment.md`.
- **`products/` and `stores/`** — plain JAX-RS resources coded by hand. `Store` uses Panache active-record (static methods on the entity); `Product` uses the repository pattern. `StoreResource` propagates to `LegacyStoreManagerGateway` only after commit (`docs/task-2-store-legacy-sync.md`).
- **`location/`** — `LocationGateway` resolves locations from a hard-coded static list; locations are *not* in the database.

**Before creating or restructuring a hexagonal package, activate `skills/hexagonal-architecture`** — layer layout, port taxonomy, where a rule belongs, errors at the edge, test strategy. Dependency rule: adapters depend on domain ports; the domain knows nothing about JAX-RS, JPA or Quarkus.

### OpenAPI code generation (one spec per build)

`src/main/resources/openapi/warehouse-openapi.yaml` is the source of truth for the Warehouse REST API. The `quarkus-openapi-generator-server` extension generates the `com.warehouse.api` package (interface `WarehouseResource`, bean `com.warehouse.api.beans.Warehouse`) into `target/generated-sources` at build time — run a build before expecting those classes to resolve. There are therefore **two `Warehouse` classes**: the generated REST bean and the domain model; `WarehouseResourceImpl` maps between them. In IntelliJ, mark `target/.../jaxrs` as generated sources if compilation fails.

**Only one spec can be generated.** The extension's config is global (`quarkus.openapi.generator.spec` / `.base-package`, no per-spec keys in 2.4.7 or 2.8.0) and a second `quarkus:generate-code` execution with its own `<properties>` is ignored. `fulfilment-openapi.yaml` therefore publishes the bonus API as **documentation only**, and its contract is hand-written: interface `FulfilmentResource` + `FulfilmentResourceImpl` in `fulfilment/adapters/restapi` (same split as the generated warehouse one, but returning `Response` directly, so no status filter is needed). Don't retry the generator route — see `docs/bonus-fulfilment.md` §1.

### Persistence details

- Schema is `drop-and-create` on every start; seed data comes from `src/main/resources/import.sql` (3 stores, 3 products, 3 warehouses `MWH.001`/`MWH.012`/`MWH.023`). **`import.sql` is frozen — never modify it**; avoid schema changes (new non-nullable columns/entities) that would need new seed rows, since the seeds can't be updated and startup would fail.
- `Warehouse.businessUnitCode` is the business identifier (DB `id` is internal). "Replace" means: archive the current warehouse (set `archivedAt`) and create a new one reusing the same business unit code.

## Java rules

Arbiter: `.claude/STANDARDS.md` + the `java-standards` skill (checkstyle is not wired into the pom). Activate the skill before writing Java. Two things that bite here:

- **`var`** only when the type is visible on the same line — never where the two `Warehouse` classes could be confused.
- Rules apply to code you write or modify — **never restyle untouched assignment scaffolding**.

## Non-negotiable

- **`import.sql` is frozen — never modify it.** It intentionally violates the location rules (MWH.001 capacity 100 > ZWOLLE-001 max 40; TILBURG-001 already full) — never "fix" the seeds; validations apply to new operations only. Tests needing headroom use `ZWOLLE-002`, `EINDHOVEN-001`, `HELMOND-001`, `VETSBY-001`, `AMSTERDAM-001` (fulfilment tests already occupy `AMSTERDAM-00x`). Avoid schema changes needing new seed rows.
- **Never modify an existing test** without explicit approval; creating tests is unrestricted.
- **`@QuarkusTest` shares one database per run** (drop-and-create per start, not per test) — every test owns its business unit codes, product and store names.
- **Store legacy sync**: CDI events observed at `TransactionPhase.AFTER_SUCCESS` — never call `LegacyStoreManagerGateway` from a `@Transactional` method.
- **Never return null**: lookups return `Optional<T>`, handled by the caller. `Optional` for return types only.
- **Task tracking**: `TODO.md` at the repo root — keep checkboxes in sync as work lands.
- **Style**: English identifiers/comments, google-java-format 2-space, constructor injection, JUnit 5 + AssertJ, hand-written fakes (no Mockito, no Lombok), RestAssured for endpoints, behavior-describing test names with `// Given / When / Then`.

## To maintain

One fact, one home: orientation and the rules above live here; how-to lives in `.claude/skills/*`; rationale lives in `java-assignment/docs/*.md`. When a new package, endpoint, entity or convention lands, update the file that owns it — not all three.

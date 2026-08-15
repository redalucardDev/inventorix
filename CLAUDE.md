# CLAUDE.md

Single reference file for Claude Code on this repository: context, architecture, conventions and workflows.

Complements in `.claude/`:

- `STANDARDS.md` — Java quality standards (checkstyle-derived, adapted to this codebase); the review checklist lives there;
- `skills/java-standards` — activate BEFORE writing or reviewing any Java code;
- `skills/tdd-workflow` — activate for any non-trivial behavior change (use-case logic, endpoint, bonus task).

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
./mvnw test -Dtest=LocationGatewayTest              # single test class
./mvnw test -Dtest=LocationGatewayTest#methodName   # single test method
```

Requires JDK 21 (`maven.compiler.release=21`; the README's "JDK 17+" predates this). Dev and test modes start PostgreSQL automatically via Quarkus Dev Services (needs Docker). Prod mode expects PostgreSQL at `localhost:15432` (see README for the `docker run` command).

`*IT` classes (`@QuarkusIntegrationTest`) run via failsafe, which is only bound in the `native` profile — plain `./mvnw verify` does not execute them.

## Architecture

Base package: `com.fulfilment.application.monolith`. The codebase deliberately mixes implementation styles (this asymmetry is the subject of QUESTIONS.md — don't "fix" it unless asked):

- **`warehouses/`** — hexagonal architecture:
  - `domain/models` — plain POJOs with public fields (`Warehouse`, `Location`)
  - `domain/ports` — interfaces: `WarehouseStore` (persistence), `LocationResolver`, and one interface per operation (`CreateWarehouseOperation`, `ReplaceWarehouseOperation`, `ArchiveWarehouseOperation`)
  - `domain/usecases` — `@ApplicationScoped` implementations of the operation ports; business validations belong here
  - `adapters/database` — `DbWarehouse` JPA entity + `WarehouseRepository` (Panache repository implementing `WarehouseStore`)
  - `domain/exceptions` — `WarehouseValidationException` (400) / `WarehouseNotFoundException` (404, built through `forBusinessUnitCode` / `forId`)
  - `adapters/restapi` — `WarehouseResourceImpl`, implementing a **generated** interface, plus `WarehouseExceptionMappers` and `WarehouseCreatedStatusFilter` (the generated interface cannot declare the 201 of `POST /warehouse`, and `@ResponseStatus` on the impl is ignored)
- **`products/` and `stores/`** — plain JAX-RS resources coded by hand. `Store` uses Panache active-record (static methods on the entity); `Product` uses the repository pattern. `StoreResource` calls `LegacyStoreManagerGateway` — the assignment requires these calls to happen only after the DB transaction commits.
- **`location/`** — `LocationGateway` resolves locations from a hard-coded static list; locations are *not* in the database.

**Dependency rule (warehouses):** dependencies point inward — adapters depend on domain ports; the domain layer knows nothing about JAX-RS, JPA or Quarkus.

### OpenAPI code generation (Warehouse API only)

`src/main/resources/openapi/warehouse-openapi.yaml` is the source of truth for the Warehouse REST API. The `quarkus-openapi-generator-server` extension generates the `com.warehouse.api` package (interface `WarehouseResource`, bean `com.warehouse.api.beans.Warehouse`) into `target/generated-sources` at build time — run a build before expecting those classes to resolve. There are therefore **two `Warehouse` classes**: the generated REST bean and the domain model; `WarehouseResourceImpl` maps between them. In IntelliJ, mark `target/.../jaxrs` as generated sources if compilation fails.

### Persistence details

- Schema is `drop-and-create` on every start; seed data comes from `src/main/resources/import.sql` (3 stores, 3 products, 3 warehouses `MWH.001`/`MWH.012`/`MWH.023`). **`import.sql` is frozen — never modify it**; avoid schema changes (new non-nullable columns/entities) that would need new seed rows, since the seeds can't be updated and startup would fail.
- `Warehouse.businessUnitCode` is the business identifier (DB `id` is internal). "Replace" means: archive the current warehouse (set `archivedAt`) and create a new one reusing the same business unit code.

### Business rules to enforce (warehouse use cases)

- Business unit code must not already exist (create).
- Location must resolve via `LocationResolver`.
- Per-location limits from `Location`: `maxNumberOfWarehouses` and `maxCapacity` (sum of warehouse capacities); warehouse capacity must also cover its stock.
- Replace additionally requires: new capacity accommodates the old warehouse's stock, and new stock matches the old stock.

## Java rules (mandatory)

- **Leverage Java 21 features** where they make code clearer: records for immutable data carriers (events, results, fixtures — *not* JPA entities or generated beans), switch expressions and pattern matching (including record patterns), sealed interfaces for closed domain alternatives, `Stream.toList()`. Do not use a feature merely because it exists.
- **Checkstyle**: write code as if checkstyle were enforced — the rules are mirrored in `.claude/STANDARDS.md`. Checkstyle is *not* wired into `java-assignment/pom.xml`, so the arbiter is `.claude/STANDARDS.md` plus the `java-standards` skill. Key rules: no star imports, no magic numbers or duplicated string literals, `.equals()` from the non-null side, max 3 returns per method (2 for void), least visibility (never `public` unless required by the API or a framework), newline at end of file.
- **`var`** only when the type is visible on the same line (`var w = new Warehouse();`, `for (var e : map.entrySet())`) — explicit type everywhere else, and never where the two `Warehouse` classes could be confused.
- **try-with-resources** for every I/O resource (streams, readers/writers, JDBC, sockets) — never a hand-written `finally` close.
- Rules apply to code you write or modify — never restyle untouched assignment scaffolding.

## Project decisions & conventions

Agreed during implementation planning — follow these unless explicitly changed:

- **Errors (warehouse flows)**: use cases throw `WarehouseValidationException` (→ 400) or `WarehouseNotFoundException` (→ 404) from `warehouses/domain/exceptions`; `@Provider` mappers live in `warehouses/adapters/restapi` and keep the JSON shape of the global `StoreResource.ErrorMapper` (`exceptionType`/`code`/`error`). No JAX-RS types inside the domain layer.
- **Transactions**: `@Transactional` sits on use-case methods (replace = archive + create atomically). REST adapter methods stay annotation-free.
- **Archived warehouses**: `WarehouseStore.getAll`, `findByBusinessUnitCode` and `findById` return only active rows (`archivedAt is null`) — an archived unit is absent from the listing, 404 on lookup, and archiving it twice is a 404. Shared rules live in `domain/usecases/WarehouseValidations`; see `docs/task-3-warehouse.md`.
- **Seed data**: `import.sql` is **frozen — never modify it**. It intentionally violates the location rules (MWH.001 capacity 100 > ZWOLLE-001 max 40; TILBURG-001 already full); these violations are deliberate — never "fix" the seeds. Validations apply to new operations only. Tests needing headroom use `ZWOLLE-002`, `EINDHOVEN-001`, `HELMOND-001`, `VETSBY-001`, or `AMSTERDAM-001`.
- **Store legacy sync**: CDI events (`StoreCreatedEvent`/`StoreUpdatedEvent`) observed with `@Observes(during = TransactionPhase.AFTER_SUCCESS)` — never call `LegacyStoreManagerGateway` directly from a `@Transactional` method.
- **Tests**: JUnit 5 + AssertJ; hand-written fakes for ports (no Mockito, no Lombok anywhere); RestAssured for endpoint tests; behavior-describing names (`rejectsCreationWhenBusinessUnitCodeAlreadyExists`) with `// Given / When / Then` comments. `@QuarkusTest` shares one DB per run (drop-and-create per start, not per test) — each test owns its business unit codes.
- **Style**: English identifiers/comments, google-java-format 2-space style like the existing code, constructor injection.
- **Never return null**: lookup/query methods return `Optional<T>` (e.g. `LocationResolver.resolveByIdentifier`, `WarehouseStore.findByBusinessUnitCode`/`findById`); absence is handled by the caller (`orElseThrow` with a domain exception, or a 404 in the REST adapter). `Optional` is for return types only — never for fields or parameters.
- **Task tracking**: `TODO.md` at the repo root — keep checkboxes in sync as work lands.

## To maintain

Update this file and `.claude/STANDARDS.md` whenever a new package, endpoint, entity or convention lands.

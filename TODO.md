# TODO — Interview Assignment

Plan re-established on 2026-08-12 after a full audit of the working tree.

> **State of the repo:** `git diff HEAD --ignore-all-space` on `java-assignment/src/` is **empty** —
> the only difference versus the commit is CRLF line endings. All 9 `UnsupportedOperationException`
> stubs are still in place and no new source file exists on disk.
> Compiled `.class` files from a lost session survive in `java-assignment/target/`
> (`fulfilment/`, `StoreCreatedEvent`, `WarehouseValidations`, …) — bytecode only, sources gone.
> Run `mvn clean` first: until then surefire picks up those stale test classes.

## Step 0 — Toolchain (blocks everything)

- [x] JDK 21 installed — `sdk install java 21.0.12-amzn` (shell default was 17, the pom needs 21)
- [x] Maven mirror bypass — `~/.m2/settings.xml` forces `nexus.ortec.fr`, unreachable from WSL;
      scratch Central-only settings written to the session scratchpad
- [x] Verified `mvn test` compiles and generates the OpenAPI sources end to end
- [x] Docker access — `usermod -aG docker` done; until the next WSL restart, builds must run
      through `sg docker -c ...` (the runner script below does this)
- [x] Baseline `mvn clean test` green: 2 tests, Dev Services Postgres starts correctly
- [x] pom: AssertJ 3.26.3 added (test scope)

Runner script (JDK 21 + Central settings + docker group) lives in the session scratchpad as
`mvnj.sh`; use `./mvnj.sh clean test`.

`./mvnw` is unusable from WSL: the wrapper has CRLF endings, so its `#!/bin/sh` shebang is invalid.
Use the system `mvn` (3.9.9). Do not convert the file.

## Task 1 — Location (must have) — DONE

- [x] `LocationGateway`: `@ApplicationScoped`, `resolveByIdentifier` implemented
- [x] `LocationResolver` port returns `Optional<Location>` (never-null convention)
- [x] `LocationGatewayTest`: real assertions — found case + not-found case
- [x] `mvn clean test` green (3 tests)

## Task 2 — Store legacy sync after commit (must have) — DONE

- [x] `StoreCreatedEvent` / `StoreUpdatedEvent` records
- [x] `LegacyStoreEventsObserver` with `@Observes(during = TransactionPhase.AFTER_SUCCESS)`
- [x] `StoreResource`: fires events from the managed entity (not the request payload) in
      `create` / `update` / `patch`; direct gateway injection removed
- [x] `StoreEndpointTest` + `RecordingLegacyStoreManagerGateway` (`@Mock`):
      notified after commit, carries the persisted id, silent on rollback
- [x] `mvn clean test` green (6 tests)

The rollback case is driven by the `unique` constraint on `Store.name`: posting an existing name
fails at commit, and before this change the legacy system was still notified (id=4, never stored).

Left untouched on purpose: `patch` guards on `entity.name != null` / `entity.quantityProductsInStock
!= 0` rather than on the payload, which looks wrong but is outside this task's scope.

## Task 3 — Warehouse (must have)

- [ ] Domain exceptions `WarehouseValidationException` (400) / `WarehouseNotFoundException` (404)
- [ ] `WarehouseExceptionMappers` (`@Provider`), keeping the `exceptionType`/`code`/`error` JSON shape
- [ ] `Warehouse.id`; `WarehouseStore.findById` / `findByBusinessUnitCode` returning `Optional`;
      `DbWarehouse.from(...)`
- [ ] `WarehouseRepository`: `create` / `update` / `remove` / lookups, filtering `archivedAt is null`
- [ ] `CreateWarehouseUseCase` + shared `WarehouseValidations`
      (unique business unit code, location resolves, max warehouses per location,
      capacity sum ≤ location max, stock ≤ capacity)
- [ ] `ReplaceWarehouseUseCase`: 404 on unknown code, stock must match, new capacity covers the old
      stock, per-location limits computed excluding the unit being replaced, atomic archive+create
- [ ] `ArchiveWarehouseUseCase`: set `archivedAt`, reject an already-archived unit
- [ ] `WarehouseResourceImpl`: constructor-injected operation ports, all 4 endpoints, `id` in responses
- [ ] Use-case unit tests with hand-written fakes (`InMemoryWarehouseStore`, `StubLocationResolver`)
- [ ] `WarehouseEndpointTest` (`@QuarkusTest` + RestAssured)
- [ ] `WarehouseEndpointIT`: uncomment and restore the archiving test

## Bonus — fulfilment associations (nice to have)

- [ ] `fulfilment/` package: `ProductFulfilment` entity + repository
- [ ] `FulfilmentService`: ≤2 warehouses per product+store, ≤3 warehouses per store,
      ≤5 product types per warehouse
- [ ] `FulfilmentResource`: POST (201/400), GET with `productId`/`storeId` filters, DELETE (204/404)
- [ ] `FulfilmentEndpointTest`: all three limits + unknown references + delete

`import.sql` is frozen — a new entity must be nullable and need no seed rows.

## Verification

- [ ] `mvn clean test` green
- [ ] Confirm the generated `WarehouseResource` returns 201 on `POST /warehouse`
      (the spec says 201; if the generator yields 200, add `@ResponseStatus(201)` on the impl)
- [ ] `mvn package` — generated sources compile

## Yours (Reda) — not delegated

- [ ] Answer the 3 questions in `java-assignment/QUESTIONS.md`
- [ ] Talking points for `case-study/CASE_STUDY.md` (discussion only, no code)

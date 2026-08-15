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

## Task 3 — Warehouse (must have) — DONE

- [x] Domain exceptions `WarehouseValidationException` (400) / `WarehouseNotFoundException` (404),
      the latter with `forBusinessUnitCode` / `forId` factories so the message lives in one place
- [x] `WarehouseExceptionMappers` (`@Provider`), keeping the `exceptionType`/`code`/`error` JSON shape
- [x] `Warehouse.id`; `WarehouseStore.findById` / `findByBusinessUnitCode` returning `Optional`;
      `DbWarehouse.from(...)`
- [x] `WarehouseRepository`: `create` / `update` / `remove` / lookups, filtering `archivedAt is null`
- [x] `CreateWarehouseUseCase` + shared `WarehouseValidations`
      (mandatory values, unique business unit code, location resolves, max warehouses per location,
      capacity sum ≤ location max, stock ≤ capacity)
- [x] `ReplaceWarehouseUseCase`: 404 on unknown code, stock must match, new capacity covers the old
      stock, per-location limits computed excluding the unit being replaced, atomic archive+create
- [x] `ArchiveWarehouseUseCase`: set `archivedAt`; an already-archived unit is no longer active, so
      a second archive returns 404
- [x] `WarehouseResourceImpl`: constructor-injected operation ports, all 4 endpoints, `id` in responses
- [x] `WarehouseCreatedStatusFilter`: restores the 201 the generated interface cannot declare
- [x] Use-case unit tests with hand-written fakes (`InMemoryWarehouseStore`, `StubLocationResolver`):
      8 create + 6 replace + 3 archive, every validation branch covered
- [x] `WarehouseEndpointTest` (`@QuarkusTest` + RestAssured), 10 tests including the archived unit
      disappearing from the listing and from the lookups
- [x] `WarehouseEndpointIT`: uncommented and restored

Design decisions written up in `java-assignment/docs/task-3-warehouse.md`.

Left open (deliberate, see the doc's *Limits* section):

- failsafe is still bound to the `native` profile only, so the two `*IT` tests do not run under
  `mvn verify`; the same behaviour is covered by `WarehouseEndpointTest`, which does run
- the two `WarehouseEndpointIT` tests share one application instance and JUnit does not guarantee
  their order — `testSimpleListWarehouses` fails if it runs after the archiving test

## Bonus — fulfilment associations (nice to have) — DONE

- [x] `fulfilment/` package, hexagonal like `warehouses`: domain (model, 6 ports, 3 use cases,
      2 exceptions) + adapters (JPA entity, repository, 3 lookup adapters, REST resource, mappers)
- [x] `CreateFulfilmentUseCase`: ≤2 warehouses per product+store, ≤3 warehouses per store,
      ≤5 product types per warehouse — all counting *distinct* participants
- [x] `FulfilmentResource`: POST (201/400), GET with `productId`/`storeId` filters, DELETE (204/404)
- [x] `FulfilmentEndpointTest`: 10 tests — three limits, duplicate, distinct-counting case,
      unknown references, archived warehouse refused, delete, cascade on product deletion
- [x] `CreateFulfilmentUseCaseTest` (11) + `RemoveFulfilmentUseCaseTest` (2) on hand-written fakes —
      every rule and rejection asserted without a container, in about a second

- [x] `fulfilment-openapi.yaml` publishing the bonus API contract — **documentation only**: the
      generator takes a single spec per build, so `FulfilmentResource` stays a hand-written adapter
      (evidence and dead ends in `docs/bonus-fulfilment.md` §1 — don't retry the generator route)

`import.sql` untouched: the new table starts empty and needs no seed row.
Design decisions in `java-assignment/docs/bonus-fulfilment.md`.

The first draft used the plain style of `products`/`stores`; it was restructured because a Panache
repository as a direct dependency left the three counting rules reachable only through HTTP. The
endpoint tests, written first and unchanged since, are what proves the restructuring changed nothing.

## Verification

- [x] `mvn clean test` green — 43 tests (6 before Task 3, 33 after it, 43 with the bonus)
- [x] `POST /warehouse` returns 201: the generator yields 200 and `@ResponseStatus(201)` on the impl
      is ignored (the JAX-RS method is declared on the generated interface), so the status is set by
      `WarehouseCreatedStatusFilter`
- [x] `mvn package` — generated sources compile

## Yours (Reda) — not delegated

- [ ] Answer the 3 questions in `java-assignment/QUESTIONS.md`
- [ ] Talking points for `case-study/CASE_STUDY.md` (discussion only, no code)

# TODO — Interview Assignment

Working plan agreed on 2026-08-11. One file for now; split per-topic only if it outgrows a page.

## Housekeeping (Step 0)
- [x] pom: drop compiler `source/target 11` override, build with `maven.compiler.release=21`
- [x] pom: add AssertJ 3.26.0 + `quarkus-jacoco` (test scope)
- [x] TODO.md (this file)
- [x] CLAUDE.md: "Project decisions & conventions" section (incl. never-return-null / `Optional` rule)
- [x] `.claude/settings.json`: minimal Maven allowlist

## Task 1 — Location (must have)
- [x] `LocationGateway`: `@ApplicationScoped`, `resolveByIdentifier` returns `Optional<Location>`
- [x] `LocationGatewayTest` restored (AssertJ) + not-found case

## Task 3 — Warehouse (must have)
- [x] Domain exceptions `WarehouseValidationException` (400) / `WarehouseNotFoundException` (404) + mappers (`WarehouseExceptionMappers`)
- [x] `Warehouse.id`, `WarehouseStore.findById`/`findByBusinessUnitCode` returning `Optional`, `DbWarehouse.from()`
- [x] `WarehouseRepository`: full implementation; `getAll`/lookups filter `archivedAt is null`
- [x] `CreateWarehouseUseCase` + shared `WarehouseValidations` (unique BU, location exists, max warehouses, capacity sum, stock ≤ capacity)
- [x] `ReplaceWarehouseUseCase`: 404 unknown BU, stock match, capacity checks, limits excluding the replaced unit, atomic archive+create
- [x] `ArchiveWarehouseUseCase`: sets `archivedAt`, rejects already archived
- [x] `WarehouseResourceImpl`: constructor-injected operation ports, 4 endpoints, `id` mapped in responses
- [x] Use-case unit tests with `InMemoryWarehouseStore` / `StubLocationResolver` fakes
- [x] `WarehouseEndpointTest` (@QuarkusTest + RestAssured)
- [x] `WarehouseEndpointIT` archiving test restored (+ deterministic `@Order`)

## Task 2 — Store legacy sync after commit (must have)
- [x] `StoreCreatedEvent` / `StoreUpdatedEvent` + `LegacyStoreEventsObserver` (`AFTER_SUCCESS`)
- [x] `StoreResource` fires events (managed entity, not request payload); gateway injection removed
- [x] `StoreEndpointTest` + `RecordingLegacyStoreManagerGateway` (@Mock): notified after commit, silent on rollback

## Bonus — fulfilment associations
- [x] `fulfilment/` package: `ProductFulfilment` entity + repository
- [x] `FulfilmentService` enforcing ≤2 per product+store, ≤3 per store, ≤5 products per warehouse
- [x] `FulfilmentResource`: POST (201/400), GET with `productId`/`storeId` filters, DELETE (204/404)
- [x] `import.sql`: 3 seeded associations (product 1 left unreferenced — ProductEndpointTest deletes it)
- [x] `FulfilmentEndpointTest` covering all three limits + unknown refs + delete

## Verification — pending (no JDK in this WSL shell; run from your environment)
- [ ] `./mvnw test` green (needs Docker for Dev Services; JDK 21)
- [ ] Check: does the generated `com.warehouse.api.WarehouseResource` return 201 for POST /warehouse? Tests assert the spec's 201; if the generator yields 200, add `@ResponseStatus(201)` on the impl or relax the test.
- [ ] `./mvnw package` — generated sources compile, JaCoCo report at `target/jacoco-report`

## Yours (Reda) — not delegated
- [ ] Answer the 3 questions in `java-assignment/QUESTIONS.md`
- [ ] Prepare talking points for `case-study/CASE_STUDY.md` (discussion only, no code)

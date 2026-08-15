---
name: hexagonal-architecture
description: >-
  Hexagonal + DDD conventions of this repo, derived from the `warehouses` package
  and mirrored in `fulfilment`. Activate BEFORE creating or restructuring a package
  with business rules: layer layout, port taxonomy, where a rule belongs (aggregate
  vs domain service), domain model vs JPA entity, errors at the edge, transactions.
---

# Skill — Hexagonal architecture (this repo's dialect)

Reference: `warehouses/`. Second application: `fulfilment/`. **Keep them identical in shape** —
a third organisational style is worse than an imperfect one repeated. `products/` and `stores/`
are deliberately *not* hexagonal; don't convert them unless asked.
Reasoning and history: `java-assignment/docs/task-3-warehouse.md`, `docs/bonus-fulfilment.md`.

## Layout

```
<feature>/
  domain/
    models/      POJOs or records — no JPA, no JAX-RS, no Quarkus
    ports/       interfaces only, driving + driven flat in one package
    usecases/    @ApplicationScoped implementations of the driving ports; rules live here
    exceptions/  <Feature>ValidationException (400), <Feature>NotFoundException (404)
  adapters/
    database/    `Db<Name>` JPA entity + Panache repository implementing the driven port
    restapi/     resource + request/response records + @Provider exception mappers
    <module>/    outbound adapter bridging to another feature
```

Adapters → domain, never the reverse. CDI/transaction annotations on use cases are the one
accepted framework import in `domain/`.

## Ports

- **Driving**: `<Verb><Feature>Operation`, one per operation, implemented by a use case.
- **Driven**: noun-named (`WarehouseStore`, `LocationResolver`, `ProductCatalog`).
- Flat in `domain/ports` — naming carries the direction; no `ports/in` + `ports/out`.
- **Own your ports**: never import another feature's port into your domain. Declare your own
  and bridge it with an adapter (`ActiveWarehouseLookup` → `ActiveWarehouseAdapter` → `WarehouseStore`).

## Where a rule belongs

| Rule | Home |
|---|---|
| Invariant of one entity, or relation between two instances | the model — **only if** it is a real aggregate root |
| Set-wide uniqueness | domain service — an aggregate cannot see its siblings |
| Anything needing a lookup (does X exist) | domain service |
| Invariant of another aggregate (per-location limits) | that aggregate if persisted, else domain service |

Domain service = `@ApplicationScoped` class in `domain/usecases`, injected with ports
(`WarehouseValidations`). An anemic public-field model cannot enforce invariants: either make it
a true aggregate (private fields, validating factory, mappers going through it) or leave the
rules in the service — half-way adds a hop without adding safety.

Order rules so each is separately reachable: the first violated one is the one reported.

## Model vs entity

- Two types always (`Warehouse`/`DbWarehouse`, `Fulfilment`/`DbProductFulfilment`); the entity owns the mapping.
- Immutable association → record with `of(...)` / `storedAs(id)`.
- Reference another aggregate by its **business identifier** when that identifier outlives the row.
- `@ManyToOne` stays **eager** if the mapper reads `related.id` (a lazy proxy reports null under field access).
- `@OnDelete(CASCADE)` on FKs whose parent has a working DELETE endpoint.

## Driven-port rules

- Lookups return `Optional`, never null — caller does `orElseThrow(domain exception)`.
- Filter logical deletion **inside the adapter** so no caller can forget it.
- Write the generated id back: `T create(T)` returning the stored copy (records), or mutate `id`.
- Panache already declares `findById`, `find`, `list`, `delete`, `persist`, `count` — a port method
  with the same erasure will not compile. Rename the port method, not the semantics.

## Edges

- Domain exceptions only; `@Provider` mappers in `adapters/restapi` translate them and keep the
  repo-wide `exceptionType`/`code`/`error` shape. Mappers are found by annotation scanning — they
  look unused and aren't. Not-found exceptions get factories (`forId`) so the message exists once.
- `@Transactional` on use-case methods only; REST methods stay annotation-free.
- REST adapter maps bean ↔ model and does nothing else.
- Annotations on a method implementing a **generated** interface are not read (`@ResponseStatus`);
  fix the status with a narrowly scoped `ContainerResponseFilter`.
- **Contract-first, once**: the REST contract belongs in `src/main/resources/openapi/<feature>-openapi.yaml`,
  but `quarkus-openapi-generator-server` generates a **single** spec per build (global config, no
  per-spec keys, a second `generate-code` execution is ignored). Either way the shape is the same —
  a `<Name>Resource` interface carrying `@Path`, the verbs and the media types, and a
  `<Name>ResourceImpl` holding only wiring and mapping. Generated: the interface comes from the YAML
  in its own package. Not generated: write the interface by hand **in `adapters/restapi`**, never in
  a package that mimics generated output — and take the chance to return `Response` with an explicit
  status, which removes the need for a `ContainerResponseFilter`.

## Tests (both levels)

- **Rules**: plain JUnit + AssertJ over hand-written fakes (`InMemory<X>Store` filtering like the
  real adapter and recording writes, `Stub<X>` for lookups). One test per rejection branch,
  each asserting nothing was written.
- **Wiring**: `@QuarkusTest` + RestAssured for statuses, JSON shape, cross-feature effects.
  Write them first and never touch them during a restructuring — they are its only proof.

## Checklist

1. Anything under `domain/` importing JPA, JAX-RS, Panache or another feature's port?
2. Driving ports per operation, driven ports noun-named, all flat?
3. `Optional` lookups, logical-deletion filter inside the adapter?
4. Rules in use case / domain service, adapters free of logic?
5. Domain exceptions + mappers keeping the shared JSON shape?
6. `@Transactional` on the use case only?
7. Fake-based tests per rule **and** endpoint tests for the wiring?
8. Same shape as `warehouses/`, no new folder vocabulary?

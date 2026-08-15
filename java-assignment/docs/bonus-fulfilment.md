# Bonus — Design decisions: warehouses as fulfilment units

Rationale behind the bonus commit. The task is to associate a `Warehouse`, as a fulfilment unit of a
`Product`, to a `Store`, under three limits: two warehouses per product and store, three warehouses
per store, five product types per warehouse.

## 1. The API

| Verb | Path | Answer |
|---|---|---|
| `POST` | `/fulfilment` | `201` with the association, `400` on any rule violation or unknown reference |
| `GET` | `/fulfilment?productId=&storeId=` | `200`, both filters optional and combinable |
| `DELETE` | `/fulfilment/{id}` | `204`, `404` if it does not exist |

Request and response are records (`FulfilmentRequest`, `FulfilmentResponse`), so no JPA entity is
serialised and the payload states exactly the three references it carries.

`src/main/resources/openapi/fulfilment-openapi.yaml` publishes that contract, but — unlike the
warehouse one — it is **documentation, not a build input**: `quarkus-openapi-generator-server`
takes a single spec per build. Its configuration is global (`quarkus.openapi.generator.spec` and
`.base-package`, with no per-spec keys in 2.4.7 nor in 2.8.0), and a second `quarkus:generate-code`
execution carrying its own `<properties>` is ignored — the codegen only reads the application
config, so whichever spec sits in `application.properties` is the one and only API generated.
Forcing this spec from the command line does produce a correct `com.fulfilment.api.FulfilmentResource`
and its beans, at the cost of no longer generating the warehouse package. Generating both would take
a second Maven module, out of proportion here.

The contract is therefore **hand-written**, and split the same way the warehouse one is generated:
`FulfilmentResource` is an interface carrying `@Path`, the verbs and the media types;
`FulfilmentResourceImpl` holds only the wiring and the mapping. It lives in `adapters/restapi`
rather than in a `com.fulfilment.api` package of its own — that namespace belongs to generated
output, and a hand-maintained copy of it would read as generated code committed by mistake. One
benefit over the generated side: since the interface is ours, `createFulfilment` returns `Response`
with an explicit `201`, so fulfilment needs no equivalent of `WarehouseCreatedStatusFilter`.

## 2. Which style the package follows

The repository deliberately mixes an hexagonal package (`warehouses`) with two plain ones
(`products`, `stores`). The bonus feature spans all three, so it had to pick one — and it follows
`warehouses`:

```
fulfilment/
  domain/
    models/Fulfilment                  immutable record: product, store, warehouse code
    ports/FulfilmentStore              persistence
    ports/ProductCatalog               does this product exist
    ports/StoreDirectory               does this store exist
    ports/ActiveWarehouseLookup        is this code an active warehouse
    ports/{Create,Remove,List}FulfilmentOperation
    usecases/CreateFulfilmentUseCase   the three limits live here
    usecases/{Remove,List}FulfilmentUseCase
    exceptions/Fulfilment{Validation,NotFound}Exception
  adapters/
    database/DbProductFulfilment       JPA entity, mapped to the domain record
    database/ProductFulfilmentRepository        implements FulfilmentStore
    database/{ProductCatalog,StoreDirectory,ActiveWarehouse}Adapter
    restapi/FulfilmentResource (contract) + FulfilmentResourceImpl,
            FulfilmentRequest/FulfilmentResponse, FulfilmentExceptionMappers
```

The first draft was written in the plain style — a `FulfilmentService` holding the rules over a
Panache repository, throwing `WebApplicationException`. It worked and it was shorter, but it left
the three counting rules reachable only through HTTP: with a Panache repository as a direct
dependency there is no seam to fake. Restructuring bought exactly that seam. The rules now run
against `InMemoryFulfilmentStore` in **milliseconds instead of a 23-second container round**, and
every branch of them is asserted individually (`§5`).

Three consequences worth naming:

- the domain layer knows no JPA, no JAX-RS: `CreateFulfilmentUseCase` speaks only `Fulfilment` and its
  four ports, and reports failures as `FulfilmentValidationException` / `FulfilmentNotFoundException`
  mapped at the edge into the same `exceptionType`/`code`/`error` shape as everywhere else;
- the fulfilment domain owns an `ActiveWarehouseLookup` port instead of importing the warehouse
  module's `WarehouseStore` directly — one module's domain should not depend on another module's
  port; `ActiveWarehouseAdapter` is the bridge, and it lives on the adapter side;
- `FulfilmentStore.findAssociation(Long)` is *not* called `findById`: `ProductFulfilmentRepository`
  also implements `PanacheRepository`, whose own `findById(Long)` returns the entity, and the two
  signatures would collide.

## 3. The warehouse is referenced by business unit code, not by a foreign key

`DbProductFulfilment` holds `product` and `store` as `@ManyToOne` relations, but the warehouse as a
plain `warehouseBusinessUnitCode` column. Three reasons:

- the business unit code *is* the warehouse identifier in this domain — the row id is internal;
- a replacement archives one row and inserts another under the same code, so an FK to the row would
  leave every association pointing at an archived unit, while the code follows the business unit
  through its replacements — which is what "this store is fulfilled by MWH.012" means;
- it keeps the `fulfilment` package from depending on the warehouse persistence adapter, leaving a
  port as the only contact surface.

Existence is still checked, through `ActiveWarehouseLookup` — implemented over
`WarehouseStore.findByBusinessUnitCode`, which only returns active warehouses, so an archived unit
cannot be turned into a fulfilment unit (`refusesAWarehouseThatHasBeenArchived`, and
`rejectsAWarehouseThatIsNotActive` on the use case).

Product and store keep real foreign keys, with `@OnDelete(CASCADE)`: deleting a product removes its
associations instead of failing on a constraint violation, which keeps `ProductResource.delete`
answering `204` as it always did (`deletingAProductTakesItsAssociationsWithIt`).

The uniqueness of `(product, store, warehouse)` is enforced by a unique constraint *and* rejected
explicitly with a `400`, so a client gets a message rather than a constraint violation.

## 4. Counting distinct warehouses, not rows

The three limits count *distinct* participants, which is the only subtle part of the feature:

| Rule | Counted | Skipped when |
|---|---|---|
| 2 warehouses per product **and** store | distinct codes for that pair | never — a repeat of the same code is a duplicate, already rejected |
| 3 warehouses per store | distinct codes across all its products | the code is already among them |
| 5 product types per warehouse | distinct product ids across all stores | the product is already stored there |

The "skipped when" column is the part a row count would get wrong: a store already served by its
three warehouses can still take a fourth association, as long as it reuses one of those three
(`reusingAWarehouseDoesNotCountTwiceAgainstTheStoreLimit`). Conversely the per-product rule needs no
such check, because the exact triple has already been refused as a duplicate — the comment in
`requireRoomForTheProductInTheStore` records why the asymmetry is intentional rather than an
oversight.

## 5. Testing

Two levels, the same split as the warehouse package: rules without a container, HTTP with one.

`CreateFulfilmentUseCaseTest` (13) and `RemoveFulfilmentUseCaseTest` (2) — plain JUnit over
hand-written fakes (`InMemoryFulfilmentStore`, `StubProductCatalog`, `StubStoreDirectory`,
`StubActiveWarehouseLookup`), covering each rule and each rejection separately: duplicate, the three
limits, **both** distinct-counting cases (a warehouse the store already relies on, a product the
warehouse already stores), unknown product / store / archived warehouse, an association missing its
product, its store or its warehouse code, removal and unknown id. The whole set runs in about a
second and leaves `fulfilment/domain` at 100% of lines and branches.

`FulfilmentEndpointTest` — 13 `@QuarkusTest` cases: the happy path with both `GET` filters, the
unfiltered listing and the two filters combined, the duplicate, the three limits, the
distinct-counting case, unknown product / store / warehouse, an empty payload, the archived
warehouse, deletion (`204` then `404`), and the cascade on product deletion. It was written
before the implementation and never changed since — which is what makes it the safety net proving
the restructuring of §2 altered nothing observable.

Isolation in a database shared by the whole run is handled by ownership: each test creates its own
products (`FUL-T*-P*`) and stores (`FUL-T*-S*`) through the REST API, so no test can shift another's
counts. The seeded warehouses are shared — each accumulates at most three product types across the
suite, far from the limit of five — and the tests that need a warehouse of their own create it at
`AMSTERDAM-001` / `AMSTERDAM-002`, the two locations with free slots that no warehouse test uses.
`import.sql` is untouched: the new table starts empty and needs no seed row.

## 6. Limits

**Same read-then-write race as the warehouse rules.** Two concurrent `POST`s can both count two
warehouses for a product and both insert, ending at three. The unique constraint stops exact
duplicates, not limit overruns; a serialisable transaction or a lock on the store row would.

**The rules are counted in memory.** Each check loads the matching associations and streams them
`distinct()`, rather than issuing `select count(distinct …)`. At the scale of a store's fulfilment
units this is clearer and cheaper to read; it is the first thing to change if the table grows.

**The filtering of `GET /fulfilment` is only covered through HTTP.** `ListFulfilmentsUseCase`
delegates to the port, and the query building lives in `ProductFulfilmentRepository`, where a fake
proves nothing — the two filter cases are asserted in the endpoint test instead.

**`@ManyToOne` stays eager.** Mapping an entity to `Fulfilment` reads `product.id` and `store.id`,
which a lazy proxy would report as `null` under field access. Eager is correct here and cheap at
this scale; making it lazy would mean storing the two ids as columns of their own.

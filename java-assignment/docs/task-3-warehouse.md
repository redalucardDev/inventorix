# Task 3 — Design decisions: warehouse creation, replacement and archiving

Rationale behind the Task 3 commit. The assignment asks for the four warehouse endpoints plus the
business rules attached to them: unique business unit code, existing location, per-location limits
on the number of warehouses and on total capacity, stock that fits the capacity, and two extra rules
when a warehouse is replaced.

## 1. Where each rule lives

The package is the only one in the codebase with a hexagonal layout, so the split was already
decided: the domain owns the rules, the adapters own the plumbing.

| Concern | Lives in |
|---|---|
| Business rules | `domain/usecases/WarehouseValidations` |
| Flow + transaction boundary | `domain/usecases/{Create,Replace,Archive}WarehouseUseCase` |
| Rule violations | `domain/exceptions/Warehouse{Validation,NotFound}Exception` |
| SQL, ids, archived filter | `adapters/database/WarehouseRepository` |
| HTTP status, JSON shape, DTO mapping | `adapters/restapi/*` |

`WarehouseValidations` is a package-private `@ApplicationScoped` bean rather than a static utility:
it needs the `WarehouseStore` and the `LocationResolver`, and injecting it keeps both use cases free
of any wiring. Creation and replacement share the per-location arithmetic, which is the part that is
easy to get subtly wrong twice.

**Why the rules are a domain service and not methods on `Warehouse`.** The obvious DDD objection is
that a rule about a warehouse belongs on the warehouse itself. It holds for two of the seven rules
and not for the others:

| Rule | Natural owner |
|---|---|
| mandatory values, not negative | the `Warehouse` itself — a self-invariant |
| stock ≤ capacity | the `Warehouse` itself — a self-invariant |
| new stock = previous stock, new capacity ≥ previous stock | a relation between two warehouses, expressible as `newWarehouse.canReplace(previous)` |
| business unit code unused | nobody — set-wide uniqueness, an aggregate can only see itself |
| location exists | nobody — needs the `LocationResolver` |
| location not full, capacity sum ≤ `maxCapacity` | **`Location`**, whose consistency boundary spans all the warehouses it hosts |

The last three cannot move onto `Warehouse` without giving the entity a repository and a resolver,
which is the modelling mistake the rule is meant to prevent. In DDD terms they belong to a *domain
service*, and that is what `WarehouseValidations` is: it lives in `domain/`, knows no JPA or JAX-RS
type, and receives its collaborators through ports. The per-location rules would ideally sit on a
`Location` aggregate root, but `Location` is not persisted here — `LocationGateway` serves a
hard-coded list — so it cannot load its own warehouses.

The first three rules could legitimately move onto the model. They stayed out because
`domain/models/Warehouse` is provided scaffolding: an anemic POJO with public fields, no
constructor, written field by field by `DbWarehouse.toWarehouse()` and by the REST mapper. Turning
it into a real aggregate root means private fields, a validating constructor and factories, hence
rewriting both mappers — restyling scaffolding the repository rules put off-limits, for a gain that
is cohesion rather than correctness. The middle path, if it is ever wanted, is to add behaviour to
`Warehouse` without touching its field style (`holdsMoreThanItCanStore()`,
`canAccommodateStockOf(previous)`, `hasSameStockAs(previous)`) and let the domain service keep only
the cross-aggregate rules; the existing tests pin the behaviour, so the refactor is verifiable.

Validation order is deliberate, because the first violated rule is the one reported:

| # | Create | Replace |
|---|---|---|
| 1 | mandatory values present, not negative | idem |
| 2 | business unit code unused | *(the code must exist — checked by the use case first, 404)* |
| 3 | location resolves | location resolves |
| 4 | stock ≤ capacity | stock ≤ capacity |
| 5 | — | new capacity ≥ **previous** stock |
| 6 | — | new stock = previous stock |
| 7 | location not full, capacity sum within the location max | idem, excluding the replaced unit |

Rules 5 and 6 overlap: once the stocks must be equal, a capacity that cannot hold the previous stock
also fails rule 4. Checking 5 before 6 is what makes "your replacement is too small" reportable as
its own message instead of hiding behind a stock mismatch — and it is what lets the two rules be
tested separately.

## 2. Archived warehouses are gone from the register

The single most consequential decision, and the one the commented-out `WarehouseEndpointIT` test was
pointing at: `getAll`, `findByBusinessUnitCode` and `findById` only return rows with
`archivedAt is null`.

Everything else follows from it:

- the listing loses the warehouse the moment it is archived;
- `GET /warehouse/{id}` on an archived unit is a 404, not a tombstone;
- archiving twice is a 404, so no extra "already archived" rule is needed;
- a business unit code is freed by archiving, which is exactly what "replace" needs — the archive and
  the re-creation of the same code happen inside one transaction and the uniqueness check still sees
  a clean register.

The alternative (returning archived rows and filtering in the use cases) was rejected: every caller
would have to remember the filter, and forgetting it is precisely the bug the assignment planted.

Archiving is a state change, not a deletion, so it goes through `WarehouseStore.update` with
`archivedAt` stamped. `remove` stays on the port as a physical delete; nothing in the four endpoints
uses it.

## 3. Replacement is one transaction

`ReplaceWarehouseUseCase.replace` is `@Transactional`: it archives the previous row and inserts the
new one atomically. A failure between the two would otherwise leave the business unit code with no
active warehouse at all. Both rows carry the same timestamp — the archived one and the new one are
two sides of a single event, and reading the history later is easier when they agree.

The replaced unit is excluded from the per-location limits (`§1`, rule 7). Without that, replacing
the only warehouse allowed at `HELMOND-001` would be rejected because the location "already hosts its
maximum", which would make replacement impossible at exactly the locations where it matters most.
The exclusion is by business unit code, so it also holds when the replacement moves to another
location — there the replaced unit is simply not among the occupants.

**Why replace does not delegate to `ArchiveWarehouseUseCase`.** Reusing the archive use case looks
like the obvious factoring, and it was rejected for three reasons. First, `archive(warehouse)`
resolves its argument by business unit code to produce its own 404; replace has already read that
row — it needs the previous stock and the location occupancy for the rules — so delegating means a
second query returning a second domain object for the same row, with the validated instance left
aside. Second, replace stamps `archivedAt` and `createdAt` with the same instant so the handover
reads as one event; the archive use case would call its own `LocalDateTime.now()` and the two rows
would drift apart. Third, the shared logic is an assignment and an `update` — a dependency between
use cases buys nothing here (transaction-wise it would be harmless: `REQUIRED` joins the caller's
transaction).

That balance changes as soon as archiving grows behaviour — a domain event, an audit entry, or the
release of the fulfilment links of the bonus task. At that point the duplicated stamping becomes a
drift risk, and the right move is to extract the shared step, e.g. a package-private
`archive(Warehouse stored, LocalDateTime at)` used by both use cases, rather than a call through the
port, which would reintroduce the second lookup.

## 4. Errors: domain exceptions, mapped at the edge

The domain throws `WarehouseValidationException` (400) and `WarehouseNotFoundException` (404) and
never sees a JAX-RS type. Two `@Provider` mappers in `adapters/restapi` translate them, reusing the
`exceptionType` / `code` / `error` JSON shape of the global `StoreResource.ErrorMapper` so a client
sees one error format across the whole API. JAX-RS picks the most specific mapper, so the global
`ExceptionMapper<Exception>` keeps catching everything else and returning its 500.

The payload is a record. It is a genuine immutable data carrier, and it produces the same three
fields as the hand-built `ObjectNode` of the original mapper with none of the ceremony.

`WarehouseNotFoundException` has no public constructor, only `forBusinessUnitCode` and `forId`: the
message text is then written once, and the two call sites in the use cases and the two in the
adapters cannot drift apart.

**`WarehouseExceptionMappers` looks unused, and is not.** Providers are wired by annotation
scanning, not by references: Quarkus indexes the classes carrying `@Provider` at build time. The
two mappers are nested classes — the outer type is only a namespace, since the project standard
allows a single top-level type per file — so no code mentions it and an IDE reports it as dead. The
endpoint tests are the proof that it is registered: the global `StoreResource.ErrorMapper` sets
`code = 500` for anything that is not a `WebApplicationException`, and the domain exceptions are
not, so without these mappers every rule violation would surface as a 500 instead of the asserted
400 and 404. Splitting them into two top-level files would silence the warning at the cost of
duplicating the response helper.

## 5. The 201 that the generated interface cannot express

`warehouse-openapi.yaml` documents `POST /warehouse` as a 201, but the generator emits an interface
method returning `Warehouse`, which RESTEasy answers with a 200. The usual fix,
`@ResponseStatus(201)` on the implementation, was tried first and does not work here: the JAX-RS
method is declared on the generated interface, and the annotation on the implementing method is not
read. Returning `Response` is not an option either — the signature belongs to the interface.

`WarehouseCreatedStatusFilter` therefore promotes 200 to 201 for `POST` on the single `warehouse`
path segment. It is scoped as narrowly as a global filter can be: any other path, any other method,
and any status the resource set itself are left untouched. The alternative — hand-writing the
resource instead of generating it — would have contradicted the repository's own setup, where the
YAML is the source of truth.

## 6. Testing strategy

The starting point was a suite that only knew the happy paths: two tests for creation, one each for
replacement and archiving, no test at all for six of the rules, and the one test that would have
caught the archived-listing bug commented out.

**Rules are tested without a container.** `InMemoryWarehouseStore` and `StubLocationResolver` are
hand-written fakes (no Mockito, per the repo conventions); the store hides archived warehouses just
like the repository and records what was created and updated, so a test can assert that a rejected
operation wrote *nothing*. The whole rule set runs in under 200 ms.

| Class | Tests | Covers |
|---|---|---|
| `CreateWarehouseUseCaseTest` | 13 | happy path, duplicate code, unknown location, location full, location capacity exceeded, stock > capacity, missing values, blank code, missing location, missing stock, negative capacity, negative stock, archived units not counted |
| `ReplaceWarehouseUseCaseTest` | 8 | happy path, unknown code (404), capacity below previous stock, stock mismatch, unknown location, location capacity exceeded, neighbours counted while the replaced unit is not (accepted and rejected) |
| `ArchiveWarehouseUseCaseTest` | 3 | happy path, unknown code, already archived |
| `WarehouseEndpointTest` | 10 | 201 + id, lookup by id, 404s including a non-numeric id, three 400s, archive → gone from listing / lookup / second archive, replacement keeping the code, replacement rejections |
| `WarehouseEndpointIT` | 2 | listing the seeded warehouses, archiving one of them — black-box on the packaged application |

The six gaps listed above are the six rejection tests in the first two rows; the archived-listing
behaviour is pinned by `archivedWarehousesDisappearFromTheListingAndFromTheLookups`.

`warehouses/domain` is covered at 100% of lines and branches, `WarehouseValidations` included — the
rules are the part of this codebase where an untested branch is a silently wrong answer, so that is
where the number is worth having. What remains uncovered in `WarehouseRepository` is unreachable
through the use cases: `remove` has no caller (archiving updates the row), and the
`managedEntityOf` fallback by business unit code only fires for a domain object without an id, which
the lookups never produce. Covering them would mean asserting on defensive code.

**Why a `@QuarkusTest` when `WarehouseEndpointIT` already exists.** The two are not the same kind of
test. `@QuarkusIntegrationTest` is black-box against the *packaged* artifact: it needs a `package`
phase and cannot inject beans, so it costs a full package + boot and only proves the assembled
application answers. `@QuarkusTest` runs the application in-JVM as part of the normal test loop, in
about a second once the shared container is up, and it is what caught the missing 201. Coverage is
split accordingly: the IT owns the seeded listing and the archive-a-seeded-unit path, and
`WarehouseEndpointTest` owns creation, the validation 400s, the 404s and replacement — the original
`listsTheWarehousesSeededInTheDatabase` was dropped once failsafe started running, since the IT
already asserts exactly that. The IT also cannot grow safely: its two tests share one application
instance while `testSimpleListWarehouses` asserts the presence of the very warehouse the other one
archives.

**Endpoint tests own their data.** `@QuarkusTest` shares one database for the whole run, so each test
uses its own business unit codes (`MWH.401`…`MWH.407`) and, where capacity matters, its own location
(`VETSBY-001`, `ZWOLLE-002`, `EINDHOVEN-001`, `HELMOND-001`). No test touches the seeded warehouses,
which keeps the listing assertions stable whatever order JUnit picks.

`import.sql` is untouched. Its deliberate violations (`MWH.001` holds 100 at a location capped at 40)
survive because the rules apply to new operations only — no validation runs at startup.

## 7. Limits and what was left alone

**The `*IT` ordering is still implicit.** Failsafe now sits in the main build, so `mvn verify`
packages the application and runs `WarehouseEndpointIT` in JVM mode (the `native` profile only swaps
in the native runner through `native.image.path`). The two IT tests share a single application
instance, and JUnit does not *guarantee* their order: `testSimpleListWarehouses` asserts `MWH.001`
is listed and would fail if the archiving test ran first. JUnit's default deterministic order
happens to run it first, and pinning it with `@TestMethodOrder` means editing a provided test, so it
stays flagged in `TODO.md` rather than done unasked.

**No concurrency control.** The per-location limits are read-then-write: two simultaneous creations
at the same location can both pass the check and jointly exceed `maxCapacity`. `@Transactional` alone
does not prevent it under `READ COMMITTED`. A production answer would take a lock on the location
row, or push the invariant into a database constraint. Out of scope here, but it is the first thing
that breaks under load.

**`id` is a `String` in the domain model** because the generated bean declares it as one. The
repository is the only place that knows it is really a `Long`, and a non-numeric id resolves to an
empty `Optional` — hence a 404 rather than a 500 on `GET /warehouse/abc`.

**Location capacity is compared against declared capacity, not stock.** `maxCapacity` sums the
warehouse capacities, as the field's own comment in `Location` states; stock only ever has to fit
inside its own warehouse.

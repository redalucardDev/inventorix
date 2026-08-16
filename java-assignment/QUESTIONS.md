# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
Yes: I would move Store and Product to the same hexagonal layout as Warehouse.

Three reasons, in the order they cost us.

1. Coupling to the persistence framework. In stores and products the business code is integrated to
   Panache: Store.listAll() is a static call on the entity itself, and that same entity is the
   JSON payload. GET /store/1 returns {"id":1,...}, so a client posting it back to create a
   second store sends `id` too - hence the "Id was invalidly set on request." guard, copy-pasted
   in ProductResource. In the other direction, renaming a column is a breaking API change.

2. The business rules are not isolated. There is no layer between the resource and the database,
   so validation lives in the JAX-RS handler coupled to HTTP statuses, and the transaction
   boundary belongs to the HTTP adapter (@Transactional on StoreResource.create) - which is
   what made the legacy synchronisation need an AFTER_SUCCESS event to run after commit.

3. Uniformity, which I do count as a real reason. The strategy changes per package (active
   record for Store, repository for Product, port + adapter for Warehouse) and nothing says
   which one is intended, so every new feature re-opens the question - I had to take that
   decision when adding the fulfilment package.

The big bonus behind 1 and 2 is the dependency inversion principle, the core of hexagonal
architecture: the domain declares the port (WarehouseStore) and the persistence adapter
implements it, so the rules depend on an interface they own instead of on Panache. That is what
makes them testable in isolation - the warehouse use cases run in milliseconds against a
hand-written InMemoryWarehouseStore, while any assertion on store behavior needs a real
database started by Dev Services. It also makes the persistence choice replaceable, which the
active record simply does not allow.

Product as well, even though it carries no rules today: it pays points 1 and 2 anyway, and
it is exactly where the first rule would be dropped into the resource - which is how
StoreResource ended up owning validation, the transaction and the legacy sync at once.

```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
Contract-first (the Warehouse API, generated from warehouse-openapi.yaml)

  Pros
  - The contract is the single source of truth and can be reviewed, versioned and discussed
    with consumers before a line of implementation exists.
  - No drift: the interface is regenerated at every build, so removing an endpoint or changing
    a payload breaks compilation instead of silently breaking a client.
  - Consumers can generate clients and mock servers from the same file, so front-end and
    back-end work can start in parallel.
  - The documentation is the contract.

  Cons, and this code base shows them concretely
  - Part of the contract does not survive code generation. The spec declares a 201 for
    POST /warehouse, but that status cannot be expressed on the generated interface and
    @ResponseStatus on the implementation is ignored, so a WarehouseCreatedStatusFilter had to
    be written just to restore the status the contract already promised.
  - Name collisions at the boundary: the generated bean com.warehouse.api.beans.Warehouse and
    the domain model Warehouse coexist, which forces fully-qualified names in
    WarehouseResourceImpl.
  - A weak spec produces a weak contract. Here the Warehouse schema has no `required` list and
    no validation constraints, `id` is exposed as a writable string in the request body, and
    the 400/404 responses are declared without any schema - so the error format is in fact not
    part of the contract, and the exception mappers had to invent it.
  - Build and tooling cost: an extra extension, generated-sources to configure in the IDE,
    generator upgrades that can change the generated code under you, and a slightly harder
    debugging path.

Code-first (Product and Store)

  Pros
  - Immediate, no build coupling, full access to every JAX-RS feature without fighting a
    generator.
  - Well suited to an internal endpoint or an early prototype where the shape is still evolving.

  Cons
  - A spec can still be published - quarkus-smallrye-openapi exposes one at /q/openapi - but it
    is derived from the code, so it describes the implementation instead of constraining it or deciding it. It
    can never disagree with what was shipped. A breaking change does not fail the build, it imply produces a new document, and consumers discover it at runtime. It also arrives after
    the code, so nothing can be agreed or built against beforehand.
  - The produced spec  quality depends entirely on annotations. Without @Schema / @APIResponse the generated
    document publishes the JPA entity as-is - `id` included for Store, so a column is an API
    field - and declares no error format at all.
  - Conventions diverge silently: today Product/Store answer 422 for a validation error while
    the Warehouse API answers 400 for the same class of problem.

My choice

I would keep contract-first for anything with an external consumer, and extend it to Store and
Product rather than dropping it on Warehouse - but I would fix the spec first: `required`
fields and formats, an explicit error schema shared by all endpoints, and separate
request/response schemas so `id` becomes read-only. I would keep the generated beans strictly
at the boundary and map them to the domain model (as WarehouseResourceImpl already does), and
add a CI check that diffs the spec against the previous version to catch breaking changes.

Code-first stays acceptable for a purely internal endpoint. What I would not keep is
the current in-between state, where half of the API has a contract and the other half does not,
with different error conventions on each side.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
Priority order = risk / cost: the budget goes on the business rules, not on what the framework
already guarantees.

1. Unit tests on the domain use cases.
   The warehouse and fulfilment rules are the real risk (unknown location, business unit code
   already taken, limits per location, capacity against stock, the three association limits).
   They run against hand-written fakes, with no database and no CDI container, in milliseconds,
   so covering every branch of the rule matrix is cheap and I cover it exhaustively.
   Written test-first: TDD is how I develop any non-trivial rule, because the test then
   specifies the behavior instead of confirming the implementation, it guarantees the test
   actually fails before the fix, and the design that emerges is the one that was testable from
   the start. It is also what makes the coverage of the behavior a by-product rather than a
   chase.

2. One thin endpoint test per resource (@QuarkusTest + RestAssured).
   Wiring, serialization, status codes and the shape of the error body - not the rules again.

3. Integration tests, for what only breaks in the real runtime: transaction boundaries,
   database constraints, Panache queries. Real container, real transaction manager, real
   database - only the outbound call to the legacy system (LegacyStoreManagerGateway) is faked,
   so a test can assert it fires after commit and not at all on rollback. Which no unit test can prove. In this code base: 
   StoreEndpointTest driving RecordingLegacyStoreManagerGateway. Database-level constraints and Panache queries fall in the same category.

4. A smoke test on the packaged application (@QuarkusIntegrationTest).

Following cases are not worth the budget: mock-heavy tests of the REST adapters, getters and setters, generated
code, and a broad end-to-end matrix the cost.

Keeping it effective over time - enforced by the build, not by a review habit:

  - JaCoCo is wired into this build already (report in target/jacoco-report). The next step is jacoco:check in the CI pipeline,
    failing the build under a branch threshold on the domain packages only: the rules *are* the
    conditionals, so a branch proves the rule was exercised in both directions - accepted and
    rejected - where a line only proves the code ran.
  - A floor that only moves up, never a score to reach: the threshold locks in what is already
    covered and is raised when it improves. A global percentage is never the gate, since
    coverage bought on CRUD is coverage not bought where the risk is.
  - No test that never runs: the *IT tests were bound to the `native` profile only, so
    `./mvnw verify` silently skipped them - false confidence is worse than no test. They now run
    in the main build.
  - Every bug fixed starts with a failing test reproducing it, so the suite grows where defects
    actually appear.
```

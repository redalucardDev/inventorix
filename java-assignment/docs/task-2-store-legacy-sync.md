# Task 2 — Design decisions: syncing stores to the legacy system after commit

Rationale behind commit `af81cde`. The assignment asks that `LegacyStoreManagerGateway` calls
happen only once the `Store` change **is committed to the database**, so the downstream legacy
system only ever receives confirmed data.

## 1. The defect in the original code

`StoreResource.create` was:

```java
@POST
@Transactional
public Response create(Store store) {
  store.persist();
  legacyStoreManagerGateway.createStoreOnLegacySystem(store);   // still inside the transaction
  return Response.ok(store).status(201).build();
}
```

`persist()` does not write to the database — with a sequence-backed id, Hibernate only assigns the
id and queues the INSERT. The flush happens when the `@Transactional` interceptor commits, i.e.
*after* the method returns. So the gateway was always called before the data existed, and any
failure at commit time left the legacy system holding a record we never stored.

This is not theoretical. `Store.name` is declared `@Column(length = 40, unique = true)`, so posting
a name that already exists fails at commit:

```
org.hibernate.exception.ConstraintViolationException: could not execute statement
  [ERROR: duplicate key value violates unique constraint "store_name_key"
   Detail: Key (name)=(TONSTAD) already exists.]
  at ...TransactionalInterceptorBase.endTransaction
```

The test written for this case fails against the original implementation with:

```
Expecting empty but was: [StoreSnapshot[id=4, name=TONSTAD, quantityProductsInStock=1]]
```

The legacy system was told about store id=4, which never existed. That failing assertion is the
specification for this task.

## 2. Options considered

| # | Approach | Verdict |
|---|---|---|
| A | Keep the inline call, but flush explicitly before it | Rejected — a flush is not a commit; the transaction can still roll back afterwards |
| B | Move the gateway call out of the resource into a second, non-transactional method | Rejected — the caller still has to know the commit already happened; the ordering guarantee is by convention, easy to break later |
| C | Register a `Synchronization` on the `TransactionSynchronizationRegistry` and call the gateway in `afterCompletion(STATUS_COMMITTED)` | Works, and is exactly what CDI does underneath — but it is manual plumbing repeated at each call site |
| D | **CDI event observed at `TransactionPhase.AFTER_SUCCESS`** | **Chosen** |
| E | Transactional outbox: write an outbox row in the same transaction, relay it asynchronously | Correct answer for production, over-engineered here — see §7 |

Option D is option C expressed declaratively: the container registers the synchronization, and the
guarantee is stated once, on the observer, instead of at each call site.

## 3. Why `AFTER_SUCCESS`

CDI lets an observer declare when it wants to be notified relative to the transaction:

| Phase | Delivered |
|---|---|
| `IN_PROGRESS` (default) | Immediately — same as the original bug |
| `BEFORE_COMPLETION` | Before commit, so the commit can still fail |
| `AFTER_COMPLETION` | After commit *or* rollback — would notify on failure too |
| `AFTER_FAILURE` | Only after rollback |
| **`AFTER_SUCCESS`** | **Only after a successful commit** |

`AFTER_SUCCESS` is a literal translation of the requirement. If the transaction rolls back, the
event is simply never delivered — which is what makes the rollback test pass without any explicit
error handling.

## 4. Design details

**Events are records holding a snapshot, not the entity.**

```java
record StoreCreatedEvent(Long id, String name, int quantityProductsInStock) { ... }
```

The observer runs after the transaction closed, so a `Store` handed over directly would be a
detached entity. It happens to work here because `Store` has no lazy associations, but it invites a
`LazyInitializationException` the day one is added. A snapshot is immutable, safe to hand to a
downstream system, and states exactly which fields the legacy contract depends on. Records are the
natural Java 21 form for this.

**Events are fired from the managed entity, not the request payload.**

The original `update` passed `updatedStore` — the deserialized request body — to the gateway. That
object has no id and reflects what the client *asked for*, not what was stored. The events are
built from `entity`, so the legacy system receives the persisted state including the generated id.
`StoreEndpointTest.notifiesTheLegacySystemWithTheStoredStateWhenAStoreIsUpdated` pins this: it
asserts the notified id equals the id assigned at creation, which the payload never carried.

**The observer is a separate class.** `LegacyStoreEventsObserver` is the only place that knows the
legacy system exists and when it may be called. `StoreResource` goes back to being a REST adapter
that announces facts about its own domain; it no longer depends on `LegacyStoreManagerGateway` at
all. Adding a second consumer of these events costs nothing.

**Visibility and injection.** The events, the observer and the resource constructor are
package-private — the narrowest scope that works, per the project standards — and the resource uses
constructor injection rather than the previous `@Inject` field.

## 5. Testing strategy — why this is a `@QuarkusTest` and not a pure unit test

The reasonable objection is that `@QuarkusTest` is slow and a unit test would be cheaper. I
disagree here specifically, for three reasons.

**The behaviour under test is infrastructure, not logic.** "The gateway is called only after the
commit" is a claim about four components cooperating: Hibernate deferring the INSERT to
flush-at-commit, PostgreSQL raising the unique violation, Narayana rolling back, and CDI declining
to deliver an `AFTER_SUCCESS` event. Remove the container and none of those participate. A unit
test would have to fake the transaction manager, and would then only prove that my fake calls my
observer — it would pass just as happily against the original buggy code. The rollback test is the
entire point of this task, and it only has teeth against a real transaction and a real constraint.

**There is no `@Mock` in the Mockito sense.** `RecordingLegacyStoreManagerGateway` is a hand-written
fake that appends to a list, as the project conventions require (Mockito is banned repo-wide). The
`@Mock` annotation on it is `io.quarkus.test.Mock`, a CDI `@Alternative` marker that selects which
bean is injected — an unfortunate name for what is just bean substitution. Substituting the gateway
is orthogonal to the transaction question: the real implementation writes a temp file and prints to
stdout, giving nothing to assert against. The fake creates the assertion surface; it does not
change what is being verified.

**The measured cost is small.** The headline duration is Quarkus boot plus the Dev Services
PostgreSQL container, paid once per run and shared by every `@QuarkusTest`. Surefire charges it to
whichever class starts first:

| Run | `ProductEndpointTest` | `StoreEndpointTest` | `LocationGatewayTest` |
|---|---|---|---|
| Boot charged to Product | 33.12 s | **1.93 s** | 0.50 s |
| Re-run after refactor | — | **1.69 s** | 0.54 s |

The marginal cost of the three store tests is about 1.7 s. Deleting them would not recover the 33 s.

**Where the cheap tests do belong.** The rule applied across this repo is: pure domain logic gets
plain JUnit with hand-written fakes and no container; endpoint and transaction behaviour gets
`@QuarkusTest`. Task 2 has no pure-logic layer — it is transaction wiring end to end. Task 3 is the
opposite: the warehouse validations (unique business unit code, per-location limits, capacity and
stock rules, replace semantics) are pure functions of their inputs and are tested against
`InMemoryWarehouseStore` and `StubLocationResolver`, with `@QuarkusTest` reserved for status codes
and JSON shape.

The one piece of pure logic here is the observer's event-to-`Store` mapping, currently covered only
indirectly through the endpoint tests. At three lines it did not seem worth a dedicated test.

## 6. Deliberately left alone

`StoreResource.patch` guards on `entity.name != null` and `entity.quantityProductsInStock != 0` —
it inspects the stored entity where it almost certainly means the incoming payload, so a patch can
overwrite a field the caller never sent. It is a real bug, but it belongs to the PATCH semantics,
not to the commit-ordering task, and fixing it would have widened this commit. Recorded in
`TODO.md` instead.

## 7. Limits of this solution

`AFTER_SUCCESS` guarantees the notification is never sent *before* a commit. It does **not**
guarantee the notification is eventually sent. If the observer throws, or the JVM dies between
commit and the gateway call, the store exists locally and the legacy system never hears about it —
the failure mode is simply inverted, from phantom records to missing ones.

Closing that gap needs the transactional outbox of option E: write the intent to an outbox table
inside the same transaction, so the row and the intent commit atomically, then have a relay publish
it with retries and at-least-once delivery, with the consumer made idempotent on the store id. That
is the right design once the legacy sync is business-critical, but it introduces a relay, a
delivery-state table and a retry policy — disproportionate for this assignment, where the stated
requirement is the ordering guarantee. Worth stating explicitly rather than leaving implied.

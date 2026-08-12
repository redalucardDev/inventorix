# Inventorix — Fulfilment Monolith

A Quarkus 3 / Java 21 application that manages the fulfilment domain of a retail company:
**warehouses**, **stores**, **products**, and the associations that make a warehouse the
fulfilment unit of a product for a given store.

The repository is an interview assignment made of two independent parts:

| Part | Content |
| --- | --- |
| [`java-assignment/`](java-assignment) | The Quarkus application — all the code lives here ([tasks](java-assignment/CODE_ASSIGNMENT.md), [questions](java-assignment/QUESTIONS.md)) |
| [`case-study/`](case-study) | Discussion-only scenarios, no code ([case study](case-study/CASE_STUDY.md), [domain briefing](case-study/BRIEFING.md)) |

The code base is derived from the [Quarkus quickstarts](https://github.com/quarkusio/quarkus-quickstarts).

## Features

- **Warehouses** — create, retrieve, replace and archive warehouse units, with the business
  rules of the domain enforced: unique business unit code, existing location, maximum number
  of warehouses and maximum capacity per location, stock that fits the capacity, and — on
  replacement — capacity accommodating the previous stock plus a matching stock.
- **Stores** — CRUD endpoints that propagate changes to a legacy system **only after** the
  database transaction has committed (CDI events observed with `TransactionPhase.AFTER_SUCCESS`).
- **Products** — CRUD endpoints backed by a Panache repository.
- **Fulfilment associations** — link a product, a store and a warehouse, under three limits:
  max 2 warehouses per product and store, max 3 warehouses per store, max 5 product types
  per warehouse.

### Architecture

The `warehouses` package follows a **hexagonal architecture** — dependencies point inward and the
domain knows nothing about JAX-RS, JPA or Quarkus:

```
warehouses/
├── domain/
│   ├── models      # plain POJOs (Warehouse, Location)
│   ├── ports       # WarehouseStore, LocationResolver, one port per operation
│   └── usecases    # business rules: create, replace, archive
└── adapters/
    ├── database    # JPA entity + Panache repository implementing WarehouseStore
    └── restapi     # REST resource implementing the generated interface + exception mappers
```

`stores`, `products`, `location` and `fulfilment` are deliberately written in simpler,
hand-rolled styles — the contrast between them is the subject of
[`QUESTIONS.md`](java-assignment/QUESTIONS.md).

The Warehouse REST API is **generated from an OpenAPI contract**:
`java-assignment/src/main/resources/openapi/warehouse-openapi.yaml` is the source of truth, and
the `quarkus-openapi-generator-server` extension generates the `com.warehouse.api` package into
`target/generated-sources` at build time.

## Prerequisites

- **JDK 21** (`maven.compiler.release=21`) with `JAVA_HOME` set
- **Docker** — Quarkus Dev Services starts a PostgreSQL container automatically in dev and test
  mode; only the production mode needs a database you provide yourself
- Maven is not required: the project ships with the Maven Wrapper (`./mvnw`)

## Getting started

All commands run from the `java-assignment/` directory.

```sh
cd java-assignment
```

### Dev mode (live reload)

```sh
./mvnw quarkus:dev
```

The application starts on <http://localhost:8080> — see <http://localhost:8080/index.html> for the
demo page and <http://localhost:8080/q/dev/> for the Quarkus Dev UI. Code changes, including JPA
entity changes, are applied on the next request.

### Build

```sh
./mvnw package
```

This also generates the Warehouse API sources. Run it once before opening the project in an IDE,
otherwise the `com.warehouse.api` classes will not resolve.

### Production mode

Start a PostgreSQL instance matching `application.properties`:

```sh
docker run -it --rm=true --name quarkus_test \
  -e POSTGRES_USER=quarkus_test -e POSTGRES_PASSWORD=quarkus_test -e POSTGRES_DB=quarkus_test \
  -p 15432:5432 postgres:13.3
```

Then run the packaged application:

```sh
java -jar ./target/quarkus-app/quarkus-run.jar
```

### Database and seed data

The schema is recreated at every start (`drop-and-create`) and seeded from
`src/main/resources/import.sql`: 3 stores, 3 products, 3 warehouses (`MWH.001`, `MWH.012`,
`MWH.023`) and 3 fulfilment associations. Locations are **not** persisted — they come from a
static list in `LocationGateway` (`ZWOLLE-001`, `AMSTERDAM-001`, `TILBURG-001`, …), each with its
own maximum number of warehouses and maximum capacity.

## Testing

```sh
./mvnw test                                          # all unit and @QuarkusTest tests
./mvnw test -Dtest=CreateWarehouseUseCaseTest        # a single test class
./mvnw test -Dtest=LocationGatewayTest#returnsEmptyWhenTheLocationDoesNotExist   # a single method
```

Tests use **JUnit 5 + AssertJ**, with RestAssured for the endpoint tests and hand-written fakes
(`InMemoryWarehouseStore`, `StubLocationResolver`) for the domain ports — no mocking framework.
A JaCoCo coverage report is produced in `target/jacoco-report/index.html`.

`@QuarkusTest` classes share a single database per run, so each test owns its own business unit
codes. `*IT` classes (`@QuarkusIntegrationTest`) are bound to failsafe in the `native` profile
only — a plain `./mvnw verify` does not execute them.

## API overview

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/warehouse` | List the active warehouse units |
| `POST` | `/warehouse` | Create a warehouse unit |
| `GET` | `/warehouse/{id}` | Get a warehouse unit by id |
| `DELETE` | `/warehouse/{id}` | Archive a warehouse unit |
| `POST` | `/warehouse/{businessUnitCode}/replacement` | Replace the active warehouse of a business unit |
| `GET` `POST` `PUT` `PATCH` `DELETE` | `/store`, `/store/{id}` | Manage stores |
| `GET` `POST` `PUT` `DELETE` | `/product`, `/product/{id}` | Manage products |
| `GET` | `/fulfilment?productId=&storeId=` | List fulfilment associations, optionally filtered |
| `POST` | `/fulfilment` | Associate a warehouse as fulfilment unit of a product for a store |
| `DELETE` | `/fulfilment/{id}` | Remove an association |

Business rule violations return `400` and unknown resources return `404`, both with the JSON shape
`{ "exceptionType": …, "code": …, "error": … }`.

Example — create a warehouse:

```sh
curl -X POST http://localhost:8080/warehouse \
  -H 'Content-Type: application/json' \
  -d '{"businessUnitCode":"MWH.042","location":"AMSTERDAM-001","capacity":40,"stock":10}'
```

## Troubleshooting

- **Compilation fails on `com.warehouse.api.*`** — run `./mvnw package` first. In IntelliJ, mark
  the generated `target/.../jaxrs` folder as a generated sources root.
- **Dev or test mode fails to start** — check that Docker is running, since Dev Services needs it
  to start PostgreSQL.
- **Startup fails on the seed script** — a new entity or column must be reflected in
  `src/main/resources/import.sql`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
Development conventions and Java standards for this repository are documented in
[CLAUDE.md](CLAUDE.md) and [.claude/STANDARDS.md](.claude/STANDARDS.md).

## License

Licensed under the [MIT License](LICENSE).

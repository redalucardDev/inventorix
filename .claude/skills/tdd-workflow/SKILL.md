---
name: tdd-workflow
description: >-
  Test-first protocol (RED / GREEN / REFACTOR) adapted to this Quarkus repo.
  Activate for any non-trivial behavior change: warehouse use-case logic, REST
  endpoint behavior, the fulfilment bonus task. Covers fakes for ports, shared
  @QuarkusTest database rules and seed-data constraints.
---

# Skill — TDD workflow

One behavior at a time: RED (one failing test) → GREEN (minimum code) → REFACTOR (tests stay green).
No production logic before a failing test demonstrates the missing behavior.

## Where to start the test

- **Use-case rule** (validation, replace semantics): plain JUnit test on the use case with **hand-written fakes** for `WarehouseStore` / `LocationResolver` — no Mockito, no Quarkus context, fast.
- **Endpoint behavior** (status codes, JSON shape): `@QuarkusTest` + RestAssured.
- Name tests after observable behavior (`rejectsCreationWhenBusinessUnitCodeAlreadyExists`) with `// Given / When / Then` comments. JUnit 5 + AssertJ.

## RED

- Exactly one failing test; the failure must be meaningful (assertion, not compilation noise).
- ⛔ Never modify an existing test to make room — ask first.

## GREEN

- Smallest coherent implementation; no speculative cases.
- Hard-coding a return value is legitimate while a single test exists — it is discipline, not laziness.
- **Triangulation**: when a hard-coded value feels wrong, don't guess — add a second test that forces the general solution. Let the tests drive the algorithm.
- **Obvious-implementation exception**: if the real code is trivially simple (e.g. a one-line delegation), write it directly.
- Don't write a second test during GREEN — finish the cycle first. Each GREEN takes minutes; if it drags, the test was too ambitious — back out and write a smaller one.
- Run the focused test: `./mvnw test -Dtest=CreateWarehouseUseCaseTest#methodName` (from `java-assignment/`).

## REFACTOR

- Apply the `java-standards` skill to every touched file.
- Validations stay in `domain/usecases`; adapters stay thin; domain throws `WarehouseValidationException` / `WarehouseNotFoundException`, never JAX-RS types.
- Never add new behavior during REFACTOR — if a missing behavior surfaces, note it and handle it in the next RED.

## Test doubles — pick the right one

This repo bans Mockito: interaction verification is done with **hand-written fakes**, not mocks.

| Double | Use when |
|--------|----------|
| **Dummy** | A parameter is required but irrelevant to this test |
| **Stub**  | You must control indirect input (e.g. a fixed clock/value) |
| **Fake**  | You need a working lightweight implementation (`WarehouseStore`, `LocationResolver`) |

Fixture data are `static final` constants in the test class.

## What TDD is not

- Not writing all tests first then all production code — **one test at a time**.
- Not testing private methods directly — assert observable behavior through the public API (use case / endpoint).
- Not chasing 100% line coverage — cover **meaningful behaviors**.
- Not a design-review tool — REFACTOR is, but only after GREEN.

## Repo test constraints

- `@QuarkusTest` shares **one database per run** (drop-and-create per start, not per test) — every test owns its business unit codes; never reuse another test's.
- Seed data intentionally violates location rules — don't assert against `MWH.001`/`ZWOLLE-001`/`TILBURG-001` capacity math. Tests needing headroom use `ZWOLLE-002`, `EINDHOVEN-001`, `HELMOND-001`, `VETSBY-001` or `AMSTERDAM-001`.
- `*IT` classes don't run under `./mvnw test` or plain `verify` — don't rely on them for feedback.
- Finish with a full `./mvnw test` before declaring the slice done, and tick `TODO.md`.

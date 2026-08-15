---
name: java-standards
description: >-
  Mandatory Java quality standards for the interview-assignment repo (checkstyle
  mirror, adapted). Activate BEFORE writing or reviewing any Java code: import
  rules, return/nesting limits, magic numbers, equals on non-null side, self-explaining
  code (comments only when ambiguous), and the review checklist.
---

# Skill — Java quality standards

Applies to **every Java file touched** (main and tests) under `java-assignment/`.
Full source of truth: `.claude/STANDARDS.md`. Rules apply to code you write or
modify — never restyle untouched assignment scaffolding.

## Rules to check systematically

1. Imports: **no star imports**; no unused/redundant imports; static imports in **tests only**.
2. Max **3** `return` per non-void method, **2** per void; guard clauses over nesting.
3. Nesting: `if` ≤ 4, `for` ≤ 3, `try` ≤ 2; boolean expressions ≤ 3 operands.
4. No duplicated string literals / magic numbers → `private static final` constants.
5. `.equals()` from the non-null side; never `==` on strings.
6. One top-level type per file; order: constants → fields → constructors → methods; utility classes get a private constructor.
7. Blank line at end of file; no `System.out` — use the JBoss `Logger` like `StoreResource`.
8. **Least visibility**: narrowest modifier that works (`private` → package-private → `protected` → `public`); never `public` unless required by the API or a framework (JAX-RS, CDI, JPA, generated interfaces, `@Provider`).
9. Constructor injection; 
10. Avoid returning null, use `Optional<T>` for lookup returns (never null, never Optional fields/params).
11. `var` **only when the type is visible on the same line** (`var w = new Warehouse();`, `for (var e : map.entrySet())`); explicit type for method returns, numeric literals and anything ambiguous (the two `Warehouse` classes).
12. **try-with-resources for every I/O resource** (streams, readers/writers, JDBC, sockets, `Files.lines`) — never a hand-written `finally` close.
13. Comments **not systematic** — code must be self-explaining; comment only when genuinely ambiguous (non-obvious "why"). Never restate what the code says; prefer rename/extract. (`// Given / When / Then` in tests stays.)

## Java 21 — leverage where it clarifies

- Records for immutable carriers (events, results, fixtures) — **not** JPA entities or generated OpenAPI beans.
- Switch expressions / pattern matching to replace `if`-`instanceof` chains.
- `Stream.toList()`. Never use a feature merely because it exists.

## Repo-specific gotchas

- **Two `Warehouse` classes**: generated REST bean (`com.warehouse.api.beans.Warehouse`) vs domain model — map in `WarehouseResourceImpl`, never mix layers.
- Generated sources live in `target/generated-sources` — run `./mvnw package` before expecting them to resolve.
- `import.sql` is **frozen — never modify it**; avoid schema changes that would need new seed rows.
- Domain layer (`warehouses/domain`) stays free of JAX-RS, JPA and Quarkus types.

> Full 10-point checklist: `.claude/STANDARDS.md`.

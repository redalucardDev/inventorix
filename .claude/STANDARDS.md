# STANDARDS.md — Java Quality Standards

> Checkstyle-style rules for this repository, adapted to the codebase's established style
> (google-java-format, 2-space). Checkstyle is not wired into `java-assignment/pom.xml`;
> the arbiter is this file plus the `java-standards` skill.
>
> Rules apply to code you **write or modify** — never restyle untouched assignment scaffolding.

---

## Non-negotiable rules

### Seed data

- ⛔ **Never modify `java-assignment/src/main/resources/import.sql`** — the seed file is
  frozen. Its intentional rule violations are
  deliberate; do not "fix" or extend them. Avoid schema changes (new non-nullable
  columns/entities) that would require new seed rows, since the seeds cannot be updated.

### Existing tests

- ⛔ **Never modify an existing test without explicit user approval**
  (assertion, fixture, naming, deletion). Creating new tests remains unrestricted.

### Imports

- No star imports (`import java.util.*` is forbidden), including static star imports.
- No unused or redundant imports.
- Static imports are allowed **in tests only** (AssertJ `assertThat`, RestAssured `given` — the idiom already in this repo); forbidden in main code.

### Return statements

- Maximum **3** `return` statements per non-void method, **2** per void method.
- Prefer guard clauses at the top over nested `if`/`else` pyramids.

### Nesting and complexity

- `if` nesting ≤ **4**, `for` nesting ≤ **3**, `try` nesting ≤ **2**.
- Boolean expressions: ≤ **3** operands — extract a well-named local or predicate method beyond that.
- Simplify `return condition ? true : false` and `if (x) return true; else return false;`.

### String literals and magic numbers

- No duplicated string literals → extract to `private static final String`.
- No magic numbers → extract to named constants (test data values inline in a `// Given` block are fine).
- Never compare strings with `==` / `!=`.
- `.equals()` always called from the non-null side: `"MWH.001".equals(code)`, not `code.equals("MWH.001")`.

### Class design

- One single top-level type per file.
- Declaration order: static constants → instance fields → constructors → methods.
- One statement per line; one variable per declaration.
- Utility classes (static methods only, e.g. `WarehouseValidations`) get a `private` constructor.
- No finalizers. Blank line at end of file.

### Local variables (`var`)

- Use `var` **only when the type is already visible on the same line** — typically
  `var warehouse = new Warehouse();`, and `for (var entry : map.entrySet())`.
- Write the type explicitly everywhere else: method returns (`var result = service.doThing()`
  hides the type), numeric literals (`var i = 0` is an `int`, never a `long`),
  and `new ArrayList<>()` without a type argument (infers `Object`).
- Never use `var` where two visible types share a name (the generated `com.warehouse.api.beans.Warehouse`
  vs. the domain `Warehouse`) — ambiguity outweighs brevity.
- If the variable name alone doesn't tell the reader what it holds, spell the type out.

### Resources (I/O)

- **Always use try-with-resources** for anything holding an OS or network resource:
  streams, readers/writers, `Connection`/`Statement`/`ResultSet`, sockets, HTTP clients'
  response bodies, `Files.lines(...)`.
- Never close a resource in a `finally` block by hand, and never rely on a finalizer or
  garbage collection to release one.

### Comments (self-explaining code first)

- **Code must explain itself**: prefer clear names, small methods and guard clauses over
  comments. Do **not** add comments systematically.
- Comment **only when the code is genuinely ambiguous** — a non-obvious business rule,
  a constraint the signature can't convey, a deliberate deviation or a "why" that isn't
  visible from the code itself. Never restate *what* the code already says.
- Prefer refactoring (rename, extract method) over a clarifying comment when possible.
- Exception: the `// Given / When / Then` structure comments in tests are the established
  repo idiom and stay.


### Visibility (least privilege)

- Use the **narrowest visibility that works**: prefer `private` for helpers/fields, then package-private, then `protected`, and only `public` when genuinely part of the type's API.
- **Do not make a class or method `public` unless it needs to be** — a class used only within its package stays package-private; a helper method stays `private`.
- Fields are `private` (or `final`); expose behavior, not state.
- **Framework exceptions** (keep public where required): JAX-RS resource classes/methods, CDI-managed beans and their injected constructors, JPA entities, implementations of the generated `WarehouseResource` interface, and `@Provider` mappers. Don't over-widen anything beyond what the framework contract requires.

### Adapted for this repo (deliberate deviations)

- **`this.` qualification**: only where the existing code uses it (constructor assignments, disambiguation) — not on every instance access.
- **`final` on parameters/locals**: encouraged where it aids reasoning, not mandatory — the existing code doesn't use it and consistency wins.
- **Javadoc**: English, only where it states something the signature can't (a business rule, a constraint); no boilerplate Javadoc on every method.
- **Inline comments**: not systematic — code should be self-explaining; add one only when the intent is genuinely ambiguous (see the *Comments* rule above).

---

## Java 21 (leverage, don't decorate)

- **Records** for immutable data carriers: CDI events, validation results, test fixtures. *Not* for JPA entities (`DbWarehouse`, `Store`, `Product`) or generated OpenAPI beans.
- **Switch expressions** and **pattern matching** (incl. record patterns) where they replace `if`/`instanceof` chains.
- **Sealed interfaces** for closed domain alternatives, only if a genuinely closed set emerges.
- `Stream.toList()` over `collect(Collectors.toList())`.
- A modern feature must make the code *clearer* — never use one merely because it exists.

---

## Checklist before submitting Java code

1. Any star imports? Static imports in main code?
2. Any duplicated string literals or magic numbers? → extract constants
3. Any `==` on strings? Is `.equals()` called from the non-null side?
4. Does any method exceed 3 returns (2 for void)? Nesting within limits?
5. Blank line at end of file?
6. Is every class/method at its narrowest workable visibility? Any `public` that isn't required by the API or a framework contract?
7. Is the code self-explaining? Any comment that just restates the code, or that should be a rename/extract instead? Comments only where genuinely ambiguous.
8. Any `var` whose type isn't visible on the same line? Any I/O resource opened outside try-with-resources?
9. New entity/column? → `import.sql` is frozen (no changes allowed), so avoid schema changes that would need new seed data (startup fails otherwise).
10. Do new tests own their business unit codes and use locations with headroom (`ZWOLLE-002`, `EINDHOVEN-001`, `HELMOND-001`, `VETSBY-001`, `AMSTERDAM-001`)?
11. Did a failing test exist before the production code?
12. Could a Java 21 feature (record, switch expression, pattern matching) make this clearer?

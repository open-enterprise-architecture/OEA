# ADR-029: Test Framework for BDD System Tests — Cucumber JVM

**Status**: accepted
**Date**: 2026-06-30
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)

---

## Context

As part of the requirements phase, 155 Gherkin feature files have already been generated under
`requirements/tests/` from the requirements (`requirements/req/REQ-*.md`)
(`scripts/generate_feature_files.py`). They form the V-model system test specification
and define *what* is tested — not *how*.

Before the start of implementation (ADR-012 Java/Spring Boot, ADR-027 Maven structure,
ADR-028 layered architecture), two questions must be clarified:

1. With which test execution framework are the Gherkin feature files executed against
   the Java/Spring Boot backend?
2. How are the step definitions (the Java code behind Given/When/Then) created,
   without a developer having to write them by hand for all 155 files?

This decision had previously only been recorded informally in `requirements/tests/README.md`
(tool versions, directory structure), without documented alternatives
or trade-offs — this is hereby made up for.

---

## Decision Drivers

- 155 feature files already exist in standard Gherkin format and are to be reused
  without migration
- Tag scheme (`@must`, `@should`, `@UC-06`, `@performance`, `@skip`, …) is
  already laid out in the feature files and must be usable for test selection
- Stakeholders without Java knowledge (product owner, QA) should still
  be able to read test scenarios
- Step definitions should be generatable by a locally hosted LLM (Qwen2.5-14B,
  4-bit quantization, headless via Ollama) — effort per
  feature file must be small and clearly delimited
- Integration with Spring Boot (dependency injection, Testcontainers) must work
  without an additional framework

---

## Considered Options

### Option 1: Cucumber JVM (cucumber-java + cucumber-spring + cucumber-junit-platform-engine)

- **Pro**: Standard Gherkin parser — the 155 existing feature files are
  usable without change; `cucumber-spring` injects Spring beans directly into
  step definitions; tag filtering (`--tags`) maps the existing tag scheme
  1:1; established ecosystem with Testcontainers integration; execution via
  standard Maven/JUnit Platform tooling
- **Con**: Additional layer of indirection (Gherkin → generated
  step definition class → Java) compared to a plain JUnit test; maintenance of
  step definitions as their own code layer

### Option 2: JUnit 5 + RestAssured without Gherkin/BDD layer

- **Pro**: Fewer tooling layers; direct Java code without
  Gherkin step mapping; slightly faster test execution
- **Con**: The 155 already created feature files would not be executable
  and would have to be discarded or translated into code comments; loss of
  readability for non-developers; no declarative tag filtering such as
  `@must and @UC-06`, JUnit tags would have to be rebuilt manually
- **Rejected**: Throws away already invested work (155 feature files,
  tag scheme, REQ traceability) without offering an advantage that outweighs this
  loss

### Option 3: Karate DSL

- **Pro**: Own BDD-like syntax with built-in HTTP client — for pure
  REST tests often less step definition code is needed
- **Con**: Own DSL instead of standard Gherkin — the 155 existing feature
  files would not be 1:1 compatible and would have to be rewritten;
  smaller ecosystem for Spring Boot/Testcontainers integration; less
  suitable for domain assertions beyond pure HTTP response checks (e.g.
  constraint checks, property validation in `core/`)
- **Rejected**: Incompatible with existing feature files; migration effort
  exceeds the benefit of the built-in HTTP client

---

## Decision

We choose **Option 1: Cucumber JVM**
(`cucumber-java`, `cucumber-spring`, `cucumber-junit-platform-engine`,
version 7.15.0), executed via Maven/JUnit Platform
(`mvn test -Dcucumber.filter.tags="..."`).

The Java step definitions under `backend/src/test/java/io/oea/steps/` are
generated during the implementation phase per feature file by **Qwen2.5-14B-Instruct**
(Q4_K_M, local headless via Ollama, see `requirements/tests/README.md`)
— one prompt per feature file, with Spring Boot/RestAssured/
Testcontainers as target stack.

**Rationale**: The feature files are already invested work and the
binding V-model test artifact of the requirements phase. Cucumber JVM is the
only one of the three options that reuses these files unchanged and
at the same time integrates natively into the Spring Boot stack (ADR-012, ADR-028).

---

## Consequences

### Positive Consequences

- Existing 155 feature files remain usable 1:1, no migration needed
- Tag-based filtering (`@must`, `@UC-06`, `@performance`, `not @skip`)
  already present and immediately usable (see `requirements/tests/README.md`)
- Stakeholders without Java knowledge can still read test scenarios
- Step definitions can be generated in isolation per feature file — small,
  clearly delimited prompts for Qwen instead of a monolithic codebase
- `cucumber-spring` uses the same dependency injection as the
  application layers from ADR-028, no parallel wiring needed

### Negative Consequences / Trade-offs

- Additional layer of indirection (Gherkin → step definition → Java) compared to
  a direct JUnit test means more tooling complexity and one more
  place where errors can arise (e.g. unrecognized step patterns)
- Quality of the generated step definitions depends on a locally hosted,
  4-bit-quantized 14B model — weaker than cloud alternatives,
  presumably higher manual repair effort
- Local generation on 16GB RAM is slow (approx. 4–8 tokens/s) — step
  definition generation for all 155 feature files is a batch process overnight,
  not an interactive workflow
- The 4 feature files with TODO placeholders (REQ-070, REQ-079, REQ-080,
  REQ-081, see `requirements/tests/README.md`) must be manually brought into the
  standard Given/When/Then format before step definition generation,
  otherwise the generation fails or delivers placeholder code

---

## References

**Related ADRs**:
- ADR-012: Backend stack Java 21 + Spring Boot 3 (target stack of the step definitions)
- ADR-027: Mono-repo and Maven module structure (directory `backend/src/test/`)
- ADR-028: Backend layered architecture (app services as test target of the step definitions)

**Concept**: §21 (Tech Stack Backend)

**Further source**: `requirements/tests/README.md` (tag scheme, Cucumber CLI filter examples, Qwen/Ollama setup)

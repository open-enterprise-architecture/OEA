# ADR-038: Spring Boot 4.1 Instead of Spring Boot 3

**Status**: accepted
**Date**: 2026-10-14
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: –
**Informed**: all contributors
**Partially supersedes**: [ADR-012](./ADR-012-backend-stack.md) (major version "Spring Boot 3"; Java 21, Hibernate, Spring Data JPA and Maven remain)
**Partially superseded**: 2026-10-15 by [ADR-039](./ADR-039-java-25.md) — Java 25 LTS instead of Java 21

## Context and Problem

[ADR-012](./ADR-012-backend-stack.md) chose Java 21 + Spring Boot 3 + Hibernate in June 2026. When the first implementation change (`ws-01-project-setup`) started on 2026-10-14, the open-source support of the Spring Boot 3 line had ended:

| Line | Latest release | End of open-source support |
|---|---|---|
| 3.5 | 3.5.16 (2026-06-25) | 2026-06-30 (ended) |
| 4.0 | 4.0.8 (2026-08-20) | 2026-12-31 |
| 4.1 | 4.1.1 (2026-08-20) | 2027-07-31 |

Starting on Spring Boot 3 would mean running without security fixes from the first commit, which contradicts the security baseline (REQ-191, security review 2026-10). No code exists yet, so the version can change without migration effort.

## Decision Drivers

- Security fixes for the framework from day one (REQ-191)
- No migration effort: no backend code exists yet
- Compatibility with the decided tooling: Java 21, Hibernate, Flyway, spec-first code generation (ADR-013), Testcontainers (ADR-029, ADR-036)
- Low maintenance for a single-maintainer project: as few major upgrades as possible before v1.0

## Considered Options

### Option 1: Spring Boot 3.5 (as in ADR-012)

- **Pro**:
  - No ADR change; the largest body of examples and answers
- **Con**:
  - No open-source security fixes since 2026-06-30; a major upgrade would be due before the first release anyway

### Option 2: Spring Boot 4.0

- **Pro**:
  - Supported major version; the first 4.x line with the longest field experience
- **Con**:
  - Open-source support ends 2026-12-31 – an upgrade to 4.1 would be needed within weeks

### Option 3: Spring Boot 4.1

- **Pro**:
  - Supported until 2027-07-31; current Spring Framework 7.0, Hibernate 7.4, Flyway 12, Jackson 3, Testcontainers 2
  - Supported by the OpenAPI generator (`useSpringBoot4`, optional Jackson 3) used for spec-first server interfaces
- **Con**:
  - Fewer community examples than for 3.x; some third-party libraries may lag behind (to be checked per dependency)

## Decision

We choose **Option 3: Spring Boot 4.1** (Java 21 unchanged). The backend follows the latest supported Spring Boot minor line; a minor upgrade is planned whenever the current line has less than three months of open-source support left.

Concretely for `ws-01-project-setup`: `spring-boot-starter-parent` 4.1.x, OpenAPI generator with `useSpringBoot4=true` and Jackson 3, Testcontainers 2, Flyway as managed by Spring Boot.

## Consequences

### Positive Consequences

- Security fixes for the framework from the first commit
- Current versions of Hibernate, Flyway and Testcontainers without later migration work

### Negative Consequences / Trade-offs

- Spring Boot 4 / Spring Framework 7 / Jackson 3 changed APIs compared with 3.x; examples written for 3.x need adaptation
- Regular minor upgrades (roughly twice a year) are required to stay in open-source support

### Follow-up Decisions

- None

## Relation to Existing ADRs

| ADR | Effect |
|---|---|
| [ADR-012](./ADR-012-backend-stack.md) | Partially superseded: "Spring Boot 3" becomes "Spring Boot 4.1"; Java 21, Hibernate, Spring Data JPA and Maven remain |
| ADR-013, ADR-016, ADR-018, ADR-029 | Mention "Spring Boot 3" only as context; their decisions are unaffected |

## References

**Concept Chapters**:

- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-012](./ADR-012-backend-stack.md), [ADR-013](./ADR-013-api-style.md), [ADR-029](./ADR-029-cucumber-test-framework.md), [ADR-036](./ADR-036-dev-test-identity-provider.md)

**Use Cases / Requirements**:

- [REQ-191: Supply chain integrity](../requirements/req/REQ-191-supply-chain-integrity.md)

## Notes

Sources for support dates: endoflife.date (Spring Boot) and release dates on Maven Central, checked 2026-10-14.

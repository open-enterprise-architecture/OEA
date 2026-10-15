# ADR-039: Java 25 LTS Instead of Java 21

**Status**: accepted
**Date**: 2026-10-15
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: –
**Informed**: all contributors
**Partially supersedes**: [ADR-012](./ADR-012-backend-stack.md) (Java version "Java 21" and container base `eclipse-temurin:21-jre-alpine`), [ADR-038](./ADR-038-spring-boot-4.md) (statement "Java 21 unchanged")

## Context and Problem

[ADR-012](./ADR-012-backend-stack.md) chose Java 21 in June 2026; [ADR-038](./ADR-038-spring-boot-4.md) kept it when moving to Spring Boot 4.1. Java 25, the next long-term support (LTS) release, has been generally available since September 2025. The first implementation change (`ws-01-project-setup`) is still unmerged, so the Java version can change before any code reaches `main`.

| LTS | GA | End of support Eclipse Temurin | End of Premier Support Oracle JDK |
|---|---|---|---|
| Java 21 | 2023-09 | 2029-12-31 | 2028-09-30 |
| Java 25 | 2025-09 | 2031-09-30 | 2030-09-30 |

OEA ships its own container image with the Java runtime (ADR-036 §3, REQ-075). The runtime therefore determines how long the image receives security fixes from the JDK vendor (REQ-191).

## Decision Drivers

- Long security support of the runtime shipped in the OEA image (REQ-191)
- No migration effort: the backend exists only on the unmerged walking-skeleton branch
- Compatibility with the decided stack: Spring Boot 4.1 (ADR-038), Hibernate, Flyway, Testcontainers, ArchUnit, Cucumber (ADR-029), OpenAPI generator (ADR-013)
- Few major upgrades for a single-maintainer project before and after v1.0

## Considered Options

### Option 1: Java 21 (as in ADR-012 and ADR-038)

- **Pro**:
  - No change; longest field experience of all LTS releases supported by Spring Boot 4
- **Con**:
  - Vendor support ends about two years earlier (Temurin 2029-12-31); an LTS upgrade would be due shortly after v1.0

### Option 2: Java 25 LTS

- **Pro**:
  - Temurin support until 2031-09-30, almost two years longer than Java 21
  - Supported by Spring Boot 4.1 and the libraries in use; no code exists that would need migration
  - Language and runtime improvements of Java 22–25 (e.g. stable scoped values, flexible constructor bodies, compact object headers, AOT cache improvements) are available from the start
- **Con**:
  - Younger release: some third-party tools or libraries may support it later than Java 21 (to be checked per dependency)
  - Developers need a JDK 25 locally

### Option 3: Track the latest non-LTS release

- **Pro**:
  - Newest language and runtime features
- **Con**:
  - Six-month support windows; a JDK upgrade every half year contradicts the low-maintenance driver

## Decision

We choose **Option 2: Java 25 LTS**. The backend is compiled for and runs on Java 25 (`java.version` 25 in `backend/pom.xml`); the container image uses Eclipse Temurin 25 (JDK for the build stage, JRE for the runtime stage), pinned by digest (REQ-191). The next LTS upgrade is planned when the support of Java 25 has less than one year left or when Spring Boot requires it.

## Consequences

### Positive Consequences

- The shipped runtime receives vendor security fixes until 2031-09-30
- No LTS upgrade is needed around the first releases

### Negative Consequences / Trade-offs

- Local development and CI need JDK 25; the CI setup action installs Temurin 25
- New dependencies must be checked for Java 25 support before adoption

### Follow-up Decisions

- None

## Relation to Existing ADRs

| ADR | Effect |
|---|---|
| [ADR-012](./ADR-012-backend-stack.md) | Partially superseded: "Java 21" becomes "Java 25"; container base Eclipse Temurin 25 instead of `eclipse-temurin:21-jre-alpine` |
| [ADR-038](./ADR-038-spring-boot-4.md) | Partially superseded: "Java 21 unchanged" becomes Java 25; Spring Boot 4.1 remains |
| ADR-013, ADR-016, ADR-018, ADR-027, ADR-029, ADR-030 | Mention "Java 21" only as context; their decisions are unaffected |

## References

**Concept Chapters**:

- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-012](./ADR-012-backend-stack.md), [ADR-038](./ADR-038-spring-boot-4.md), [ADR-036](./ADR-036-dev-test-identity-provider.md)

**Use Cases / Requirements**:

- [REQ-191: Supply chain integrity](../requirements/req/REQ-191-supply-chain-integrity.md)
- [REQ-075: Platform-independent deployment](../requirements/req/REQ-075-platform-independent-deployment.md)

## Notes

Sources for support dates: endoflife.date (Eclipse Temurin, Oracle JDK), checked 2026-10-15. Current Temurin release at the time of the decision: 25.0.4.1.

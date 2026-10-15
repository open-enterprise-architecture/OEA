# ADR-041: Root Aggregator POM for the Dependency Graph

**Status**: accepted
**Date**: 2026-10-15
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: –
**Informed**: all contributors
**Partially supersedes**: [ADR-027](./ADR-027-project-setup-monorepo.md) §2 (the backend stays a single Maven module; a root aggregator `pom.xml` is added)

## Context and Problem

Supply chain integrity (REQ-191) relies on GitHub's dependency graph and Dependabot alerts. For Maven, the graph reads only the direct dependencies from `pom.xml`; transitive dependencies (e.g. `spring-core` pulled in by the Spring Boot starters) are known only after Maven resolves them.

The first implementation change (`ws-01-project-setup`, design D9) chose GitHub's *Automatic dependency submission* instead of an own submission workflow, because an own workflow needs `contents: write` in a job that runs Maven – third-party code with a write token, which REQ-192 forbids. The repository owner enabled the setting on 2026-10-15.

Verification after the merge of #24, which changed `backend/pom.xml`: no submission ran, and the graph lists only the 27 direct dependencies. GitHub's automatic submission watches manifest files in the **repository root**; the monorepo layout of ADR-027 places the only `pom.xml` in `backend/`.

## Decision Drivers

- Transitive Maven dependencies in the dependency graph, so that Dependabot alerts cover them (REQ-191)
- No write token in jobs that execute third-party code (REQ-192)
- Keep the monorepo layout and the single backend module of ADR-027
- Little maintenance: no own workflow to pin and update

## Considered Options

### Option 1: Root aggregator POM

A minimal `pom.xml` in the repository root with `packaging pom` and the module `backend`. It declares no dependencies and no plugins.

- **Pro**:
  - GitHub's automatic submission sees a root manifest and resolves the backend module, including all transitive dependencies
  - No own workflow, no write token
  - Builds, CI and Dependabot keep working in `backend/` unchanged
- **Con**:
  - One more file in the root whose purpose is not obvious; documented in the file and here
  - Relies on GitHub behavior (root manifest) that is documented only briefly

### Option 2: Read-only vulnerability scan in CI

OSV-Scanner or OWASP Dependency-Check on the resolved dependency tree in a read-only CI job.

- **Pro**:
  - Independent of GitHub's dependency graph; fails the build on known vulnerabilities
- **Con**:
  - The dependency graph and Dependabot alerts still miss transitive dependencies; a second tool to maintain

### Option 3: Own submission workflow

The Maven dependency submission action in a workflow with `contents: write`.

- **Pro**:
  - Full control over the submission
- **Con**:
  - Write token in a job that resolves and runs Maven plugins; needs an exception to REQ-192

## Decision

We choose **Option 1: a root aggregator POM** (`pom.xml` in the repository root, `artifactId oea-aggregator`, module `backend`). The backend remains a single Maven module; all builds, tests and the CI keep running in `backend/` with its Maven wrapper. The aggregator must never declare dependencies or plugins.

If the dependency graph still does not show transitive dependencies after the aggregator is on `main`, Option 2 is implemented as fallback.

## Consequences

### Positive Consequences

- Dependabot alerts cover transitive backend dependencies without any write token in workflows
- No change to the build, the CI jobs or the backend module structure

### Negative Consequences / Trade-offs

- An additional root file; future Maven modules (if ADR-027 is ever revisited) are added there
- The automatic submission runs only when a `pom.xml` changes on the default branch

### Follow-up Decisions

- None

## Relation to Existing ADRs

| ADR | Effect |
|---|---|
| [ADR-027](./ADR-027-project-setup-monorepo.md) | Partially superseded: §2 gains a root aggregator `pom.xml`; the backend stays a single module in `backend/` |
| ADR-038, ADR-039 | Unaffected |

## References

**Concept Chapters**:

- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-027](./ADR-027-project-setup-monorepo.md), [ADR-036](./ADR-036-dev-test-identity-provider.md)

**Use Cases / Requirements**:

- [REQ-191: Supply chain integrity](../requirements/req/REQ-191-supply-chain-integrity.md)
- [REQ-192: CI least privilege](../requirements/req/REQ-192-ci-least-privilege.md)

## Notes

Local check: `cd backend && ./mvnw -f ../pom.xml dependency:tree` resolves the backend module through the aggregator, including transitive dependencies such as `org.springframework:spring-core`. The verification on GitHub follows after the merge (task 9.1 of the archived change `2026-10-15-ws-01-project-setup`).

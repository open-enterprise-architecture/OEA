# ADR-027: Project Setup — Mono-Repo, Maven Structure, Package Naming, Docker Compose

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas – Repository Owner](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Context

Before the first code commit, four structural questions must be decided that can
only be changed later at high cost:

1. **Repository strategy**: One repo for everything or separate repos per layer?
2. **Backend module structure**: One Maven module or several?
3. **Java package naming**: Which base package for all Java classes?
4. **Local development environment**: How does one start OEA on a developer laptop?

---

## Decisions

### 1. Mono-Repo

All layers — backend, frontend (client app + web portal), API spec,
documentation, and requirements — live in a single Git repository.

```
oea/
├── backend/              ← Java 21 / Spring Boot (ADR-012)
│   ├── src/
│   └── pom.xml
├── frontend/             ← Vue 3 + TypeScript (ADR-011)
│   ├── src/              ← shared code: components, stores, router
│   ├── electron/         ← Electron main process + preload (ADR-008)
│   └── portal/           ← web portal entry point (no Electron)
├── api/                  ← openapi.yaml (contract between backend + frontend)
├── adrs/                 ← Architectural Decision Records
├── requirements/         ← use cases, REQs, user stories
├── business-analysis/    ← stakeholders, vision
├── business-objects/     ← domain model
├── docs/                 ← data model, Walking Skeleton, screens
├── scripts/              ← helper scripts
├── docker-compose.yml    ← local development environment
└── AGENTS.md
```

**Rationale**: Backend API and frontend are tightly coupled (OpenAPI contract).
A breaking change in the API requires simultaneous adjustment in the frontend — this
belongs in a single PR. Contributors clone one repo. The Walking Skeleton
runs as a single `docker compose up`.

### 2. Backend: Single Maven Module for v1

The backend is a **single Maven module** (`backend/pom.xml`). Internal
package structure replaces module boundaries:

```
backend/src/main/java/io/oea/
├── api/          ← REST controllers, request/response DTOs, OpenAPI (ADR-028)
├── app/          ← application services: stable interfaces + commands/queries (ADR-028)
├── core/         ← domain model, repositories, domain services
├── audit/        ← audit schema handling (ADR-024)
├── integration/  ← external write access / service accounts (ADR-026)
└── config/       ← Spring configuration, security, Flyway
```

The communication rules between the three layers (`api → app → core`)
are described in ADR-028.

**Why not multi-module?** Maven multi-module projects (several `pom.xml` files
in a parent-child hierarchy) only bring real benefit with multiple teams or reusable
libraries. For v1 with one developer it only means overhead:
separate build steps, maintaining inter-module dependencies in XML, longer
configuration time.

**Retrofittable**: If OEA grows and e.g. an `oea-client-lib` for external
API clients becomes useful, the module can be extracted without breaking changes.

### 3. Java Package Naming: `io.oea`

All Java classes use `io.oea` as the base package:

```java
io.oea.api.v1.controller.EntityController
io.oea.app.entity.EntityAppService
io.oea.app.entity.command.UpsertEntityCommand
io.oea.core.domain.entity.Entity
io.oea.core.repository.EntityRepository
io.oea.audit.service.AuditEventWriter
io.oea.integration.service.BatchWriteService
```

**Rationale**: `io.oea` is short, technically correct (reverse-domain convention)
and unambiguous. Alternatives like `com.openea` or `org.openea` would presuppose a
registered domain that does not exist at the time of the project.

### 4. Local Development: Docker Compose

```
docker compose up
```

starts all services required for the Walking Skeleton:

```yaml
services:
  db:        # PostgreSQL 15 (primary development DB)
  api:       # Spring Boot backend (profile: dev)
  frontend:  # Vite dev server with HMR
```

Additional compose files for other DBs (REQ-150):
- `docker-compose.mysql.yml`    — MySQL 8
- `docker-compose.sqlserver.yml` — SQL Server 2019

**No Keycloak in the compose for the Walking Skeleton**: Auth via OIDC (ADR-006)
is bridged in dev mode with a simplified JWT stub that requires no
external OIDC provider locally. For integration tests: Keycloak as an optional
compose service (`--profile keycloak`).

### 5. Frontend Package Manager: npm (no Yarn, no pnpm)

Vite and Vue 3 support all three. npm is chosen because:
- No additional tool needed (npm comes with Node.js)
- GitHub Actions caching for npm available out-of-the-box
- No `.npmrc` configuration for private registries needed (OSS)

---

## Directory Conventions

| Path | Content |
|---|---|
| `backend/src/main/java/io/oea/` | Java source code |
| `backend/src/main/resources/` | `application.yml`, Flyway migrations (`db/migration/`) |
| `backend/src/test/java/io/oea/` | Unit and integration tests |
| `frontend/src/components/` | Vue components (shared) |
| `frontend/src/stores/` | Pinia stores |
| `frontend/src/api/` | Generated OpenAPI client (do not edit manually) |
| `frontend/electron/main.ts` | Electron main process |
| `frontend/portal/main.ts` | Web portal entry point |

---

## Rejected Alternatives

### Multi-Repo (backend + frontend separate)

- **Pro**: Independent CI pipelines; Electron build isolated
- **Con**: Cross-repo coordination on API changes; two repos for contributors;
  Walking Skeleton unwieldy across repo boundaries
- **Rejected**: Overhead without benefit for v1

### Maven Multi-Module from the Start

- **Pro**: Enforces clean module boundaries early
- **Con**: Configuration effort (3–5 `pom.xml`); inter-module builds slower;
  no added value for one developer
- **Rejected**: YAGNI — retrofittable when needed

### Gradle instead of Maven

- **Pro**: Shorter build files (Kotlin DSL); incremental builds faster
- **Con**: Spring Boot Initializr generates Maven; team context knows Maven;
  ADR-012 implies Maven (`./mvnw`)
- **Rejected**: Consistency with ADR-012

---

## Consequences

**Positive:**
- First `git clone` + `docker compose up` = runnable development environment
- No cross-repo versioning needed
- API spec and generated client always in sync
- CI can coordinate backend and frontend in one pipeline

**Negative / Compromises:**
- Git repository grows with code (acceptable; no large binary content)
- Electron build (code signing, platform-specific releases) runs in the same
  pipeline — complexity in CI, not in the repo
- Single Maven module: package discipline requires code review instead of compiler enforcement

---

## Affected Concept Chapters

- §21 (Tech Stack), §6 (Repository Structure)

## Related ADRs

- ADR-011: Frontend Vue 3
- ADR-012: Backend Java/Spring Boot
- ADR-015: Flyway migrations
- ADR-023: Multi-DB (docker-compose.mysql.yml)
- ADR-026: External integration (backend/integration/)
- ADR-028: Backend layered architecture api/app/core (communication rules)

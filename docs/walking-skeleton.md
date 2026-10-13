# Walking Skeleton – Definition

**As of**: 2026-10-04
**Status**: defined (revised: story list updated, entities via batch API instead of form, placement in S1–S2)

---

## What Is the Walking Skeleton?

A walking skeleton is the thinnest possible end-to-end cut through all technical layers that delivers real value for at least one stakeholder. It proves that the architecture works — not as a hello world, but as a minimal, usable version of the actual product.

AGENTS.md prescribes: **exactly one end-to-end use case**. The skeleton is implemented **at the beginning** (sprint S1–S2) and only then expanded in breadth (anti-pattern AP-25 "Walking skeleton as big bang" in `docs/anti-patterns.md`).

---

## Decision: UC-06 (Create and Use Catalog)

### Rationale

| Criterion | UC-05 Architecture Vision (Solution, Deltas, Canvas) | UC-06 Catalog | Why Catalog Wins |
|---|---|---|---|
| Frontend complexity | Vue Flow (nested nodes, layout engine, drag & drop) | Table (sortable) | Catalog is significantly simpler to render |
| Domain model complexity | Solution, EntityDelta, Plateau, conflict detection | ArchitectureEntity + EntityTypeDefinition | Catalog gets by with the core model |
| External dependencies | Vue Flow, ELK.js, D3 | no additional libraries | less risk at project start |
| Stakeholder value | Architect can model | **Every** stakeholder can view the inventory | more universal value |
| Scope | > 30 SP, of which > 50 % canvas code | **31 SP** incl. bootstrapping and login (see below) | realistic in 2 sprints incl. project setup |

UC-06 depends only on UC-01 (login) and UC-04 (metamodel), not on UC-05 (see preconditions in UC-06). UC-05 later supplements the catalog with the business path of capturing entities as a delta of a solution.

**What the skeleton proves**:

- Docker Compose starts (REQ-075): backend, DB, auth, frontend
- Persistence: Flyway migrations, entity persistence with JSONB properties, metamodel query, audit events (ADR-015, ADR-016, ADR-024)
- OIDC auth: token validation, role mapping (ADR-006)
- REST API spec-first from `api/openapi.yaml`: metamodel import, entity batch, catalog creation, catalog query (ADR-013)
- Frontend: Vue app renders in the browser; catalog creation and catalog table (ADR-011, ADR-014)
- End-to-end data flow: import script → API → DB → table in the browser

---

## Prerequisite UCs (Must Be Implemented Minimally in the Skeleton)

UC-06 alone cannot be started. Three UCs are prerequisites, plus a way to create entities:

| UC | Why Necessary | Minimal Scope |
|---|---|---|
| **UC-02** (Bootstrapping) | Without admin user and `instance-slug` (ADR-001) there is no instance | US-013 (local bootstrapping) |
| **UC-01** (Login) | Without auth no API request is possible | US-001 (OIDC login) |
| **UC-04** (Metamodel, minimal) | Without at least one EntityType there is no catalog | US-033 (import starter package) |
| Create entities | Direct creation in the catalog does not exist from a business perspective; entities arise as a solution delta (UC-05) or via import | US-141 (batch API, minimal scope) |

---

## Minimal User Stories of the Walking Skeleton

These 6 user stories form the walking skeleton. Everything else comes afterwards.

| # | User Story | UC | SP | Skeleton Scope | What Is Proven |
|---|---|---|---|---|---|
| 1 | [US-013](../requirements/user-stories/US-013-local-bootstrapping.md): Local bootstrapping | UC-02 | 5 | complete | DB schema, Flyway, instance-slug (ADR-001), first admin user |
| 2 | [US-001](../requirements/user-stories/US-001-oidc-login.md): OIDC login | UC-01 | 8 | complete | OIDC token flow, auth stack (ADR-006), session |
| 3 | [US-033](../requirements/user-stories/US-033-import-metamodel.md): Import metamodel / starter package | UC-04 | 5 | complete | Package import (ADR-002), `ApplicationComponent` available, `scope=imported` |
| 4 | [US-141](../requirements/user-stories/US-141-entity-batch-import.md): Create entities via batch | – | 5 | AC1 (upsert of entities); connections, dryRun, and service account audit (AC2–AC4) follow later | Entity persistence, JSONB, validation against metamodel, audit |
| 5 | [US-046](../requirements/user-stories/US-046-create-catalog.md): Create catalog | UC-06 | 3 | complete (incl. primary entity type) | Catalog configuration, metamodel API, role check |
| 6 | [US-049](../requirements/user-stories/US-049-show-catalog-data.md): Display catalog data | UC-06 | 5 | Table, sorting, empty state; default columns `name` and `status` as long as no columns are configured; joins, pagination, and SavedViews follow later | Query API, table in the Vue frontend |

**Total: 31 story points** plus one-time project setup (Maven module `backend/`, Vue project `frontend/`, `api/openapi.yaml`, Docker Compose with PostgreSQL and Keycloak, CI). Planned for **sprint S1–S2**.

The omitted acceptance criteria of US-141 and US-049 are caught up in the expansion sprints; the stories only count as `done` then.

---

## Walking Skeleton Scenario (End-to-End Flow)

```text
1. Admin starts docker compose up
   → PostgreSQL, backend, Keycloak, frontend start
   → Flyway migrations run through

2. Admin opens browser: https://oea.local
   → Vue app loads

3. Admin calls /setup (no admin exists yet)
   → US-013: enters instance-slug "acme-corp" and OIDC config
   → Result: first admin user stored; instance-slug = "acme-corp"

4. Admin logs in (UC-01)
   → US-001: OIDC redirect → token → session (token storage after security review, not in LocalStorage)
   → Admin lands on an empty start page

5. Admin imports starter package
   → US-033: "Import starter configuration" → oea-starter-togaf-classic (ADR-005)
   → ApplicationComponent, DataObject, TechnologyComponent etc. are available as EntityTypes

6. Import script creates entities
   → US-141: POST /api/v1/entities/batch (strategy=upsert) with e.g. 3 ApplicationComponents
   → "CRM System", "ERP", "Intranet" are in the repository; audit events written

7. Admin creates catalog
   → US-046: "New catalog" → name "Application Inventory", primary entity type ApplicationComponent

8. User opens the catalog
   → US-049: Table shows 3 rows with name | status, sortable by name

9. Result: The imported applications are in the EA repository.
   Every logged-in user sees them in the catalog.
```

This scenario proves all technical layers end to end. The architecture works.

---

## Explicitly Excluded (Comes After the Walking Skeleton)

| Feature | UC | Rationale |
|---|---|---|
| Solution, deltas, canvas / diagrams | UC-05 | Temporal model and Vue Flow — highest complexity |
| Column configuration, joins, filters, pagination, SavedViews | UC-06 (US-047 ff.) | Expansion; default columns suffice for the skeleton |
| Connections, dryRun, service accounts in batch import | US-141 AC2–AC4 | Expansion |
| Creation wizard | US-069 / REQ-066 | Expansion |
| Dashboard | UC-07 | requires working entities |
| Data lineage | UC-08 | Feature track; requires entity model |
| Arc42 | UC-09 | Feature track |
| BPMN / processes | UC-10 | Feature track |
| Web portal (read-only) | ADR-008 | Dual track; after client app |
| Password/TOTP/passkey login, remote bootstrapping | UC-01, UC-02, UC-03 (remainder) | Expansion; OIDC and local bootstrapping suffice for the skeleton |

---

## Technical Stack (the Walking Skeleton Puts the Decisions to the Test)

| Layer | Decision | ADR |
|---|---|---|
| Deployment | Docker Compose; `docker compose up` = runnable | REQ-075, ADR-027 |
| Backend | Java 21 + Spring Boot 3 + Hibernate; Maven module `backend/`; layers api/app/core | ADR-012, ADR-027, ADR-028 |
| Database | PostgreSQL 15 + JSONB; Flyway migrations | ADR-015, ADR-016 |
| API | REST, JSON; OpenAPI 3.x spec-first (`api/openapi.yaml`) | ADR-013 |
| Auth | OIDC; Keycloak locally, Entra ID / Authentik in production | ADR-006 |
| Frontend | Vue 3 + TypeScript + PrimeVue (DataTable) | ADR-011, ADR-014 |
| Tests | Cucumber JVM for the feature files of the skeleton REQs | ADR-029 |
| Entity IDs | Integer; instance-slug set during bootstrapping | ADR-001 |
| Metamodel | scope: built-in / imported / organization | ADR-002 |
| Starter package | oea-starter-togaf-classic as JSON import | ADR-005 |
| Audit | Audit events in a separate schema, from the start | ADR-024 |

---

## DoD Criterion

The walking skeleton counts as implemented when the scenario above runs through completely:

- `docker compose up` starts without errors
- Bootstrapping step completed
- OIDC login successful
- Starter package imported
- Entities created via batch API
- Catalog created, entities visible and sortable in the catalog table
- Cucumber scenarios of the skeleton acceptance criteria green in CI

**No canvas, no solutions, no joins, no filters, no dashboard** — that is the skeleton.

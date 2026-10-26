# Walking Skeleton – Definition

**As of**: 2026-10-22
**Status**: complete – project setup done in OpenSpec change `ws-01-project-setup` (2026-10-15); the six user stories are implemented in changes 2–7, the last one `us-049-show-catalog-data` (2026-10-22); the omitted acceptance criteria of US-141 and US-049 follow in the expansion sprints (revised before: story list updated, entities via batch API instead of form, placement in S1–S2; development environment and IdP per ADR-036, audit and migration setup per ADR-033)

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

- Development environment starts with one command (REQ-075, ADR-036): `compose.dev.yaml` with Compose profile `dev` – PostgreSQL, one-shot migration step, backend (Spring profile `dev`), Keycloak with the realm `oea-dev`, frontend; all ports bound to `127.0.0.1`
- Operator setup is safe by default (ADR-036, REQ-183): the operator file `compose.yaml` contains no IdP and no default secrets and refuses to start until every secret is set
- Persistence: Flyway migrations in a separate migration step with the owner role, application running with the runtime role (ADR-033 §1–§2); entity persistence with JSONB properties, metamodel query (ADR-015, ADR-016)
- Audit: audit events in the separate schema, append-only for the runtime role, hash-chained, pseudonymous actors (ADR-024, ADR-033)
- OIDC auth: authorization code flow with PKCE `S256` against a real IdP, token validation, role mapping via IdP group identifiers – no JWT stub or other authentication bypass (ADR-006, ADR-032, ADR-036)
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
| 4 | [US-141](../requirements/user-stories/US-141-entity-batch-import.md): Create entities via batch | – | 5 | AC1 (upsert of entities); connections, dryRun, and service account audit (AC2–AC4) follow later | Entity persistence (typed values, ADR-022), validation against metamodel, versioning, audit |
| 5 | [US-046](../requirements/user-stories/US-046-create-catalog.md): Create catalog | UC-06 | 3 | complete (incl. primary entity type) | Catalog configuration, metamodel API, role check |
| 6 | [US-049](../requirements/user-stories/US-049-show-catalog-data.md): Display catalog data | UC-06 | 5 | Table, sorting, pagination, empty state, view state in the URL ([ADR-044](../adrs/ADR-044-view-state-in-url.md)); default columns `name` and `status` as long as no columns are configured; joins and SavedViews follow later | Query API, table in the Vue frontend |

**Total: 31 story points** plus one-time project setup (Maven module `backend/`, Vue project `frontend/`, `api/openapi.yaml`, operator file `compose.yaml`, development file `compose.dev.yaml` with PostgreSQL, migration step and Keycloak realm `dev/keycloak/realm-oea-dev.json`, CI incl. the ADR-036 build checks). Planned for **sprint S1–S2**.

The omitted acceptance criteria of US-141 and US-049 are caught up in the expansion sprints; the stories only count as `done` then.

**Project setup – done** (archived change `openspec/changes/archive/2026-10-15-ws-01-project-setup/`; specs in `openspec/specs/platform/`): Java 25 + Spring Boot 4.1 backend (ADR-038, ADR-039), Vue 3 + Nuxt UI 4 frontend (ADR-040), provisioning script and `oea migrate` step (ADR-037), startup guards, Keycloak realm, CI jobs for backend, frontend and security checks. Start commands:

```bash
scripts/dev.sh                                        # development: docker compose -f compose.dev.yaml --profile dev up --build
cp .env.example .env && docker compose up             # operator: every secret required (manual/setup/installation.md)
```

Change 2 (`us-013-local-bootstrapping`) adds the local bootstrapping, the audit hash chain with the retention seed, `oea audit verify` and checkpoints. Change 3 is split (owner decision 2026-10-15): **3a** builds the session infrastructure, the login of the local system admin account with the setup phase of [ADR-042](../adrs/ADR-042-setup-phase-without-second-factor.md) (password only until the first access of another identity, then a second factor) and the administration of persons, identity bindings and IdP group mappings (US-013 AC2); **3b** adds the OIDC login of persons (US-001) with the issuer guards G1–G3 on the stored providers, the roles derived from IdP group mappings with re-evaluation within 15 minutes, and the development seed `scripts/dev_seed.py`.

---

## Walking Skeleton Scenario (End-to-End Flow)

```text
1. Developer starts the development environment (helper script / make target):
   docker compose -f compose.dev.yaml --profile dev up
   → PostgreSQL starts; the migrate service runs Flyway with the owner role
     (application and audit schema, roles per ADR-033) and exits
   → Keycloak starts and imports the realm "oea-dev"
     (issuer http://localhost:8180/realms/oea-dev)
   → backend (Spring profile "dev", runtime role) and frontend start

2. Admin opens browser: http://localhost:5173
   → Vue app loads

3. Admin reads the one-time setup token inside the backend container
   (docker compose exec backend cat /run/oea/setup-token) and calls /setup
   → US-013: enters the token, login name, password and instance-slug "acme-corp"
   → Result: local system admin account stored (argon2id); instance-slug = "acme-corp";
     audit event system.bootstrapped as the first chained record after the retention seed
   → The provider for the Keycloak dev realm, one person with an identity binding per
     test user and the group mappings are created through the admin API by
     scripts/dev_seed.py (change 3b, owner decision 2026-10-15: providers only in the
     database); the seed enrolls the admin's TOTP factor that the setup phase requires

4. Admin logs in (UC-01)
   → US-001: OIDC redirect to Keycloak (authorization code flow, PKCE S256)
     → test user of the dev realm → token → session
     (no token in localStorage/sessionStorage, REQ-165)
   → Admin lands on an empty start page

5. Metamodel administrator imports the starter package
   → a person with the role metamodel-admin (IdP group mapping; the local system admin
     account has no metamodel:write, ADR-032), e.g. dev-metamodel-admin with TOTP
   → US-033: start-page card "Import starter configuration" → oea-starter-togaf-classic
     (ADR-005; POST /admin/metamodel/starter-packages/oea-starter-togaf-classic/import)
   → Application Component, Data Object, Technology Component etc. are available as
     entity types with scope=imported, each with the property Status

6. Import script creates entities
   → a person with the role enterprise-architect (IdP group mapping, e.g. dev-architect);
     service accounts follow with US-142 (owner decision 2026-10-16)
   → US-141: POST /api/v1/entities/batch (strategy=upsert) with e.g. 3 ApplicationComponents
     and their Status, identified by externalId
   → "CRM System", "ERP", "Intranet" are in the repository in version 1; a second run
     reports them as unchanged; audit events entity.created written (hash-chained,
     pseudonymous actor reference)

7. Enterprise architect creates catalog
   → a person with catalog:manage (role enterprise-architect, e.g. dev-architect); a viewer
     can only create personal catalogs (ADR-032, owner decision 2026-10-19)
   → US-046: "Catalogs" → "New catalog" → name "Application Inventory", primary entity type
     Application Component, visibility Shared (POST /api/v1/catalogs)
   → the client opens the new catalog's page; the catalog is listed in the overview for
     every reader; audit event catalog.created

8. User opens the catalog
   → US-049: Table shows 3 rows with name | status (GET /api/v1/catalogs/{id}/query), sortable
     by every column (header click: ascending ↑ → descending ↓ → default order), paginated
     with 50 rows per page ("Page 1 of 3" for 120+ entities); sort and page are kept in the
     URL, so back navigation and reload restore the view (ADR-044); every reader with
     entity:read (e.g. dev-viewer) sees the shared catalog

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
| Deployment | Docker Compose; development: `docker compose -f compose.dev.yaml --profile dev up`; operator: `compose.yaml` with all secrets required | REQ-075, ADR-027, ADR-036 |
| Backend | Java 25 + Spring Boot 4.1 + Hibernate; Maven module `backend/`; layers api/app/core | ADR-012, ADR-038, ADR-039, ADR-027, ADR-028 |
| Database | PostgreSQL 15 + JSONB; Flyway migrations in a one-shot migration step with the owner role; application with the runtime role | ADR-015, ADR-016, ADR-033 |
| API | REST, JSON; OpenAPI 3.x spec-first (`api/openapi.yaml`) | ADR-013 |
| Auth | OIDC with PKCE `S256`; Keycloak dev realm in development and tests (no JWT stub), Entra ID / Authentik in production | ADR-006, ADR-036 |
| Frontend | Vue 3 + TypeScript + Nuxt UI 4 (TanStack Table) + Tailwind CSS 4 | ADR-011, ADR-014, ADR-040 |
| Tests | Cucumber JVM for the feature files of the skeleton REQs; PostgreSQL and Keycloak Testcontainers; tokens only through real login flows | ADR-029, ADR-036 |
| Entity IDs | Integer; instance-slug set during bootstrapping | ADR-001 |
| Metamodel | scope: built-in / imported / organization | ADR-002 |
| Starter package | oea-starter-togaf-classic as JSON import | ADR-005 |
| Audit | Audit events in a separate schema, from the start; owner/runtime role separation in the first migration; hash chain | ADR-024, ADR-033 |

### Repository Security Setup (Tasks for the First Backend Change)

Decided in `ws-01-project-setup` (design D9): GitHub's *Automatic dependency submission*, no own workflow. Because the setting watches only manifests in the repository root, [ADR-041](../adrs/ADR-041-root-aggregator-pom.md) adds a root aggregator `pom.xml` with the module `backend`. Original task list:

- **Dependency submission for Maven**: the GitHub dependency graph reads only the direct dependencies from `pom.xml`; transitive dependencies are known only after Maven resolves them. Either set *Settings → Advanced Security → Automatic dependency submission* to **Enabled** (GitHub-hosted runners), or add a dedicated, SHA-pinned workflow with the official Maven dependency submission action whose job alone has `contents: write` (REQ-191, REQ-192). Decide in the change's design; prefer the dedicated workflow if it keeps all workflows under version control and Dependabot updates.
- **Verify** that Dependabot alerts show transitive backend dependencies (e.g. Spring Framework modules pulled in by Spring Boot).
- Frontend and Electron need no dependency submission: `package-lock.json` already contains the full dependency tree.

Repository settings already expected before the walking skeleton (owner, *Settings → Advanced Security*): private vulnerability reporting, dependency graph, Dependabot alerts and secret scanning enabled; automatic dependency submission disabled until `backend/pom.xml` exists.

---

## DoD Criterion

The walking skeleton counts as implemented when the scenario above runs through completely:

- `docker compose -f compose.dev.yaml --profile dev up` starts without errors
- The provisioning step creates the database roles (ADR-037) and the first audit migration sets the owner/runtime grants; the application refuses to start if the runtime role owns audit objects or holds UPDATE/DELETE on them (ADR-033 §1, sign-off condition of the security review)
- Bootstrapping step completed
- OIDC login against the Keycloak dev realm with PKCE successful; no authentication stub in the production artifact (ADR-036 build checks green)
- Starter package imported
- Entities created via batch API
- Catalog created, entities visible and sortable in the catalog table
- Cucumber scenarios of the skeleton acceptance criteria green in CI
- Transitive backend dependencies visible in the GitHub dependency graph (dependency submission for Maven active)

**No canvas, no solutions, no joins, no filters, no dashboard** — that is the skeleton.

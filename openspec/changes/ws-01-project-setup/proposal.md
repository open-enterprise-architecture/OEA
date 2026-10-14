# Proposal

## Why

The walking skeleton (UC-06 catalog browser, [docs/walking-skeleton.md](../../../docs/walking-skeleton.md)) needs a runnable project before any user story can be implemented. This first change creates the code base, the development and operator environments and the safety rails that every later change relies on: database roles and migration step (ADR-033, ADR-037), a real development IdP without any authentication stub (ADR-036) and the CI checks that enforce them. It is the first of six walking-skeleton changes; it carries no user story of its own.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create and Use Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (walking skeleton; prerequisite setup only) |
| User Stories | – (project setup; the walking-skeleton stories follow in changes 2–6: US-013, US-001, US-033, US-141, US-046 + US-049) |
| Requirements | [REQ-075: Platform-independent deployment](../../../requirements/req/REQ-075-platform-independent-deployment.md), [REQ-156: PostgreSQL only](../../../requirements/req/REQ-156-postgresql-only-database.md), [REQ-151: Audit schema separation](../../../requirements/req/REQ-151-audit-schema-separation.md), [REQ-174: Audit tamper resistance](../../../requirements/req/REQ-174-audit-tamper-resistance.md) (AC1), [REQ-183: Production-safe defaults](../../../requirements/req/REQ-183-production-safe-defaults.md), [REQ-188: Secrets and key management](../../../requirements/req/REQ-188-secrets-key-management.md), [REQ-191: Supply chain integrity](../../../requirements/req/REQ-191-supply-chain-integrity.md), [REQ-192: CI least privilege](../../../requirements/req/REQ-192-ci-least-privilege.md) |
| ADRs | [ADR-011](../../../adrs/ADR-011-frontend-framework.md), [ADR-012](../../../adrs/ADR-012-backend-stack.md), [ADR-013](../../../adrs/ADR-013-api-style.md), [ADR-014](../../../adrs/ADR-014-frontend-component-library.md), [ADR-015](../../../adrs/ADR-015-db-migration.md), [ADR-027](../../../adrs/ADR-027-project-setup-monorepo.md), [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-029](../../../adrs/ADR-029-cucumber-test-framework.md), [ADR-030](../../../adrs/ADR-030-postgresql-only.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md), [ADR-037](../../../adrs/ADR-037-database-roles-provisioning.md) |
| Sprint / Milestone | S1 / Walking skeleton 1/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

The change name deviates from the `<us-id>-<short-name>` convention because the setup has no user story; the prefix `ws-01` marks it as walking-skeleton change 1 of 6.

## What Changes

- **Backend scaffold** `backend/`: single Maven module, Java 21, Spring Boot 3, packages `io.oea.{api,app,core,audit,integration,config}` (ADR-027, ADR-028); server stubs generated spec-first from `api/openapi.yaml` (ADR-013); health endpoint
- **Database setup**: provisioning script that creates the six roles of ADR-037; one-shot migration step `oea migrate` running Flyway for `db/migration`, `db/migration-audit` (as `oea_owner`) and `db/migration-reporting` (as `oea_reporting_owner`); first audit migration with the audit tables and the INSERT/SELECT-only grants for `oea_app` (ADR-033 §1–§2)
- **Startup checks**: PostgreSQL minimum version 15 (REQ-156 AC3); fail-closed audit role check (REQ-151 AC5); startup guards G4–G6 and the inverse guard for the Spring profile `dev` (ADR-036 §6, REQ-183)
- **Environments**: operator `compose.yaml` (all secrets required, no IdP) with `.env.example`; development `compose.dev.yaml` with Compose profile `dev` (PostgreSQL, migration step, Keycloak with realm `dev/keycloak/realm-oea-dev.json`, backend, Vite), ports bound to `127.0.0.1` (ADR-036 §3–§4)
- **Frontend scaffold** `frontend/`: Vue 3 + TypeScript + Vite + PrimeVue + Tailwind CSS, npm; API types generated from `api/openapi.yaml`; Storybook configured for screen stories (ADR-011, ADR-014, ADR-031)
- **Test foundation**: Cucumber JVM runner (ADR-029), PostgreSQL and Keycloak Testcontainers as shared singletons, test-scope helper for real authorization code flows with PKCE (ADR-036 §5)
- **Build and CI checks**: ArchUnit and Maven Enforcer rules against test-only authentication code, `scripts/check_production_artifact.py`, `scripts/check_compose_secrets.py`, known-development-secrets list with completeness check (ADR-036 §6–§7); new CI jobs for backend build and tests and frontend build, type check and Storybook build, each with read-only permissions and SHA-pinned actions (REQ-192)

## Out of Scope

- OIDC login, token validation and startup guards G1–G3 (issuer and signature rules) – change 3 (US-001), because they need the OIDC configuration
- Bootstrapping, first admin and instance slug – change 2 (US-013)
- Audit hash chain, checkpoints and the verification command (REQ-174 AC3, AC5) – change 2, together with the first audited action
- Reporting schema views and analytics (ADR-034) – after the walking skeleton
- Electron client (`frontend/electron/`), web portal entry point, mock API generation and the GitHub Pages / screenshot workflow (ADR-031) – with the first screen in change 6 (catalog) or later
- Helm chart and Kubernetes (REQ-075 AC3), object storage (AC5) – after the walking skeleton
- Maven dependency submission to the GitHub dependency graph – decided in this change's design, see tasks

## Capabilities

### New Capabilities

- `platform/deployment`: how OEA is started in development and by operators – one-command development environment, operator Compose file that refuses to start without explicit secrets, loopback-only development ports
- `platform/database-roles`: database role model, provisioning, separate migration step and the runtime role's restricted privileges on the audit schema, including the fail-closed startup check
- `platform/startup-safety`: startup checks that refuse unsafe configurations (PostgreSQL version, secrets, CORS, Swagger UI, development profile guard) and the guarantee that no test-only authentication code ships in the production artifact

### Modified Capabilities

- none (no specs exist yet)

## Impact

- New directories: `backend/`, `frontend/`, `dev/` (Keycloak realm, development backend configuration, development secrets), `db` provisioning script
- New root files: `compose.yaml`, `compose.dev.yaml`, `.env.example`; `.gitignore` extended (`.env`, build output, `node_modules/`)
- `api/openapi.yaml`: consumed by code generation in backend and frontend; no contract change
- CI: `.github/workflows/ci.yml` gets backend and frontend jobs and the new check scripts
- `docs/data-model.puml`: no change (audit tables already modeled per ADR-033); `business-objects/`: no change
- `docs/screens/`: no change

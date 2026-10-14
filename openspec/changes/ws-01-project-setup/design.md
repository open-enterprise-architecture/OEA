# Design

## Context

The repository contains requirements, ADRs, the data model and `api/openapi.yaml`, but no code (see proposal.md – Why). CI currently validates documents only. ADR-027 fixes the monorepo layout, ADR-033/ADR-037 the role model and migration step, ADR-036 the development IdP, startup guards and build checks. This design turns them into a concrete skeleton that later changes extend without restructuring.

## Goals / Non-Goals

**Goals:**

- A backend and a frontend that build, test and start in CI and locally with one command each
- The security rails of ADR-033, ADR-036 and ADR-037 active from the first commit, so later changes cannot accidentally ship without them
- Test infrastructure that later changes reuse unchanged (Testcontainers singletons, real login helper, Cucumber runner)

**Non-Goals:**

- Any business functionality, any user-facing screen beyond the Vue start page
- OIDC login and the issuer/signature guards G1–G3 (change 3), audit hash chain (change 2)

## Architecture Constraints

- ADR-012 / ADR-038: Java 21, Spring Boot 4.1, Hibernate, Maven
- ADR-027: monorepo; single Maven module `backend/`; base package `io.oea`; npm for `frontend/`
- ADR-028: packages `api` → `app` → `core`; `audit`, `integration`, `config` as technical packages
- ADR-013: spec-first – server interfaces and client types are generated from `api/openapi.yaml`, never the other way round
- ADR-030: PostgreSQL ≥ 15 only; Testcontainers, no H2
- ADR-033 / ADR-037: provisioning step creates roles; one-shot migration step with owner roles; runtime role INSERT/SELECT on audit tables; fail-closed check
- ADR-036: Keycloak dev realm, no authentication stub, startup guards, build-time checks, `compose.yaml` vs. `compose.dev.yaml`
- ADR-029: Cucumber JVM on the JUnit Platform
- ADR-031: Storybook for screen stories in `frontend/`
- REQ-192: CI jobs read-only, actions pinned to commit SHAs; no write token in jobs that run third-party code

## Decisions

### D1 – Migration step as a command of the backend image

`oea migrate` is a mode of the same Spring Boot application (non-web application context, Flyway API, exits after migrating) started by a `migrate` service in both Compose files with the owner credentials. The backend service depends on its successful completion.

- *Alternative*: the official Flyway container image. Rejected: a second image to pin and keep in sync with the migrations; migrations would leave the build artifact.
- The running application disables Flyway auto-migration and only validates that the schema history is current (spec: Application does not migrate).

### D2 – Provisioning via the PostgreSQL init mechanism

`deploy/postgres/initdb/10-oea-roles.sh` creates the six roles of ADR-037 and the schemas with their owners from environment variables. The official PostgreSQL image runs it once on an empty data volume. The same script is documented for operator-managed PostgreSQL (`psql` with variables). Passwords come only from the environment (operator: required variables; development: `*-dev-only` values).

### D3 – Startup checks as an environment post-processor plus a database check

- Configuration guards (secrets, CORS, Swagger UI, inverse `dev` guard) run before the web server starts, on the prepared environment only.
- Database checks (PostgreSQL version, audit privileges and ownership via `pg_roles`, `has_table_privilege`, `pg_tables.tableowner`) run after the datasource is available and before the web server accepts connections.
- Both report one error line per offending setting and never print secret values. They live in `io.oea.config` and have no profile condition except the explicit `dev` relaxations of ADR-036 §6.
- Details fixed during implementation: the configuration guards are an `EnvironmentPostProcessor` (`io.oea.config.startup.ConfigurationGuards`); secrets must have at least 16 characters (KEK rules follow with REQ-188); values containing `dev-only` or `test-only` count as development values; the CORS origins are configured in the new property `oea.security.cors.allowed-origins` (empty by default); the migration step sets `oea.mode=migrate`, so G4 checks the owner passwords there and `spring.datasource.password` in the application. The known-development-secrets list is read from `classpath*:security/known-dev-secrets.sha256`.

### D4 – Issuer configuration property introduced now

The inverse `dev` guard needs a property to inspect. This change introduces `oea.security.oidc.providers[*].issuer` (empty by default); change 3 uses the same property for login. Without configured issuers the inverse guard has nothing to check and passes.

### D5 – Code generation

- Backend: `openapi-generator-maven-plugin`, generator `spring`, `interfaceOnly`, `useSpringBoot4`, Jackson 3, into `target/generated-sources`; controllers implement the generated interfaces.
- Frontend: `openapi-typescript` generates types into `frontend/src/api/generated/` (not committed; generated in `prebuild`).

### D6 – Health endpoint in the contract

REQ-075 requires `GET /health`. It is added to `api/openapi.yaml` as a public operation (allowlist entry in `scripts/check_openapi_authorization.py`, returns only `{"status": "UP"}`) and implemented by mapping Spring Boot Actuator's health indicator without details. All other actuator endpoints stay disabled.

### D7 – Test infrastructure

- `io.oea.testsupport.containers`: static singletons for PostgreSQL (image of the minimum supported major version) and Keycloak (realm file mounted from `dev/keycloak/`), provisioning script and migration step applied once per JVM; `@DynamicPropertySource` provides URLs.
- `io.oea.testsupport.auth`: helper that performs the authorization code flow with PKCE against Keycloak headlessly (ADR-036 §5). It is created now and used from change 3 on.
- Cucumber runner `io.oea.steps.CucumberIT` with feature files from `backend/src/test/resources/features/` (requirements/tests stay source material).

### D8 – Container images pinned by digest

PostgreSQL, Keycloak, Node and JDK base images are referenced by tag and digest in `compose.yaml`, `compose.dev.yaml`, the backend `Dockerfile` and the Testcontainers configuration (REQ-191). Dependabot is extended to the `docker` ecosystem.

### D8a – Development ports

Development services bind to `127.0.0.1` with ports that avoid typical local services: PostgreSQL `55432`, Keycloak `8180`, backend `8080`, Vite `5173`. Container names use the Compose project name `oea`.

### D9 – Maven dependency submission: GitHub automatic submission

Transitive Maven dependencies reach the dependency graph through GitHub's *Automatic dependency submission* (setting **Enabled**), not through an own workflow. An own workflow would need `contents: write` in a job that resolves Maven dependencies – executing third-party code with a write token, which REQ-192 forbids. The repository owner switches the setting on when this change is merged; the change verifies that transitive dependencies appear.

### D10 – Frontend scaffold

Vite + Vue 3 + TypeScript (strict) + PrimeVue 4 + Tailwind CSS, `vue-tsc` type check, Vitest for unit tests, Storybook (Vue 3 + Vite) with the accessibility addon. Node 24 LTS. Only a start page and an empty `src/screens/` directory; no screen story yet, so `scripts/check_screens.py` stays green.

## Data Model

No change to `docs/data-model.puml` or `business-objects/`. Planned Flyway migrations:

- `db/migration/V1__baseline.sql` – empty baseline of schema `public` (owner `oea_owner`)
- `db/migration-audit/V1__audit_schema.sql` – tables `audit.events`, `audit.event_changes`, `audit.retention_settings` (seed 2555 days, CHECK ≥ 365), `audit.chain_head` as modeled; explicit grants (INSERT, SELECT for `oea_app`; SELECT for `oea_auditor`); `REVOKE ALL … FROM PUBLIC`. The hash-chain trigger and the purge function follow in change 2.
- `db/migration-reporting/V1__reporting_schema.sql` – empty schemas `reporting` and `reporting_ctx` (owner `oea_reporting_owner`)

## API

Delta to `api/openapi.yaml`: new public operation `GET /health` (operationId `getHealth`, tag `Operations`, response `{ "status": "UP" }`, `429` on rate limit), added to the public allowlist of `scripts/check_openapi_authorization.py`. No other contract change.

## Risks / Trade-offs

- [Testcontainers make the backend CI job slow (Keycloak start)] → singletons per JVM; measure the start time in this change (ADR-036 follow-up) and cache Maven dependencies
- [Init script runs only on an empty volume; existing development volumes keep old roles] → `make dev-reset` / documented `docker compose down -v`
- [Automatic dependency submission depends on a repository setting outside version control] → listed in the walking-skeleton document and verified in the wrap-up task
- [Generated server interfaces constrain controller signatures] → accepted; contract changes go through `api/openapi.yaml` first (ADR-013)

## Open Questions

- Exact Keycloak and PostgreSQL patch versions and digests – fixed when the images are pinned

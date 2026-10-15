# Tasks

## 1. Backend scaffold

- [x] 1.1 Create `backend/` as a single Maven module with Maven wrapper, Java 25 (ADR-039) and Spring Boot 4.1 (ADR-038), packages `io.oea.{api,app,core,audit,integration,config}` and an application class; verify `./mvnw -q verify` succeeds in `backend/`
- [x] 1.2 Add `GET /health` to `api/openapi.yaml` (public, tag `Operations`, `{ "status": "UP" }`, `429`) and `getHealth` to the public allowlist of `scripts/check_openapi_authorization.py`; verify Redocly lint and the authorization check pass
- [x] 1.3 Configure `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly`, `useSpringBoot4`, Jackson 3) to generate server interfaces from `api/openapi.yaml`; verify the build generates `OperationsApi` with `getHealth` (interfaces are grouped by tag) and compiles
- [x] 1.4 Implement `GET /health` (served as `/api/v1/health` under the contract's server base path) by aggregating the Actuator health indicators without details, all actuator endpoints unexposed; verify with a web slice test that `/health` returns 200 `{"status":"UP"}` (503 `DOWN` if an indicator is down) and with an integration test that `/actuator/env` returns 404
- [x] 1.5 Add ArchUnit layer rules for `api → app → core` (ADR-028); verify the architecture test passes and fails on a deliberately wrong dependency in a test fixture

## 2. Database roles, provisioning and migration step

- [x] 2.1 Write `deploy/postgres/initdb/10-oea-roles.sh` creating the six roles of ADR-037 and the schemas with their owners from environment variables; verify with a Testcontainers test that the roles exist without `SUPERUSER`, `CREATEROLE`, `CREATEDB`, `BYPASSRLS` and without mutual membership (spec: Role attributes after provisioning)
- [x] 2.2 Flyway migration `db/migration/V1__baseline.sql` (empty baseline of `public`, owner `oea_owner`); verify it is applied by the migration step
- [x] 2.3 Flyway migration `db/migration-audit/V1__audit_schema.sql`: `audit.events`, `audit.event_changes`, `audit.retention_settings` (CHECK ≥ 365; the seed row of 2555 days follows in change 2 with the hash-chain trigger, owner decision 2026-10-15), `audit.chain_head` per `docs/data-model.puml`, grants INSERT/SELECT for `oea_app`, SELECT for `oea_auditor`, `REVOKE ALL … FROM PUBLIC`; verify with a test that the table owner is `oea_owner`
- [x] 2.4 Flyway migration `db/migration-reporting/V1__reporting_schema.sql` (empty schemas `reporting`, `reporting_ctx`, owner `oea_reporting_owner`); verify the owner in a test
- [x] 2.5 Implement the `migrate` mode (non-web context, Flyway for the three locations with the respective owner credentials, exit code 0/1) and disable auto-migration in the application, which only validates the schema history; verify with Testcontainers tests for both scenarios of "Separate migration step"
- [x] 2.6 Write the Cucumber feature `platform/database-roles.feature` with the scenarios "Update rejected" and "Destructive statements rejected" (runtime role against `audit.events`); verify the scenarios pass in CI
- [x] 2.7 Document the provisioning script for operator-managed PostgreSQL in `docs/operations.md` (new); verify link validation passes

## 3. Startup checks

- [x] 3.1 Implement the PostgreSQL version check (minimum 15); verify with a PostgreSQL 14 Testcontainer that startup fails naming the minimum version (spec: PostgreSQL too old)
- [x] 3.2 Implement the fail-closed audit privilege and ownership check; verify with tests that grant UPDATE to `oea_app` resp. transfer ownership of an audit table to it and expect startup to fail naming the privilege or ownership
- [x] 3.3 Introduce the property `oea.security.oidc.providers[*].issuer` (empty by default) and the configuration guards: secrets (empty, too short, default, hash in the known-development-secrets list), CORS (`*`, `http`, loopback origins), Swagger UI, and the inverse `dev` guard (non-loopback issuer); verify with one startup test per scenario of "No unsafe settings outside development" and "Development profile only with loopback issuers", each asserting the property name and the absence of the secret value in the log
- [x] 3.4 Create the known-development-secrets list `backend/src/main/resources/security/known-dev-secrets.sha256` and `scripts/check_dev_secrets.py` that extracts secret literals from `compose.dev.yaml`, `dev/` and the realm file and fails on unlisted ones; verify the script passes and fails after adding an unlisted test literal (spec: New development secret without hash)

## 4. Environments

- [x] 4.1 Write the backend `Dockerfile` (multi-stage, JDK/JRE base images pinned by digest, non-root user, `migrate` and `serve` entry points); verify `docker build` succeeds and the container runs as non-root
- [x] 4.2 Write `compose.yaml` (PostgreSQL, `migrate`, backend; no IdP; every secret `${VAR:?…}`) and `.env.example` with empty values; add `.env` to `.gitignore`; verify `docker compose -f compose.yaml config` fails with an empty environment naming the missing variables (spec: Empty environment)
- [x] 4.3 Write `scripts/check_compose_secrets.py` (no literal secrets or `${VAR:-…}` defaults in `compose.yaml`, all published ports of `compose.dev.yaml` bound to `127.0.0.1`, every development service with `profiles: ["dev"]`); verify it passes and fails on seeded violations
- [x] 4.4 Write `dev/keycloak/realm-oea-dev.json` with the clients, groups (fixed UUIDs, `groups` claim as identifiers), test users and TOTP secrets of ADR-036 §4; verify Keycloak imports it and a discovery request to `http://localhost:8180/realms/oea-dev/.well-known/openid-configuration` returns 200
- [x] 4.5 Write `compose.dev.yaml` (profile `dev`: PostgreSQL with `*-dev-only` credentials and the init script, `migrate`, Keycloak with realm import, backend with Spring profile `dev` and `dev/backend/application-dev.yml`, Vite), ports on `127.0.0.1`, images pinned by digest, plus a helper target (`make dev` / `scripts/dev.sh`); verify the scenarios "Development environment starts" and "Development services require the profile" manually and record the result in the PR
- [x] 4.6 Verify the scenario "All secrets supplied": with a filled `.env`, `docker compose up` migrates, starts and `GET /health` returns 200 within 60 seconds (CI job using Compose)

## 5. Test foundation

- [x] 5.1 Create `io.oea.testsupport.containers` with PostgreSQL (minimum supported major version) and Keycloak singletons, provisioning and migration applied once per JVM; verify an integration test starts the application against them
- [x] 5.2 Create the test-scope login helper `io.oea.testsupport.auth` (authorization code flow with PKCE against Keycloak, headless); verify a test obtains a token for `dev-viewer` and the token's `groups` claim contains the group UUID
- [x] 5.3 Set up the Cucumber runner `io.oea.steps.CucumberIT` on the JUnit Platform with features from `backend/src/test/resources/features/`; verify the database-roles feature of 2.6 runs through it

## 6. Build-time checks against test-only authentication code

- [x] 6.1 Add ArchUnit rules: main classes must not depend on Testcontainers, Keycloak, `io.oea.testsupport..` or `spring-security-test`, no main class name matching the stub pattern, no profile-conditional security bean; integration tests must not use mock authentication; verify the rules pass and fail on fixtures (spec: Stub class in main code)
- [x] 6.2 Add Maven Enforcer `bannedDependencies` (Testcontainers, Keycloak, mock OAuth servers, WireMock, `spring-security-test` not in compile/runtime scope); verify the build fails when such a dependency is added with compile scope
- [x] 6.3 Write `scripts/check_production_artifact.py` inspecting the Spring Boot jar and the image for forbidden classes, banned libraries, `application-dev*` and realm files; verify it passes on the real artifact and fails on a seeded one (spec: Production artifact inspection)

## 7. Frontend scaffold

- [x] 7.1 Create `frontend/` with Vite, Vue 3, TypeScript (strict), Nuxt UI 4 (ADR-040), Tailwind CSS 4, Vitest and npm; a start page only; verify `npm ci`, `npm run type-check`, `npm run test` and `npm run build` succeed
- [x] 7.2 Generate API types from `api/openapi.yaml` with `openapi-typescript` in `prebuild` (output not committed); verify the build fails if the spec is invalid
- [x] 7.3 Configure Storybook (Vue 3 + Vite, accessibility addon) with an empty `src/screens/`; verify `npm run build-storybook` succeeds and `python3 scripts/check_screens.py` stays green

## 8. CI

- [x] 8.1 Add the job "Backend build and tests" (`./mvnw verify` incl. Testcontainers, ArchUnit, Enforcer, Cucumber) with `contents: read`, SHA-pinned actions and Maven cache; verify it runs green on the PR
- [x] 8.2 Add the job "Frontend build" (type check, unit tests, build, Storybook build); verify it runs green on the PR
- [x] 8.3 Add the job "Security checks" running `check_compose_secrets.py`, `check_dev_secrets.py` and, after packaging, `check_production_artifact.py`; verify it runs green on the PR; the seeded violations of 4.3, 3.4 and 6.3 were verified locally with the same scripts (CI runs only for pull requests, so no separate test branch)
- [x] 8.4 Extend `.github/dependabot.yml` with the `maven`, `npm` and `docker` ecosystems; verify the YAML is valid
- [x] 8.5 Measure and record the Keycloak and PostgreSQL Testcontainers start times in CI (ADR-036 follow-up); verify the numbers are noted in the PR description

## 9. Wrap-up

- [ ] 9.1 Ask the repository owner to set *Automatic dependency submission* to **Enabled** (design D9) and verify transitive backend dependencies (e.g. Spring Framework modules) appear in the dependency graph
  - Result (2026-10-15, after #24 changed `backend/pom.xml`): no automatic submission ran; only direct Maven dependencies appear. The setting covers only a root `pom.xml`; D9 needs a follow-up decision
- [x] 9.2 Update `docs/walking-skeleton.md` (setup done, actual start commands) and `README.md` (how to start development); no user story status changes (this change has no user story); `docs/screens/SCREENS.md` and `docs/data-model.puml` unchanged
- [x] 9.3 Regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [x] 9.4 Run `python3 scripts/validate_links.py` and the markdown lint and verify both pass

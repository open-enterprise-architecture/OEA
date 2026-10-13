# ADR-036: Development and Test Identity Provider Without Authentication Stub

**Status**: accepted
**Date**: 2026-10-13
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer (security review 2026-10, SEC-20, SEC-24, SEC-26), Requirements Engineer (REQ-183, REQ-166, REQ-188)
**Informed**: Backend Engineer, Frontend Engineer
**Partially supersedes**: [ADR-027](./ADR-027-project-setup-monorepo.md) (§4 "Local Development: Docker Compose": the simplified JWT stub in dev mode, Keycloak only as optional Compose profile for integration tests, and the root `docker-compose.yml` as development environment started with the backend profile `dev`) – see [Relation to Existing ADRs](#relation-to-existing-adrs)

## Context and Problem

[ADR-027](./ADR-027-project-setup-monorepo.md) §4 decided that OIDC authentication (ADR-006) "is bridged in dev mode with a simplified JWT stub that requires no external OIDC provider locally"; Keycloak was only an optional Compose service for integration tests (`--profile keycloak`). The root `docker-compose.yml` was the development environment and started the backend with the profile `dev`.

The security review of the requirements baseline (`security-reviews/requirements-baseline-2026-10.md`) rated this as **SEC-20 (High)**: an operator who copies the repository's Compose file to a server – the path REQ-075 and REQ-156 AC1 describe – runs the backend in the dev profile, the stub accepts any token or a token signed with a well-known static key, and default database credentials from the Compose file are reused in production. Two related findings affect the same files:

- **SEC-24**: no HTTP security baseline; a typical shortcut for local development (wildcard CORS, relaxed cookies) leaks into production when it is profile-conditional.
- **SEC-26**: secrets and key management undefined; secrets placed as defaults in a Compose file or `.env` end up in backups and copies.

The owner decided on 2026-10-13 (Q11): **no JWT stub at all – a real IdP container in development and tests**, and PKCE is mandatory for every OIDC client. The resulting requirements are:

- [REQ-183](../requirements/req/REQ-183-production-safe-defaults.md): no authentication stub or bypass; real OIDC IdP container in the development/test Compose profile and in integration tests; no test-only authentication classes in the production artifact (build-time check); startup refused outside the development profile if signature verification is disabled, the issuer is a localhost address or default secrets are configured; the operator Compose file requires all secrets explicitly.
- [REQ-166](../requirements/req/REQ-166-oidc-hardening-identity-binding.md): authorization code flow with PKCE (`S256`) for every client, full ID token validation, binding of a person to (`iss`, `sub`); verification "against a mock IdP with crafted tokens".
- [REQ-188](../requirements/req/REQ-188-secrets-key-management.md): application secrets encrypted with a key-encryption key (KEK) supplied from outside the database; the KEK is never part of a database backup.
- [REQ-156](../requirements/req/REQ-156-postgresql-only-database.md): integration and Cucumber tests run against a real PostgreSQL Testcontainer; the same Testcontainers infrastructure is available for an IdP container.

The [walking skeleton](../docs/walking-skeleton.md) (S1–S2) already plans "Docker Compose with PostgreSQL and Keycloak" and implements OIDC login ([US-001](../requirements/user-stories/US-001-oidc-login.md)) and local bootstrapping first. The review requires SEC-20 to be resolved before that implementation starts. This ADR decides which IdP is used in development and tests, how it is provisioned, and which mechanisms keep any development shortcut out of production.

## Decision Drivers

- **No bypass by construction**: the application has exactly one authentication path – standards-conformant OIDC token validation against the JWKS of a configured issuer. Nothing in the production artifact can accept a token that was not signed by a configured IdP (REQ-183).
- **Realism of claims and flows**: tests must exercise what production relies on – authorization code flow with PKCE `S256` (REQ-166), `iss`/`aud`/`azp`/`nonce` validation, group claims mapped to authorization roles via immutable identifiers (ADR-032, REQ-167), `amr`/`acr` for MFA of privileged roles (ADR-032 SoD-8, REQ-171), refresh and revocation behavior (REQ-165).
- **ADR-006 obligations**: Entra ID and Authentik are the tested reference integrations; the implementation is a generic, RFC-conformant OIDC client. The dev/test IdP should strengthen, not weaken, that principle.
- **Startup time and footprint in Testcontainers**: integration and Cucumber tests run on every pull request (ADR-029, REQ-156); the IdP must start fast and as a single, reusable container.
- **Provisioning as code**: realm, clients, test users, groups and MFA credentials must be versioned in the repository and reproducible in developer machines, CI and Testcontainers.
- **Licensing**: the IdP is a development and test tool, not distributed with OEA; its license must allow unrestricted use in an AGPL-3.0 project's development and CI.
- **Maintenance**: one provisioning definition shared by local development and tests; upgrades of the IdP must not require code changes in OEA.
- **Safe operator path**: the Compose file an operator finds first must be the safe one; secrets are never defaulted (SEC-20, SEC-26).

## Considered Options

### Option (a): Keycloak container for development and tests

Keycloak (Apache-2.0, CNCF project) runs as a single container in `start-dev` mode with an embedded store and imports a versioned realm file at startup (`--import-realm`). Testcontainers uses the community module `testcontainers-keycloak` (Apache-2.0) with the same realm file.

- **Pro**:
  - Single container, no external database needed in dev mode; starts within seconds to a few tens of seconds including realm import (to be measured in the first change)
  - Realm, clients, groups (with fixed IDs), users and OTP credentials are fully declarative in one JSON file; the import is idempotent per container start
  - Enforces PKCE per client (`pkce.code.challenge.method = S256`), supports loopback redirect URIs for native clients (RFC 8252), refresh token rotation, token revocation, back-channel logout, `amr` and `acr` (level-of-authentication) mappings
  - Mature Testcontainers module, widely used with Spring Security; Admin REST API allows tests to change group memberships and disable users (REQ-164, REQ-165 scenarios)
  - The walking skeleton already plans Keycloak; ADR-027 already named it for integration tests
  - Testing daily against a provider that is **not** a reference integration guards the "RFC conformance instead of provider specifics" principle of ADR-006
- **Con**:
  - Not one of the two reference integrations of ADR-006; Authentik-specific behavior (claim formats, discovery details) is not covered by the daily tests
  - No built-in mapper that emits group IDs (only group names or paths) – immutable group identifiers (REQ-167) need a configured attribute mapper in the realm
  - Realm export format changes between major versions; realm files must be migrated on upgrades

### Option (b): Authentik container for development and tests

Authentik (open-source core under MIT; enterprise features under a separate license) is already a mandatory reference integration per ADR-006. It is provisioned via blueprints (YAML).

- **Pro**:
  - Every test run exercises a reference integration of ADR-006
  - Blueprints provide provisioning as code; groups have UUIDs, which match the immutable identifier rule of REQ-167 directly
  - Supports PKCE, refresh tokens, `amr`, MFA stages; property mappings allow exact control of claims
- **Con**:
  - Needs several containers (server, worker, its own PostgreSQL and further backing services depending on the version); first start runs database migrations – noticeably slower and heavier than a single Keycloak container on every pull request
  - No established Testcontainers module; a Compose-based or custom container setup has to be maintained by OEA
  - Claims for group UUIDs and `amr` require custom property mappings (Python expressions) that are themselves code to maintain
  - Couples the daily tests to one provider; drift towards Authentik-specific behavior would go unnoticed

### Option (c): Lightweight OIDC mock server used only in tests (e.g. `mock-oauth2-server`)

A mock authorization server (e.g. NAV's `mock-oauth2-server`, MIT) issues tokens for arbitrary subjects and claims on request; local development uses something else (stub or real IdP).

- **Pro**:
  - Very fast startup (a few seconds), tiny footprint
  - Arbitrary claims per test (`groups`, `amr`, `acr`, expired tokens) without provisioning
  - Real JWKS and signatures; the application code path is the same as in production
- **Con**:
  - Issues tokens for any subject without credential check – tests do not exercise login, MFA, PKCE enforcement by the IdP, refresh rotation or revocation the way a real IdP behaves
  - Claims are whatever the test asks for; tests can pass with claim shapes no real IdP produces (false confidence on group mapping and `amr`)
  - Development and tests would use different IdPs, contradicting the owner decision (Q11) and REQ-183 AC4 ("real OIDC IdP container")
  - A permissive token issuer is exactly the kind of component that must never be reachable from a deployed instance; having it in the tool chain invites reuse as a "quick dev login"

### Option (d): In-application JWT stub behind a profile flag (status quo of ADR-027)

The backend contains a stub that issues or accepts tokens signed with a static key when a profile (`dev`) is active.

- **Pro**:
  - Zero external dependencies; fastest local start
  - Trivial to write tests with arbitrary roles
- **Con**:
  - The bypass ships in the production artifact; one wrong profile variable (`SPRING_PROFILES_ACTIVE=dev`) turns it on (SEC-20 attack scenario)
  - The static signing key is public (it is in the repository)
  - No test of the real login flow, PKCE, `amr`/`acr`, refresh or revocation before the first deployment against a real IdP
  - Security configuration becomes profile-conditional – the pattern that also produces wildcard CORS and relaxed cookies in production (SEC-24)
  - Explicitly rejected by the owner (Q11) and forbidden by REQ-183

## Decision

We choose **Option (a): Keycloak as the single development and test IdP**, supplemented by an **Authentik reference-integration suite** that runs nightly and before every release. There is **no authentication stub or bypass anywhere** – not in production code, not behind a profile, not as a mock token issuer in development. Keycloak gives every pull request a real, fast, fully code-provisioned IdP that enforces PKCE and produces realistic tokens, while the Authentik suite keeps the ADR-006 obligation for a tested reference integration without making every test run pay for Authentik's heavier setup. Option (b) as the only IdP fails on startup cost and maintenance per pull request; option (c) contradicts Q11 and tests claim shapes rather than IdP behavior; option (d) is the vulnerability SEC-20 describes.

### 1. One authentication path in all environments

- The backend has one security configuration. **No security bean is conditional on a Spring profile** (`@Profile`, `@ConditionalOnProperty` on authentication beans): `SecurityFilterChain`, `JwtDecoder`, `AuthenticationProvider`, session handling and the OIDC client are identical in development, test and production. Environments differ only in **property values** (issuer, client IDs, allowed CORS origins, secrets).
- Tokens are accepted only after signature verification against the JWKS of a configured issuer, with an allowlist of asymmetric algorithms (RS256/PS256/ES256 families; never `none`, never `HS*` with a JWKS) and validation of `iss`, `aud`, `exp`, `nbf`, `nonce`, `azp` (REQ-166). OEA has **no configuration switch** that disables signature verification.
- PKCE `S256` is used and enforced in development exactly as in production: the Keycloak clients require it, and OEA's own client side always sends it.

### 2. Profiles

| Profile | Where | Meaning |
|---|---|---|
| Spring profile `dev` | Local development (dev Compose profile) and all automated tests that start the application | The only non-production profile. Allows loopback issuers over HTTP and the known development secrets; enables Swagger UI (ADR-013). Changes no security bean. |
| No profile / any other profile | Production and every operator deployment | Production rules: startup guards active, Swagger UI off |
| Compose profile `dev` | `compose.dev.yaml` | Every service of the development file carries `profiles: ["dev"]`; nothing starts without `--profile dev` |
| Compose profile `authentik` | `compose.dev.yaml` | Optional Authentik stack for local work on the reference integration |

The `dev` Spring profile has an **inverse guard**: when `dev` is active, every configured issuer **must** be a loopback address. A production instance accidentally started with `SPRING_PROFILES_ACTIVE=dev` and a real IdP therefore refuses to start instead of silently relaxing rules. `dev` cannot be combined with a profile named `prod`.

Development configuration (`application-dev.yml`) is **not** part of `backend/src/main/resources`: it lives in `dev/backend/` (mounted by `compose.dev.yaml`) and the tests set their values from `src/test/resources` and `@DynamicPropertySource` (issuer URL of the Testcontainer).

### 3. Compose files: operator vs. development

| File | Purpose | Secrets | Ports |
|---|---|---|---|
| `compose.yaml` (repository root) | **Operator file**: PostgreSQL, OEA backend image (production rules), web portal assets. No IdP service – operators connect Entra ID, Authentik or another OIDC provider (ADR-006). | Every secret as `${VAR:?<message>}`; no `${VAR:-default}` and no literal secret anywhere; the KEK (REQ-188) and database passwords preferably as Compose `secrets:` from files outside the repository; `.env.example` lists all variables with **empty** values | As configured by the operator (behind a TLS reverse proxy) |
| `compose.dev.yaml` | **Development file** (Compose profile `dev`): PostgreSQL with development credentials, Keycloak with realm import, backend built from source with Spring profile `dev`, Vite dev server; optional profile `authentik` | Fixed, clearly marked development values (`*-dev-only`), all registered in the known-development-secrets list (§6) | Every published port bound to `127.0.0.1` |

Start commands:

```bash
# development (wrapped by a helper script / make target)
docker compose -f compose.dev.yaml --profile dev up

# operator
cp .env.example .env   # fill in every value
docker compose up      # fails if any required secret is unset
```

The file that an operator finds first and copies is the safe one. CI checks both files (§7).

Keycloak runs in `start-dev` mode with its embedded store; this store is internal to the IdP container and is not OEA application data, so REQ-156 (PostgreSQL only) is not affected. Keycloak is configured with `KC_HOSTNAME=http://localhost:8180` and a dynamic back channel, so the issuer seen by browsers and validated by the backend is `http://localhost:8180/realms/oea-dev`, while the backend container fetches discovery and JWKS via the Compose service name.

### 4. Realm and test users as code

The realm definition `dev/keycloak/realm-oea-dev.json` is the single source for local development and Testcontainers. It contains:

#### Clients

| Client | Type | Purpose |
|---|---|---|
| `oea-portal` | as decided by the login design of REQ-001/REQ-165 (public or confidential) | Web portal login; PKCE `S256` required; redirect URIs only `http://localhost:5173/*` and the backend callback on `http://localhost:8080` |
| `oea-electron` | public | Electron client per RFC 8252: PKCE `S256` required, loopback redirect `http://127.0.0.1/*` (any port) |
| `oea-test-foreign-app` | confidential | A second application of the same IdP; its tokens carry a different `aud` and must be rejected (REQ-166 AC2) |

No client enables the resource owner password grant or the implicit flow.

**Groups** (fixed UUIDs in the realm file, emitted as identifiers – not names – in the `groups` claim via a multivalued attribute mapper, so that IdP group mappings use immutable identifiers as required by REQ-167 and ADR-032 §7)

| Group | Mapped to authorization role (ADR-032) |
|---|---|
| `oea-portal-readers` | `portal-reader` |
| `oea-viewers` | `viewer` |
| `oea-analysts` | `analyst` |
| `oea-enterprise-architects` | `enterprise-architect` |
| `oea-auditors` | `auditor` |
| `oea-metamodel-admins` | `metamodel-admin` |
| `oea-security-admins` | `security-admin` |
| `oea-system-admins` | `system-admin` |

`integration-writer` has no group: it is meant for service accounts, which authenticate with OEA's own client credentials endpoint (REQ-176) and cannot be targets of IdP group mappings (ADR-032 SoD-4).

**Test users** (all passwords and OTP secrets are development values registered in §6)

| User | Groups | MFA | Purpose |
|---|---|---|---|
| `dev-portal-reader` | `oea-portal-readers` | – | Portal access only |
| `dev-viewer` | `oea-viewers` | – | Read-only repository access |
| `dev-analyst` | `oea-analysts` | – | Raw analytics (REQ-179) |
| `dev-architect` | `oea-enterprise-architects` | – | Modeling, entity writes, catalogs |
| `dev-auditor` | `oea-auditors` | – | Audit log |
| `dev-metamodel-admin` | `oea-metamodel-admins` | TOTP | Privileged role with MFA (`amr` contains `otp`) |
| `dev-security-admin` | `oea-security-admins` | TOTP | Privileged role with MFA |
| `dev-system-admin` | `oea-system-admins` | TOTP | Privileged role with MFA; remote system admin mapping (REQ-167) |
| `dev-admin-no-mfa` | `oea-system-admins` | – | Privileged role without second factor: privileged permissions must not become effective (ADR-032 SoD-8) |
| `dev-architect-metamodel` | `oea-enterprise-architects`, `oea-metamodel-admins` | TOTP | Combined holdings without SoD conflict |
| `dev-sod-combined` | `oea-metamodel-admins`, `oea-security-admins` | TOTP | SoD-3 flagged combination |
| `dev-no-role` | – | – | Authenticated without role (REQ-003) |
| `dev-email-twin` | – | – | Same verified e-mail as `dev-architect`, different `sub` (REQ-166 AC1) |
| `dev-email-unverified` | – | – | `email_verified = false` (REQ-166 AC4) |

TOTP secrets are fixed in the realm file so that tests can compute valid codes. `amr` and `acr` are emitted through Keycloak's authentication-method-reference and level-of-authentication mappings. The OEA-side mapping from group identifiers to roles for the dev instance is provisioned by the test setup and by a development seed, never by code paths that exist only in the dev profile.

**Authentik reference suite**: `dev/authentik/blueprints/oea-dev.yaml` provisions the same groups and users (group UUIDs, property mapping for `groups` as UUIDs and for `amr`). The suite reuses the Cucumber scenarios tagged for login, group mapping and MFA and runs nightly and before every release; it can be started locally with the Compose profile `authentik`.

### 5. Tests

- **Integration and Cucumber tests** (ADR-029) start one Keycloak Testcontainer per test JVM (static singleton, same realm file as development) next to the PostgreSQL Testcontainer (REQ-156). The issuer URL is injected via `@DynamicPropertySource`. Local developers may enable Testcontainers reuse; CI never does.
- **Tokens are obtained only through real flows**: a test-scope helper (`backend/src/test/java/io/oea/testsupport/auth/`) performs the complete authorization code flow with PKCE against Keycloak headlessly (HTTP client following redirects and submitting the login and OTP forms) and returns the resulting OEA session or token. Tokens are cached per test user for the run. Browser end-to-end tests (Playwright) log in through the real login page.
- **Negative token tests** (REQ-166 AC1–AC4) use real IdP behavior wherever possible: wrong `aud` via `oea-test-foreign-app`; changed `sub` with matching e-mail via `dev-email-twin`; expiry via the injectable application `Clock`; `nonce` mismatch by manipulating the authorization request in the helper. Tokens with `alg: none` or `HS256` are crafted in test code; they are attack inputs that the application must reject, signed with no key the application trusts. No test configures the application to trust a key other than the IdP's JWKS.
- **IdP-side state changes** (removing group membership for REQ-165, disabling a user, revoking sessions for REQ-164) are performed through the Keycloak Admin REST API from test code on dedicated users.
- **No mock authentication in system-level tests**: `spring-security-test` mock principals (`@WithMockUser`, `jwt()` and `oidcLogin()` request post-processors) are allowed only in unit tests that do not start the security filter chain. Integration tests, the generated authorization matrix test (ADR-032 §8) and Cucumber step definitions must authenticate through the IdP; an ArchUnit rule on the test sources enforces this for `@SpringBootTest` classes and the package `io.oea.steps`.

### 6. Startup guards

A startup guard in `io.oea.config` runs after the environment is prepared and **before the web server accepts connections**. On violation the application logs one error per offending setting (property name, never the secret value) and exits with a non-zero status.

**Outside the Spring profile `dev`** startup fails if any of the following holds:

| # | Condition |
|---|---|
| G1 | Signature verification is not in effect for a configured OIDC provider: no issuer/JWKS source configured, or the allowed algorithm set contains `none` or any `HS*` algorithm |
| G2 | A configured issuer (static configuration or stored provider, ADR-006) is a localhost address: host `localhost` or `*.localhost`, any address in `127.0.0.0/8`, `::1`, `0.0.0.0`, or a host name that resolves only to loopback addresses |
| G3 | A configured issuer does not use `https` |
| G4 | A secret property (database passwords incl. the migration owner role of ADR-024, KEK of REQ-188, OIDC client secrets, any property flagged as secret in the configuration metadata) is empty, shorter than its minimum length (KEK: 256 bit), contained in the list of generic weak values (`changeme`, `change-me`, `password`, `secret`, `admin`, `postgres`, `oea`, …), or matches the known-development-secrets list |
| G5 | The CORS allowed origins contain `*`, an `http` origin or a loopback origin (SEC-24, REQ-187) |
| G6 | Swagger UI or the OpenAPI UI endpoint is enabled (ADR-013, REQ-183 AC3) |

**With the Spring profile `dev`** startup fails if any configured issuer is **not** a loopback address (inverse guard), and a prominent warning is logged at startup.

The same issuer rules G2/G3 are applied when an authentication provider is saved through the admin API outside `dev`, so a provider stored at runtime cannot reintroduce a localhost issuer.

The **known-development-secrets list** is a resource in the production artifact that contains only SHA-256 hashes of every secret value used in `compose.dev.yaml`, `dev/` and the realm/blueprint files. A CI script extracts all secret literals from these files and fails if one is missing from the list, so a new development secret cannot be forgotten.

### 7. Build-time and CI checks

| Check | Mechanism | Fails when |
|---|---|---|
| ArchUnit (main classes) | Test in `backend/src/test/java/io/oea/architecture/` | A main class depends on `org.testcontainers..`, Keycloak libraries, `io.oea.testsupport..` or `org.springframework.security.test..`; a class name matches `(Stub\|Fake\|Mock\|Dummy\|Test)(Jwt\|Token\|Auth\|Idp\|Issuer\|Login)`; a bean of type `SecurityFilterChain`, `JwtDecoder`, `AuthenticationProvider`, `AuthenticationManager` or `OAuth2AuthorizedClientService` is profile-conditional |
| ArchUnit (test classes) | Same module | `@SpringBootTest` classes or `io.oea.steps` use mock authentication from `spring-security-test` (§5) |
| Maven Enforcer | `bannedDependencies` in `backend/pom.xml` | Testcontainers, Keycloak, `mock-oauth2-server`, WireMock or `spring-security-test` appear in `compile` or `runtime` scope |
| Artifact inspection | `scripts/check_production_artifact.py` on the Spring Boot jar and the container image | The artifact contains classes matching the forbidden patterns, banned libraries in `BOOT-INF/lib/`, `application-dev*` files, realm/blueprint files, private keys (`*.pem`, `*.key`, `*.jks`, `*.p12`) or any known development secret value |
| Compose lint | `scripts/check_compose_secrets.py` | `compose.yaml` contains a literal secret or a `${VAR:-…}`/`${VAR-…}` default for a secret variable; `docker compose -f compose.yaml config` succeeds with an empty environment; a port in `compose.dev.yaml` is not bound to `127.0.0.1`; a service in `compose.dev.yaml` lacks the `dev` Compose profile |
| Startup tests | Integration tests starting the application without `dev` | Any of G1–G6 does not abort startup with the expected message (REQ-183 AC2) |

All checks run on every pull request. The Authentik reference suite runs nightly and as a release gate.

### 8. Secrets: development vs. production

| Aspect | Development (`compose.dev.yaml`, tests) | Production (`compose.yaml`, other deployments) |
|---|---|---|
| Database passwords | Fixed values marked `*-dev-only`, in the repository, hash-listed | Required variables (`${VAR:?}`) or Compose/Kubernetes secrets; never defaulted |
| KEK (REQ-188) | Fixed development KEK file in `dev/secrets/`, hash-listed | File secret mounted from outside the repository and outside the database backup path; rotation per REQ-188 |
| OIDC client secrets | Development values of the realm file, hash-listed (public clients have none) | Configured by the operator; stored encrypted (REQ-188); never returned by the API |
| IdP | Keycloak dev realm on `localhost`, HTTP | Operator's IdP over HTTPS (Entra ID, Authentik or another OIDC provider) |
| Exposure | Ports bound to `127.0.0.1` only | Behind a TLS reverse proxy |
| `.env` | Not needed | `.env.example` with empty values; `.env` is in `.gitignore` |

Development secrets are deliberately public and therefore worthless outside the loopback-bound development setup; the guards make sure they can never be used by a production instance.

## Consequences

### Positive Consequences

- SEC-20 is resolved by construction: the production artifact contains no bypass, a wrong profile cannot create one, and the operator Compose file cannot start with default secrets.
- The first OIDC login of the walking skeleton (US-001) is developed and tested against a real IdP with PKCE, group claims and MFA claims from day one; integration problems surface in pull requests, not at the first customer installation.
- One realm file serves developers, CI and Testcontainers; adding a test user or group is a reviewed change.
- The security configuration is identical in all environments, which also prevents profile-conditional CORS and cookie shortcuts (SEC-24).
- The Authentik suite keeps the ADR-006 reference integration continuously tested, while daily tests against Keycloak guard the standards-conformant client design.
- Secret handling is explicit and checkable (SEC-26): development secrets are known and rejected in production, production secrets are never defaulted.

### Negative Consequences / Trade-offs

- Local development and every test run need Docker and a running Keycloak container; the first test run of a JVM pays the IdP startup time (in addition to PostgreSQL).
- Tests that need a token are slower than tests with mocked principals; the headless login helper is test code that has to follow changes of the Keycloak login pages.
- Two provisioning definitions (Keycloak realm, Authentik blueprint) must be kept in sync; Keycloak and Authentik upgrades can require realm/blueprint migrations.
- Keycloak's lack of a built-in group-ID mapper means the dev realm emits group identifiers through a configured attribute mapper – realistic in format, but not the way a default Keycloak installation behaves.
- Entra ID cannot be containerized; its reference integration remains a manual or tenant-based test outside this decision.
- The developer start command changes from `docker compose up` to `docker compose -f compose.dev.yaml --profile dev up` (wrapped by a helper).
- HTTP on `localhost` in development relies on browsers treating `http://localhost` as a secure context for `Secure`/`__Host-` cookies; if a client or browser does not, local TLS is needed.

### Follow-up Decisions

- Test strategy for the Entra ID reference integration (dedicated test tenant, manual release checklist or scheduled job with secrets in CI) (pending)
- Login design for the web portal (confidential backend-for-frontend vs. public client) determines the final type of the `oea-portal` client (solution design for US-001, REQ-001/REQ-165)
- Image pinning by digest and update automation for the Keycloak and Authentik images, as part of supply chain integrity (REQ-191)
- Whether the identifier-format validation of REQ-167 gets a "generic OIDC" provider profile that accepts the dev realm's group identifiers, or whether the dev instance is configured as provider type `authentik`-compatible (solution design)

## Relation to Existing ADRs

| ADR | Relation |
|---|---|
| [ADR-027](./ADR-027-project-setup-monorepo.md) | **Partially superseded** (§4 "Local Development: Docker Compose"): the paragraph "No Keycloak in the compose for the Walking Skeleton … simplified JWT stub … Keycloak as an optional compose service (`--profile keycloak`)" is replaced by §1–§5 of this ADR; the root `docker-compose.yml` with `api` in profile `dev` as "local development environment" is replaced by the operator file `compose.yaml` and the development file `compose.dev.yaml` (§3). The positive consequence "first `git clone` + `docker compose up` = runnable development environment" now reads "first `git clone` + the dev start command". Mono-repo, Maven structure, package naming and npm (§1–§3, §5 of ADR-027) are unchanged. |
| [ADR-029](./ADR-029-cucumber-test-framework.md) | **Not superseded, extended**: Cucumber JVM, Testcontainers and the step-definition approach stay. Step definitions authenticate through the test-scope login helper against the Keycloak Testcontainer and must not use mock authentication (§5); the step-generation prompts have to include this rule. |
| [ADR-006](./ADR-006-auth-stack-choice.md) | Applied: generic RFC-conformant OIDC client; Authentik reference integration tested by the nightly/release suite; Keycloak is a test fixture, not a new reference integration. |
| [ADR-013](./ADR-013-api-style.md) | Refined: "Swagger UI non-production only" is defined as "only with Spring profile `dev`" and enforced by guard G6. |
| [ADR-024](./ADR-024-audit-separation.md) | Applied: the migration owner role and audit roles have required, non-defaulted secrets in the operator Compose file. |
| [ADR-030](./ADR-030-postgresql-only.md) | Consistent: the IdP's internal store is not OEA data; OEA tests keep using the PostgreSQL Testcontainer. |
| [ADR-032](./ADR-032-authorization-model.md) | Applied: test groups and users cover every built-in role, combined holdings, SoD-3 and SoD-8; IdP group mappings use immutable identifiers. |

## Required Changes to Other Artifacts

| # | Change | Affected | Owner |
|---|---|---|---|
| CH-1 | Header line "Partially superseded: 2026-10-13 by ADR-036 – JWT stub / dev Compose (§4)" and index entry | `adrs/ADR-027-project-setup-monorepo.md`, `adrs/README.md` | Solution Architect |
| CH-2 | Scenario step 1 and DoD: dev start command instead of `docker compose up`; "Keycloak locally" remains | `docs/walking-skeleton.md` | Solution Architect |
| CH-3 | REQ-156 AC1 and REQ-075: `docker compose up` with the operator file requires all secrets; the default configuration is not runnable without `.env` | `requirements/req/REQ-156-postgresql-only-database.md`, `requirements/req/REQ-075-platform-independent-deployment.md` | Requirements Engineer |
| CH-4 | REQ-166 verification method: "against the development IdP (Keycloak Testcontainer) plus crafted invalid tokens" instead of "mock IdP" | `requirements/req/REQ-166-oidc-hardening-identity-binding.md` | Requirements Engineer |
| CH-5 | REQ-183: reference this ADR; mark the conflict with ADR-027 §4 as resolved | `requirements/req/REQ-183-production-safe-defaults.md` | Requirements Engineer |
| CH-6 | Tasks for the first implementation change: `compose.yaml`, `compose.dev.yaml`, `.env.example`, `dev/keycloak/realm-oea-dev.json`, startup guard, ArchUnit rules, Maven Enforcer, `scripts/check_production_artifact.py`, `scripts/check_compose_secrets.py`, login helper | OpenSpec change for the walking skeleton setup | Solution Architect, Backend Engineer |

## References

**Concept Chapters**:

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- ADR-006, ADR-013, ADR-024, ADR-027, ADR-029, ADR-030, ADR-032

**Use Cases / Requirements**:

- [UC-01: Login](../requirements/use-cases/UC-01-login.md), [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [REQ-183: Production-safe defaults without development authentication bypass](../requirements/req/REQ-183-production-safe-defaults.md)
- [REQ-166: OIDC relying-party hardening with mandatory PKCE and immutable identity binding](../requirements/req/REQ-166-oidc-hardening-identity-binding.md)
- [REQ-188: Secrets and encryption key management](../requirements/req/REQ-188-secrets-key-management.md)
- [REQ-156: PostgreSQL as the only supported database](../requirements/req/REQ-156-postgresql-only-database.md)
- [REQ-167: Immutable claim values for remote admin mapping](../requirements/req/REQ-167-immutable-admin-claim-mapping.md), [REQ-171: Mandatory MFA for privileged accounts](../requirements/req/REQ-171-mfa-privileged-accounts.md), [REQ-165: Session timeouts and client token storage](../requirements/req/REQ-165-session-timeouts-token-storage.md), [REQ-187: HTTP security headers, CORS, CSRF and cookie attributes](../requirements/req/REQ-187-http-security-baseline.md)
- [US-001: OIDC login](../requirements/user-stories/US-001-oidc-login.md)

**Security Review**:

- [Security review requirements baseline 2026-10](../security-reviews/requirements-baseline-2026-10.md) – SEC-20, SEC-24, SEC-26; owner decision Q11

## Notes

- Startup times of Keycloak and Authentik in Testcontainers were compared qualitatively; the first implementation change measures them and records the values in its design document.
- License status as of the decision date: Keycloak Apache-2.0, `testcontainers-keycloak` Apache-2.0, Authentik core MIT (enterprise directory under a separate license), `mock-oauth2-server` MIT. None of these tools is distributed with OEA.

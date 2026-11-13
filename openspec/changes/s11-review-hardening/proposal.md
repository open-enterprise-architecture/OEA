# Proposal

## Why

The [architecture review](../../../docs/reviews/architecture-2026-11.md) and the [application security audit](../../../security-reviews/application-audit-2026-11.md) of 2026-11-13 found weaknesses that get worse with every further sprint:

- **AA-01 (security, Medium):** behind the reverse proxy that the operator setup requires, every request appears to come from the proxy. One unauthenticated client can then block all local logins of the instance, and the audit trail records only the proxy's address. The sign-off of the audit requires a fix before any runnable release.
- **API-1 (first part):** the contract declares bearer and OAuth2 authentication as global alternatives, although only the session cookie is implemented.
- **ARCH-1 and ARCH-2:** the ArchUnit rules leave `config` and `audit` unchecked, and there are four package cycles.

The owner decided on 2026-11-13 to resolve these points before S12 adds the approval workflow.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | – (technical package, no user goal) |
| User Stories | – |
| Requirements | [REQ-168: Local authentication throttling](../../../requirements/req/REQ-168-local-auth-throttling.md), [REQ-175: Security event logging](../../../requirements/req/REQ-175-security-event-logging.md), [REQ-187: HTTP security baseline](../../../requirements/req/REQ-187-http-security-baseline.md), [REQ-176: Machine credential lifecycle](../../../requirements/req/REQ-176-machine-credential-lifecycle.md), [REQ-158: Deny-by-default authorization](../../../requirements/req/REQ-158-deny-by-default-authorization.md) |
| ADRs | [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md) (amended by this change), [ADR-013: API style](../../../adrs/ADR-013-api-style.md), [ADR-036 §8](../../../adrs/ADR-036-dev-test-identity-provider.md) |
| Reviews | [Architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md) (ARCH-1, ARCH-2, API-1), [application security audit 2026-11](../../../security-reviews/application-audit-2026-11.md) (AA-01) |
| Sprint / Milestone | Between S11 and S12 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

- **Client address behind a trusted proxy (AA-01):**
  - The backend takes the client address, scheme, host and port from the `X-Forwarded-*` headers, but only when the request comes from a trusted proxy.
  - By default, loopback and the private networks are trusted (owner decision 2026-11-13). The operator narrows this with `OEA_TRUSTED_PROXIES`.
  - The per-address login throttle, the setup-token throttle, the audit source address and the `Origin` check then see the real client.
  - A forwarded header from an untrusted peer is ignored.
  - The manual states what the proxy must send.
- **Implementation status of the security schemes (API-1, first part):**
  - `bearerAuth`, `oauth2ClientCredentials` and `clientSecretBasic` are marked `x-oea-status: planned`.
  - The contract introduction says that the session cookie is the only scheme implemented.
  - As with operations, an instance whose accepted schemes and markers disagree does not start, and the contract check in CI checks the markers.
  - The schemes themselves stay in the contract as the target (owner decision: mark as planned, do not remove).
- **Module rules (ARCH-1, ARCH-2):**
  - A new package `io.oea.infrastructure` takes the technical adapters that `app` and `audit` use: the OIDC client, the WebAuthn verifier, the secret cipher with its key ring, and the configuration guards.
  - ArchUnit checks all layers (`api`, `config`, `app`, `audit`, `infrastructure`, `core`) and that the packages are free of cycles.
  - The cycles `app.access ↔ app.login ↔ app.metamodel`, `api ↔ api.entity` and `config.migration ↔ config.startup` are resolved.
  - [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md) is amended with the new layer and the rules (owner decision).
- **Behavior:** apart from AA-01, no behavior changes. The refactoring moves classes only, and all existing tests stay green.

## Out of Scope

- Service accounts, the token endpoint and bearer authentication themselves (API-1, second part): issue [#264](https://github.com/open-enterprise-architecture/OEA/issues/264).
- The other findings of the reviews, each tracked in its own issue:
  - redaction in deltas `new` (AA-02): [#256](https://github.com/open-enterprise-architecture/OEA/issues/256);
  - `If-Match` (API-2): [#257](https://github.com/open-enterprise-architecture/OEA/issues/257);
  - the full module ADR (ARCH-3): [#258](https://github.com/open-enterprise-architecture/OEA/issues/258);
  - the refactorings (ARCH-4 to ARCH-9): [#259](https://github.com/open-enterprise-architecture/OEA/issues/259), [#260](https://github.com/open-enterprise-architecture/OEA/issues/260), [#261](https://github.com/open-enterprise-architecture/OEA/issues/261);
  - the contract clean-up: [#262](https://github.com/open-enterprise-architecture/OEA/issues/262);
  - the open checks of the audit: [#263](https://github.com/open-enterprise-architecture/OEA/issues/263).
- An ArchUnit rule that confines `JdbcTemplate` to `core` (ARCH-4): it depends on the decision in #258.
- The empty `io.oea.integration` package (ARCH-3).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `platform/deployment`: a new requirement for the client address and the request origin behind a trusted reverse proxy.
- `platform/api-contract`: a new requirement for the implementation status of the security schemes.

## Impact

- **`backend/`:**
  - `application.yml`: forwarded headers and trusted proxies.
  - The adapters move from `config.oidc`, `config.webauthn` and `config.secrets`, and the guards from `config.startup`, to `io.oea.infrastructure`.
  - Shared types of `app.access`, `app.login` and `app.metamodel` move to neutral packages.
  - `api.ApiExceptionHandler` stops depending on `api.entity`.
  - `OperationCoverageCheck` is extended for security schemes.
  - ArchUnit: `LayerRules`, a new cycle rule, and fixtures for both.
  - No Flyway migration.
- **`api/openapi.yaml`:** markers on three security schemes and the introduction text. No operation changes.
- **`scripts/check_openapi_authorization.py`:** checks the markers of the schemes.
- **`compose.yaml`:** `OEA_TRUSTED_PROXIES`.
- **`manual/`:** proxy requirements in the installation guide (en, de).
- **`adrs/ADR-028`:** amendment.
- **Not affected:** `frontend/`, `docs/data-model.puml`, `business-objects/`, `docs/screens/`.

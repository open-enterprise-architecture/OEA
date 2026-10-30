# Proposal

## Why

S5 change 6 consolidates the local authentication of persons. Two stories remain open:

- [US-003](../../../requirements/user-stories/US-003-access-without-role.md) (access without role) is already covered by the specs `identity/idp-roles` and `identity/role-assignments`, but it is still in the backlog.
- [US-008](../../../requirements/user-stories/US-008-login-latency.md) and [REQ-008](../../../requirements/req/REQ-008-login-latency.md) set a latency target that nothing measures yet.

The owner decided on 2026-10-30:

- **US-003 AC2:** a new role applies within 60 seconds for changes made in OEA (local role assignments and IdP group mappings). Group changes at the identity provider keep following the re-evaluation of at most 15 minutes (ADR-032 Q16/Q17, REQ-165 AC5).
- **US-008:** a reproducible measurement now covers the local and the OIDC login. AC2 (client credentials token) follows with the token endpoint.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-01: Login](../../../requirements/use-cases/UC-01-login.md) |
| User Stories | [US-008: Login latency](../../../requirements/user-stories/US-008-login-latency.md) (AC1); [US-003: Access without role](../../../requirements/user-stories/US-003-access-without-role.md) |
| Requirements | [REQ-008](../../../requirements/req/REQ-008-login-latency.md); [REQ-003](../../../requirements/req/REQ-003-access-without-role-assignment.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (Q16/Q17, permission cache), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md) (development realm for OIDC tests), [ADR-030](../../../adrs/ADR-030-postgresql-only.md) |
| Sprint / Milestone | S5 change 6 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-003, REQ-003:**
  - AC1 is the scenario "Person without any role" of `identity/idp-roles` together with the 403 answers of the permission check (REQ-158).
  - AC2 is the scenario "Assignment effective within 60 seconds" of `identity/role-assignments` and the 60-second rule of `identity/idp-roles` for mappings.
  - Group changes at the identity provider follow the re-evaluation (owner decision 2026-10-30).
  - The story is set to `done` without new code.
- **US-008, REQ-008:**
  - "Server-side processing time" is the time OEA spends on a login without the round trips to the identity provider (token endpoint, JWKS).
  - AC1 also covers the password login of persons, the second step with TOTP, and the passwordless login with a passkey.
  - AC2 (client credentials) stays open until `issueClientCredentialsToken` is implemented, so the story stays `in-progress`.

## What Changes

- **Login processing metric:**
  - Every successful or refused login records its server-side processing time as the Micrometer timer `oea.login.processing`, tagged with the method (`oidc`, `password`, `password-totp`, `password-passkey`, `passkey`) and the outcome.
  - For an OIDC login, the time of the token request to the identity provider is excluded.
  - The timer is available through the existing metrics endpoint of the actuator.
- **Latency measurement (US-008 AC1):**
  - New integration test `LoginLatencyIT`. It runs on a database with 10,000 persons, 1,000 roles, 1,000 IdP group mappings and local role assignments, against the development realm (ADR-036).
  - It measures OIDC logins and password logins of persons and checks that the 95th percentile of `oea.login.processing` is below 300 ms. It runs in CI with `./mvnw verify`.
- **Indexes:** any index that the measurement shows to be missing for the login queries (person identity, group mappings, role assignments, permissions) is added in a Flyway migration.
- **Documentation:**
  - Operations manual: the metric `oea.login.processing`.
  - Stories: US-003 set to `done`; US-008 notes the measurement.

## Out of Scope

- **US-008 AC2:** the client credentials token, until the token endpoint exists.
- **Load tests with concurrent users** in a production-like environment.
- **Shorter IdP re-evaluation intervals** (owner decision 2026-10-30).

## Capabilities

### New Capabilities

- `platform/login-latency`: the login processing metric and the latency target with its measurement.

### Modified Capabilities

None. US-003 is covered by `identity/idp-roles` and `identity/role-assignments` without changes.

## Impact

- **`backend/`:** `LocalLogin`, `PasskeyLogin`, `OidcLoginFlow` (timer around the server-side processing); a small `io.oea.app.login.LoginMetrics` component.
- **Tests:** `LoginLatencyIT` (failsafe) with seed data, the development realm and the timer.
- **Flyway:** a migration with indexes if the measurement needs them.
- **Docs:** manual operations (EN/DE), US-003, US-008, REQ-003, REQ-008, sprint plan.

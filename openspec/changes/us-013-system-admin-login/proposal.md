# Proposal

## Why

After the bootstrapping (change 2) the instance has a system admin account that cannot log in, so nobody can create the persons, identity bindings and IdP group mappings that the first OIDC login (change 3b, US-001) needs. This change lets the system admin log in during the setup phase of [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md), enforces a second factor before any other identity gets access, and builds the session and authorization infrastructure every later operation relies on.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-02: System Admin Bootstrapping](../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md), [UC-23: Manage Roles and Role Assignments](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md), [UC-01: Login](../../../requirements/use-cases/UC-01-login.md) (session part) |
| User Stories | [US-013: Local bootstrapping](../../../requirements/user-stories/US-013-local-bootstrapping.md) (AC2), [US-143: Seed permission catalog and built-in roles](../../../requirements/user-stories/US-143-seed-permission-catalog-built-in-roles.md) (AC1, AC2, AC4), [US-147: Map IdP group to role](../../../requirements/user-stories/US-147-map-idp-group-to-role.md) (AC1, AC4, AC7; login-related AC2, AC3, AC5, AC6 follow in change 3b) |
| Requirements | [REQ-157](../../../requirements/req/REQ-157-permission-model.md), [REQ-158](../../../requirements/req/REQ-158-deny-by-default-authorization.md), [REQ-163](../../../requirements/req/REQ-163-no-anonymous-access.md), [REQ-164](../../../requirements/req/REQ-164-session-termination-revocation.md) (logout), [REQ-165](../../../requirements/req/REQ-165-session-timeouts-token-storage.md) (timeouts, cookies), [REQ-167](../../../requirements/req/REQ-167-immutable-admin-claim-mapping.md), [REQ-168](../../../requirements/req/REQ-168-local-auth-throttling.md), [REQ-169](../../../requirements/req/REQ-169-password-storage-quality.md), [REQ-170](../../../requirements/req/REQ-170-totp-replay-protection.md), [REQ-171](../../../requirements/req/REQ-171-mfa-privileged-accounts.md), [REQ-172](../../../requirements/req/REQ-172-reauthentication-sensitive-actions.md), [REQ-175](../../../requirements/req/REQ-175-security-event-logging.md), [REQ-187](../../../requirements/req/REQ-187-http-security-baseline.md), [REQ-188](../../../requirements/req/REQ-188-secrets-key-management.md) |
| ADRs | [ADR-006](../../../adrs/ADR-006-auth-stack-choice.md), [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md) (§6 issuer rules on save), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md) |
| Sprint / Milestone | S1 / Walking skeleton 1/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

Walking-skeleton change 3a ([docs/walking-skeleton.md](../../../docs/walking-skeleton.md)); change 3b (US-001, OIDC login) builds on it.

## What Changes

- **Sessions** (REQ-164, REQ-165, REQ-187): server-side sessions for the web frontend (backend-for-frontend, owner decision 2026-10-15) with an `HttpOnly`, `Secure`, `SameSite=Strict` cookie `__Host-OEA_SESSION`, idle timeout 30 minutes and absolute lifetime 12 hours (configurable within the REQ-165 ranges), CSRF protection for state-changing requests, logout, `GET /me`. **BREAKING** for the contract: the cookie scheme is renamed from `OEA_SESSION` to `__Host-OEA_SESSION` (no client exists yet).
- **Authorization enforcement** (ADR-032 §8 L1/L2, REQ-157, REQ-158, REQ-163): every request needs an authenticated principal except the public allowlist; each operation's permissions are generated from `x-oea-authorization` and checked before the controller; undeclared operations are denied; permission catalog and built-in roles seeded by migration and readable via `GET /admin/permissions` and `GET /admin/roles` (US-143); a generated matrix test calls every implemented operation without the permission.
- **System admin login** (US-013 AC2, ADR-042, REQ-168, REQ-169): `POST /auth/local/login` with login name and password; during the setup phase without second factor, afterwards with TOTP; throttling per account and per source address with generic responses; argon2id rehash on outdated parameters; audit events `auth.login`, `auth.login-failed`, `auth.throttled`, `auth.logout`.
- **Second factor** (REQ-170, REQ-171, REQ-172, ADR-042): TOTP enrollment and confirmation in the running session (fresh authentication within 5 minutes), replay protection, confirmation marks the session as second-factor-verified.
- **Setup phase** (ADR-042): the first identity binding or IdP group mapping is accepted only with an enrolled and confirmed second factor (`403 urn:oea:problem:second-factor-required` otherwise) and ends the setup phase irreversibly (`system.setup-phase-ended`).
- **Secrets** (REQ-188): key-encryption keys from files outside the database (256 bit), AES-256-GCM for TOTP secrets and OIDC client secrets with the key ID per value; command `oea secrets reencrypt` for key rotation; startup guard G4 covers the keys.
- **Access administration via API** (owner decisions 2026-10-15: providers via the admin API, administration without UI, roles only through IdP group mappings): OIDC providers (`/admin/auth/providers`, issuer rules G2/G3 on save, client secret write-only), persons (`/admin/persons`, new in the contract), identity bindings `admin-entered` (`/admin/persons/{personId}/identities`, new), IdP group mappings (`/admin/idp-group-mappings`, US-147 AC1, AC4, AC7); every change audited.
- **Frontend**: login page, TOTP enrollment page, logout and a minimal profile view; administration only through the API.
- **Data model**: tables `persons`, `person_identities`, `auth_providers`, `permissions`, `authorization_roles`, `role_permissions`, `idp_group_mappings`, `sessions`, `totp_credentials`, `auth_throttle` (new) and the setup-phase columns of `system_settings` (new attributes); `docs/data-model.puml` and `business-objects/` updated.

## Out of Scope

- OIDC login of persons, IdP-derived roles in sessions, group re-evaluation every 15 minutes, refresh tokens, issuer guards G1–G3 at startup – change 3b (US-001)
- Local role assignments (`/admin/persons/{personId}/role-assignments`), custom roles, four-eyes approval, access review – UC-23 later
- Administration screens in the frontend; screen stories (SCR-002 and others stay as they are)
- Passkeys, recovery codes, factor reset and break-glass (REQ-009, REQ-173); local logins of persons (REQ-010, REQ-011); service accounts (REQ-176)
- Electron client tokens (bearer, OS storage), `email-match` identity bindings, person import, entity link of persons
- Per-principal API rate limits beyond the login endpoints (REQ-186)

## Capabilities

### New Capabilities

- `identity/sessions`: browser sessions – cookie attributes, timeouts, CSRF protection, logout and the current principal
- `identity/local-login`: login of the local system admin account – password, throttling, setup phase, second factor and its enrollment
- `identity/access-administration`: administration of OIDC providers, persons, identity bindings and IdP group mappings, including the rule that the first foreign access needs the admin's second factor
- `platform/authorization`: deny-by-default authentication and function-level authorization generated from the API contract, permission catalog and built-in roles
- `platform/secret-storage`: encryption of stored secrets with an external key-encryption key

### Modified Capabilities

- none

## Impact

- `backend/`: Spring Security (web) and the session filter in `io.oea.config`; `io.oea.app` services for login, enrollment and administration; `io.oea.core` entities and repositories; `io.oea.api` controllers; generated permission mapping from `api/openapi.yaml`
- Flyway: `db/migration/V3__…` (identity, authorization, session tables, seeded permissions and built-in roles)
- `api/openapi.yaml`: new `POST /auth/local/login`, TOTP enrollment operations, `/admin/persons`, `/admin/persons/{personId}/identities`; cookie scheme renamed; problem types `second-factor-required`, `reauthentication-required`, `invalid-credentials`
- `frontend/`: login page, TOTP enrollment page, logout, profile view; CSRF header for state-changing requests
- `compose.yaml`, `compose.dev.yaml`, `.env.example`: key-encryption key file as a Compose secret; development key listed in the known-development-secrets list
- Docs: `docs/operations.md` (key file, setup phase), `docs/audit-event-catalog.md` (new event types), `docs/data-model.puml`, `business-objects/`

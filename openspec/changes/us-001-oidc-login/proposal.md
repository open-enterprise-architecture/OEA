# Proposal

## Why

After change 3a the system admin can configure providers, persons, identity bindings and IdP group mappings, but no person can log in yet. This change completes the second half of walking-skeleton step 4 ([docs/walking-skeleton.md](../../../docs/walking-skeleton.md)): the OIDC login of persons (US-001) with the roles derived from IdP group mappings, so that the following changes (metamodel import, entities, catalog) run with real, role-checked persons.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-01: Login](../../../requirements/use-cases/UC-01-login.md), [UC-23: Manage Roles and Role Assignments](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (A1, IdP group mappings) |
| User Stories | [US-001: OIDC login](../../../requirements/user-stories/US-001-oidc-login.md) (AC1–AC3; AC4 optional, later), [US-147: Map IdP group to role](../../../requirements/user-stories/US-147-map-idp-group-to-role.md) (AC2, AC3, AC5, AC6; AC7 at the next request) |
| Requirements | [REQ-001](../../../requirements/req/REQ-001-oidc-login-session.md), [REQ-003](../../../requirements/req/REQ-003-access-without-role-assignment.md), [REQ-004](../../../requirements/req/REQ-004-reject-unknown-offboarded-person.md), [REQ-005](../../../requirements/req/REQ-005-audit-log-login.md), [REQ-006](../../../requirements/req/REQ-006-no-account-existence-disclosure.md), [REQ-007](../../../requirements/req/REQ-007-lifecycle-transition-invited-active.md), [REQ-165](../../../requirements/req/REQ-165-session-timeouts-token-storage.md) (AC5), [REQ-166](../../../requirements/req/REQ-166-oidc-hardening-identity-binding.md) (AC1–AC3), [REQ-167](../../../requirements/req/REQ-167-immutable-admin-claim-mapping.md), [REQ-171](../../../requirements/req/REQ-171-mfa-privileged-accounts.md) (persons), [REQ-183](../../../requirements/req/REQ-183-production-safe-defaults.md) (AC2, AC5) |
| ADRs | [ADR-006](../../../adrs/ADR-006-auth-stack-choice.md), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (§7 IdP group mappings, SoD-1, SoD-8, DM-10), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md) (§5 tests, §6 guards G1–G3), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md) |
| Sprint / Milestone | S1 / Walking skeleton 1/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

Walking-skeleton change 3b; builds on change 3a (`us-013-system-admin-login`, archived).

## What Changes

- **OIDC login of persons** (US-001 AC1–AC3, REQ-001, REQ-166): authorization code flow with PKCE `S256`, `state` and `nonce`, run by the backend (backend-for-frontend, confidential client); ID token validation of `iss`, `aud`, `azp`, `exp`, `nbf`, `nonce` and the signature against the provider's JWKS (asymmetric algorithms only); the person is found by the immutable pair (`iss`, `sub`) of an identity binding, never by e-mail; only persons in status `invited` or `active` get a session, and `invited` becomes `active` at the first login (REQ-004, REQ-007); the result is the same `__Host-OEA_SESSION` session as in change 3a. Failures produce one generic message (REQ-006) and audit events (REQ-005).
- **Providers from the database only** (owner decision 2026-10-15): the login uses the providers stored through the admin API; the property list `oea.security.oidc.providers` is removed. The startup guards G1–G3 and the inverse guard of the profile `dev` check the stored providers before the web server accepts connections. The development realm is provisioned by a seed script that drives the admin API. **BREAKING** for configurations that set `oea.security.oidc.providers` (only the development configuration does).
- **Session lifetime with an IdP** (owner decision 2026-10-15): a session established through an IdP ends at the first of the OEA idle timeout, the OEA absolute lifetime, or the expiry or rejection of the IdP refresh token; OEA never extends an IdP token. REQ-001 (context "Token validity") and US-001 AC3 are clarified accordingly.
- **IdP-derived roles** (US-147 AC2, AC3, AC6, AC7; REQ-165 AC5; ADR-032 §7, DM-10): group identifiers of the `groups` claim of the provider's issuer are matched against active mappings at login and re-evaluated with the stored, encrypted IdP refresh token at the latest 15 minutes after the previous evaluation; a missing or truncated groups claim, a different issuer or a failed refresh yields no IdP-derived roles (fail closed) and a warning event; derived roles are recorded per session (`session_idp_roles`) and every change of the set is audited; a deleted or deactivated mapping stops applying within 60 s.
- **Second factor for privileged roles of persons** (SoD-8, REQ-171; owner decision 2026-10-15): a privileged role derived for a person is effective only if the ID token's `amr` shows a second factor; otherwise it is listed with `notEffectiveReason: mfa-required`.
- **Self-administration rule** (US-147 AC5, SoD-1): creating or changing a mapping for a group contained in the caller's own current groups claim is rejected with 403 and reason `self-administration`.
- **Contract**: public operations to list the enabled login providers, start the login and receive the callback; `Me` lists IdP-derived roles with source `idp`.
- **Frontend**: the login page offers the enabled providers next to the local login; after the callback the profile shows the derived roles and the "no role assigned" notice of REQ-003.
- **Data model**: new table `session_idp_roles` (modeled, not yet migrated); `sessions` gains the IdP token expiry; table `oidc_login_states` for state, nonce and PKCE verifier of running logins.

## Out of Scope

- US-001 AC4 / REQ-001 AC3: optional online token validation (introspection) – later, as noted in US-001
- Electron client login, OEA-issued access and refresh tokens with rotation and reuse detection (REQ-165 AC2, AC4; REQ-166 AC5)
- First-time linking by e-mail with administrator confirmation (REQ-166 AC4), person import
- Local role assignments and the "last path to `system-admin`" rule of US-147 AC7 (US-146); service accounts (US-147 AC8)
- RP-initiated logout at the IdP (`endSessionUrl`) – the logout ends the OEA session only
- Authentik reference suite (ADR-036 §4) – the nightly suite follows with the CI hardening
- Screen stories: SCR-001 keeps the status `legacy-mockup` (screen stories with change 6)

## Capabilities

### New Capabilities

- `identity/oidc-login`: login of persons through a configured OIDC provider – authorization code flow with PKCE, token validation, identity binding lookup, person status, login failures
- `identity/idp-roles`: authorization roles derived from IdP group mappings – derivation at login, re-evaluation within 15 minutes, fail-closed cases, second factor for privileged derived roles

### Modified Capabilities

- `identity/sessions`: session timeouts additionally bounded by the IdP refresh token of sessions established through an IdP; the current principal lists roles with their source
- `identity/access-administration`: IdP group mappings rejected for the caller's own groups (SoD-1)
- `platform/startup-safety`: issuer guards G1–G3 and the inverse development guard apply to the stored providers instead of the removed property list

## Impact

- `backend/`: OIDC client and token validation in `io.oea.config.security` (Spring Security OAuth2 client and JOSE, without the default login pages); login service, role derivation and re-evaluation in `io.oea.app`; `io.oea.core` entities; the startup guard for stored providers; removal of `oea.security.oidc.providers`
- Flyway: `db/migration/V4__oidc_login.sql` (`session_idp_roles`, `oidc_login_states`, `sessions.idp_token_expires_at`)
- `api/openapi.yaml`: `listLoginProviders`, `startOidcLogin`, `completeOidcLogin`; `Me` roles with source `idp`; problem type `login-failed`
- `frontend/`: provider buttons on the login page, error display after the callback, roles in the profile
- `dev/`: realm redirect URI for the callback, seed script `scripts/dev_seed.py`, `dev/backend/application-dev.yml` without providers
- Docs: `docs/walking-skeleton.md` (provider source), `docs/operations.md` (login configuration, public URL), `docs/audit-event-catalog.md`, `docs/data-model.puml`, `business-objects/session.md`, REQ-001 and US-001 AC3 wording

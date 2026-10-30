# Proposal

## Why

Since change 3 of S5, persons with a local password have TOTP as second factor. Passkeys (WebAuthn) are the stronger and simpler option: phishing-resistant, no code to type, and usable without a password ([UC-03](../../../requirements/use-cases/UC-03-set-up-authentication-method.md), [UC-01](../../../requirements/use-cases/UC-01-login.md) A3). [ADR-048](../../../adrs/ADR-048-webauthn-library.md) chose WebAuthn4J in the backend and `@simplewebauthn/browser` in the web portal.

The owner decided on 2026-10-30:

- a login with a passkey alone counts as second-factor-verified if the authenticator verified the user (PIN or biometrics, `userVerification: required`); privileged roles then apply (SoD-8);
- persons with a local password register passkeys; the system admin account stays with password and TOTP (ADR-042);
- after the password step, a person with a passkey must confirm with the passkey or, if present, a TOTP code.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-03: Set up authentication method](../../../requirements/use-cases/UC-03-set-up-authentication-method.md); [UC-01: Login](../../../requirements/use-cases/UC-01-login.md) (A3) |
| User Stories | [US-022: Passkey enrollment](../../../requirements/user-stories/US-022-passkey-enrollment.md); [US-009: Passkey login](../../../requirements/user-stories/US-009-passkey-login.md); [US-025: Additional method for a logged-in person](../../../requirements/user-stories/US-025-additional-method-logged-in-person.md) (AC2); [US-024: Audit log of enrollments](../../../requirements/user-stories/US-024-audit-log-enrollment.md) (passkeys) |
| Requirements | [REQ-023](../../../requirements/req/REQ-023-passkey-enrollment.md); [REQ-009](../../../requirements/req/REQ-009-passkey-login.md); [REQ-026](../../../requirements/req/REQ-026-additional-method-authenticated-person.md) AC2; [REQ-025](../../../requirements/req/REQ-025-audit-log-enrollment.md); [REQ-168](../../../requirements/req/REQ-168-local-auth-throttling.md); [REQ-172](../../../requirements/req/REQ-172-reauthentication-sensitive-actions.md); [REQ-173](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC5 |
| ADRs | [ADR-048](../../../adrs/ADR-048-webauthn-library.md) (WebAuthn4J, `@simplewebauthn/browser`), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (SoD-8), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md), [ADR-043](../../../adrs/ADR-043-third-party-license-policy.md) (licenses of new dependencies), [ADR-013](../../../adrs/ADR-013-api-style.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S5 change 4 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-022, REQ-023 (enrollment token, endpoints):**
  - There are no enrollment tokens; a person registers a passkey in the running session, like TOTP (change 3): `POST /me/second-factors/passkey` returns the creation options, and `POST /me/second-factors/passkey/{registrationId}/confirm` verifies the response.
  - AC2 (cancellation): a cancelled registration leaves only a pending registration that expires after 10 minutes; nothing is stored as a credential.
  - The user handle is a random 64-byte value per person, never the e-mail address or the person ID. Attestation is `none`.
- **US-009, REQ-009 (endpoints):** the passwordless login is `POST /auth/passkey/options` and `POST /auth/passkey/login`; a failure gives the same answer as a wrong password (AC2). Discoverable credentials (resident keys) are required at registration, so AC3 holds for every passkey.
- **REQ-009, SoD-8:** a passkey login with user verification is second-factor-verified (owner decision 2026-10-30).
- **US-024, REQ-025 (passkeys):** a confirmed registration is `auth.factor-added` with `type: passkey`; a failed verification is `auth.factor-enrollment-failed` with reason `verification-failed`. A cancellation in the browser never reaches the server and is not audited.
- **US-025 AC2:** several passkeys per person; each can be used.

## What Changes

- **Passkey registration (US-022, US-025 AC2, REQ-023):**
  - New `startPasskeyRegistration` and `confirmPasskeyRegistration` under `/me/second-factors/passkey` for persons with a local password; re-authentication within 5 minutes (REQ-172).
  - Discoverable credential and user verification required; attestation `none`; the relying party ID and the allowed origin come from `oea.web.public-url` (overridable with `oea.auth.passkey.*`).
  - Verified with WebAuthn4J (origin, RP ID hash, challenge, user verification, signature); a wrong origin or RP ID is rejected (AC4).
  - Stored as `local_credentials` of type `passkey` with `passkey_credentials` (credential ID, COSE public key, signature counter, AAGUID, label). The confirmation marks the session second-factor-verified and is audited as `auth.factor-added`.
- **Passwordless login (US-009, REQ-009):**
  - New public `POST /auth/passkey/options` and `POST /auth/passkey/login`. The browser offers the discoverable passkeys of the device; the login identifies the person by the credential.
  - Only persons in status `invited` or `active` with password login of persons possible; an invited person becomes `active`.
  - The session is second-factor-verified (owner decision); the required password change of a temporary or expired password still applies.
  - A failure answers like a wrong password, is audited as `auth.login-failed` and counts for the throttling per source address (REQ-168 AC3). A signature counter that goes backwards rejects the login.
- **Passkey as second step after the password (owner decision):**
  - The `second-factor-required` answer lists the methods of the person (`totp`, `passkey`).
  - New `POST /auth/local/login/passkey/options` and `POST /auth/local/login/passkey` complete a login transaction with a passkey of that person; five failures invalidate the transaction, as for codes.
- **Second factors:** `GET /me/second-factors` lists passkeys with their label; removal and the factor reset (change 3) cover passkeys.
- **Frontend:**
  - SCR-003: the choice between passkey and authenticator app, the passkey registration with label; the passkey mockup is replaced by the screen story.
  - SCR-001: "Log in with a passkey", and "Use a passkey" in the second-factor step.
  - `@simplewebauthn/browser` as new dependency (ADR-048).
- **Dependencies:** `com.webauthn4j:webauthn4j-core` (backend), `@simplewebauthn/browser` (frontend); third-party licenses regenerated (ADR-043).
- **Manual:** usage (register and use a passkey) and operations (relying party); English and German.

## Out of Scope

- **Passkeys of the system admin account** (owner decision): it keeps password and TOTP.
- **Passkeys of persons who log in through an IdP**, and passkeys without a local password.
- **2FA enforcement:** change 5.
- **The desktop client:** passkeys in Electron follow with the client work; this change covers the web portal.
- **Attestation and authenticator allow lists** (AAGUID policies).

## Capabilities

### New Capabilities

- `identity/passkeys`: the passkey registration of persons, the passwordless login, the passkey as second step after the password, and the relying party settings.

### Modified Capabilities

- `identity/person-totp`: "TOTP login of persons" – the second step after the password accepts a passkey as well, and the answer names the available methods.
- `identity/second-factor-management`: "Overview of the own second factors" – passkeys are listed with their label.

## Impact

- **`backend/`:**
  - New `io.oea.app.login.PasskeyRegistration` and `PasskeyLogin`, `io.oea.config.webauthn` (WebAuthn4J manager, relying party properties).
  - `LocalLogin` (methods in the second-factor answer, passkey second step), `SecondFactors` (labels of passkeys).
  - Controller operations in `io.oea.api.identity`.
- **Flyway `V13__passkeys.sql`:** `passkey_credentials`, `persons.webauthn_user_handle`, `webauthn_challenges`, `sessions.auth_method` value `password-passkey`.
- **`api/openapi.yaml`:** the six new operations, the second-factor answer with `secondFactors`, schemas for options and responses as JSON objects of the WebAuthn Level 3 JSON encoding.
- **Data model:** `docs/data-model.puml` (`passkey_credentials` migrated, `webauthn_challenges`, `persons.webauthn_user_handle`), `business-objects/local-credential.md`.
- **`frontend/`:** `SecondFactorPage.vue`, `LoginPage.vue`, `api/session.ts`, new `api/passkey.ts`; screen stories SCR-001 and SCR-003 and their images.
- **Licenses:** `THIRD-PARTY-LICENSES.md`.
- **Docs:** audit event catalog, manual (English and German).

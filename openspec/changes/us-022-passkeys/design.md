# Design

## Context

See proposal.md – Why. Relevant current state (after `us-021-person-totp`):

- **Second factors of persons:**
  - `TotpEnrollment` (`io.oea.app.login`) enrolls TOTP in the running session with re-authentication (`RecentAuthentication`, 5 minutes) and audits `auth.factor-added` and `auth.factor-enrollment-failed`.
  - `SecondFactors` (`io.oea.app.access`) lists active `totp` and `passkey` credentials, removes them and resets them. Its queries already include the type `passkey`.
- **Login:**
  - `LocalLogin.loginPerson` throws `SecondFactorRequiredException` with a login transaction when the person has an active TOTP credential.
  - `loginWithTotp` completes the transaction for the owner; five failed codes invalidate it.
  - `startPersonSession` activates an invited person, computes the required password change and creates the session (`Sessions.createForPersonPassword`, with the second-factor flag).
- **Storage:**
  - `local_credentials.type` allows `passkey`. `passkey_credentials` is modeled in `docs/data-model.puml` but not migrated.
  - `sessions.auth_method` allows `passkey`, but not `password-passkey`.
- **Relying party:** `oea.web.public-url` is the public URL of the web portal (used by the OIDC login). `CsrfOriginFilter` checks the `Origin` of state-changing requests.
- **ADR-048:** WebAuthn4J used directly (Jackson 3) and `@simplewebauthn/browser` in the web portal.

## Goals / Non-Goals

**Goals:**

- Passkeys for persons with a local password: registration, passwordless login, and second step after the password.
- One verification component (WebAuthn4J) behind OEA's own endpoints, sessions, audit and throttling.

**Non-Goals:**

- Passkeys of the system admin account, of IdP persons or in the desktop client.
- Attestation verification and AAGUID allow lists.
- 2FA enforcement.

## Architecture Constraints

- **ADR-013:** the operations go into `api/openapi.yaml` first. Options and responses are JSON objects of the WebAuthn Level 3 JSON encoding (`PublicKeyCredentialCreationOptionsJSON`, `RegistrationResponseJSON`, and the request and authentication counterparts), typed as open objects with the documented required members.
- **ADR-028:**
  - Registration and login services in `io.oea.app.login`; the WebAuthn4J configuration in `io.oea.config.webauthn`.
  - Controllers in `io.oea.api.identity`.
  - WebAuthn4J types do not reach `io.oea.api`: the application layer takes and returns JSON strings or maps.
- **ADR-032 SoD-8:** a passkey login with user verification is second-factor-verified (owner decision 2026-10-30).
- **ADR-043:** the licenses of `webauthn4j-core`, `jackson-dataformat-cbor` (Apache-2.0) and `@simplewebauthn/browser` (MIT) are allowed; `THIRD-PARTY-LICENSES.md` is regenerated.
- **REQ-168:** passkey login failures count for the throttling per source address; there is no login name before the assertion.
- **ADR-031:** SCR-001 and SCR-003 change with their screen stories.

## Decisions

### D1: Relying party

`PasskeyProperties` (`@ConfigurationProperties("oea.auth.passkey")`):

- `rp-id` – default: the host of `oea.web.public-url`;
- `rp-name` – default `OEA`;
- `origins` – default: `oea.web.public-url` without path.

Without an RP ID or an origin, every passkey operation answers 409 (problem type `passkeys-unavailable`). The values are validated at startup: the RP ID must equal or be a registrable suffix of the host of every origin.

### D2: Storage (`V13__passkeys.sql`)

- **`passkey_credentials`:**

  | Column | Type | Content |
  |---|---|---|
  | `credential_id` | `UUID` PK | FK → `local_credentials.id` |
  | `webauthn_credential_id` | `BYTEA` | NOT NULL, UNIQUE |
  | `attested_credential_data` | `BYTEA` | NOT NULL; AAGUID, credential ID and COSE public key, serialized with WebAuthn4J's `AttestedCredentialDataConverter` |
  | `sign_count` | `BIGINT` | NOT NULL, DEFAULT 0 |
  | `transports` | `TEXT[]` | |
  | `backup_eligible` | `BOOLEAN` | NOT NULL |
  | `backed_up` | `BOOLEAN` | NOT NULL |
  | `label` | `TEXT` | |

  This replaces the planned columns `credential_b64`, `public_key` and `aaguid` of the data model; the AAGUID stays readable from the attested data.
- **`persons.webauthn_user_handle`:** `BYTEA` UNIQUE, 64 random bytes, created at the first registration (US-022 notes: never the e-mail address).
- **`webauthn_challenges`:**

  | Column | Type | Content |
  |---|---|---|
  | `id` | `UUID` PK | |
  | `purpose` | `TEXT` | CHECK `registration`, `login`, `second-factor` |
  | `challenge` | `BYTEA` | NOT NULL, 32 random bytes |
  | `person_id` | `BIGINT` | NULL for `login` |
  | `login_transaction_id` | `UUID` | FK, for `second-factor` |
  | `label` | `TEXT` | for `registration` |
  | `created_at`, `expires_at` | | registration 10 minutes, login and second factor 5 minutes |

  A challenge is deleted when it is used, successfully or not; creating a challenge deletes the expired rows, so the table stays small without a separate cleanup job.
- **`sessions.auth_method`:** the CHECK gets `password-passkey`.

### D3: Registration

`PasskeyRegistration.start(principal, label)`:

1. **Checks:** a person with an active local password (403 otherwise) and re-authentication (401 `reauthentication-required`).
2. **Challenge:** stores a challenge with purpose `registration`.
3. **Options:** returns creation options with
   - `rp {id, name}`;
   - `user {id: user handle, name: e-mail address, displayName: name}`;
   - `pubKeyCredParams` ES256 (-7) and RS256 (-257);
   - `authenticatorSelection {residentKey: required, userVerification: required}`;
   - `attestation: none`;
   - `excludeCredentials` with the person's passkeys;
   - `timeout` 300000.

`confirm(principal, registrationId, responseJson)`:

1. Loads the unexpired registration challenge of the person (404 and `auth.factor-enrollment-failed` with reason `enrollment-expired` otherwise).
2. Verifies with `WebAuthnManager.verifyRegistrationResponseJSON` against challenge, origins, RP ID, user verification required and user presence required.
3. On failure: 400 problem type `invalid-passkey`, audited with reason `verification-failed`; the challenge is used up.
4. Stores `local_credentials` (type `passkey`, active) and `passkey_credentials`. The label defaults to `Passkey (YYYY-MM-DD)`.
5. Marks the session second-factor-verified and appends `auth.factor-added` (`type: passkey`, `label`).

### D4: Passwordless login

- **`POST /auth/passkey/options`** (`startPasskeyLogin`, public): stores a challenge with purpose `login` and returns `{challengeId, options}`, with the request options `rpId`, `challenge`, `userVerification: required`, `timeout` and no `allowCredentials`, so the browser offers the device's discoverable passkeys.
- **`POST /auth/passkey/login`** (`passkeyLogin`, public, body `{challengeId, credential}`):
  1. Checks the throttling per source address (REQ-168 AC3), then consumes the challenge.
  2. Finds the passkey by its credential ID and the person by the user handle; both must match.
  3. Verifies with `WebAuthnManager.verifyAuthenticationResponseJSON` (challenge, origin, RP ID, user verification, signature, counter).
  4. Requires password login of persons possible, a human person in status `invited` or `active`, and an active passkey. A signature counter that is not greater than the stored one is rejected, unless both are 0 (synced passkeys).
  5. Updates `sign_count` and `last_used_at`, and calls `startPersonSession(…, secondFactor: true, method: passkey)`. The person's active password credential provides origin and age for the required password change; a person without an active password cannot use passkeys, because the registration requires one and the password reset keeps it.

  Every failure answers 401 `invalid-credentials`, records a throttling failure for the source address and appends `auth.login-failed` (reason `invalid-credentials`). There is one answer for all failures, so nothing reveals which check failed.
- **Session:** `Sessions.createForPersonPassword` gets the method (`password`, `password-totp`, `password-passkey`, `passkey`) instead of the boolean; `auth.login` names the same method.

### D5: Passkey as second step

- **Second-factor answer:** `SecondFactorRequiredException` carries the methods of the person. The problem body gets `secondFactors: ["totp", "passkey"]`, and the system admin account gets `["totp"]`. A person with only passkeys also gets a login transaction.
- **`POST /auth/local/login/passkey/options`** (`startLocalLoginPasskey`, public, body `{loginTransaction}`):
  - for a valid transaction of a person with passkeys: a challenge with purpose `second-factor`, request options with `allowCredentials` of the person's passkeys and `userVerification: required`;
  - an unknown or invalid transaction gives the generic 401.
- **`POST /auth/local/login/passkey`** (`localLoginPasskey`, public, body `{loginTransaction, challengeId, credential}`):
  - verifies as in D4, with the credential restricted to the transaction's person;
  - a failure counts as a failed code of the transaction (five invalidate it);
  - success deletes the transaction and starts the session with method `password-passkey`.

### D6: Frontend

- **`api/passkey.ts`** wraps `@simplewebauthn/browser` (`startRegistration`, `startAuthentication`, `browserSupportsWebAuthn`) and maps a cancellation (`NotAllowedError`) to an outcome without server call.
- **SCR-003** (`SecondFactorPage.vue`): if the browser supports WebAuthn, the page offers "Passkey" (recommended) and "Authenticator app". The passkey path asks for a label and starts the registration. The offer after the password login (change 3) shows the same choice.
- **SCR-001** (`LoginPage.vue`):
  - "Log in with a passkey" below the local form;
  - in the second-factor step, "Use a passkey" when `secondFactors` contains `passkey`, and the code field only when it contains `totp`.
- **SCR-005:** the list shows the type ("Passkey" or "Authenticator app") with the label; nothing else changes.

### D7: Tests without hardware

- **Backend:** WebAuthn4J's test module (`webauthn4j-test`, test scope) provides a software authenticator that creates registration and authentication responses for a given challenge, origin and RP ID. Integration tests and Cucumber steps use it. Wrong origins, missing user verification and replayed counters are produced with the same emulator.
- **Frontend:** the tests stub `@simplewebauthn/browser`; the screen stories show the states without calling the browser API.

## Data Model

- **`docs/data-model.puml`:** `passkey_credentials` as migrated (D2), `persons.webauthn_user_handle`, new `webauthn_challenges`, `sessions.auth_method` value `password-passkey`.
- **`business-objects/local-credential.md`:** type `passkey` realized, label default, signature counter rule.

## API

Delta to `api/openapi.yaml`:

- **New `POST /me/second-factors/passkey`** (`startPasskeyRegistration`, Identity, `authenticated`): body `{label}`, `201 {registrationId, options, expiresAt}`, `401`, `403`, `409` (`passkeys-unavailable`), `413`, `429`.
- **New `POST /me/second-factors/passkey/{registrationId}/confirm`** (`confirmPasskeyRegistration`, Identity, `authenticated`): body = registration response, `200 SecondFactor`, `400` (`invalid-passkey`), `401`, `403`, `404`, `413`, `429`.
- **New `POST /auth/passkey/options`** (`startPasskeyLogin`, Identity, public): `200 {challengeId, options}`, `403` (origin), `409`, `429`.
- **New `POST /auth/passkey/login`** (`passkeyLogin`, Identity, public): body `{challengeId, credential}`, `201 LoginResult`, `401`, `403`, `409`, `413`, `429`.
- **New `POST /auth/local/login/passkey/options`** (`startLocalLoginPasskey`, Identity, public): body `{loginTransaction}`, `200 {challengeId, options}`, `401`, `403`, `429`.
- **New `POST /auth/local/login/passkey`** (`localLoginPasskey`, Identity, public): body `{loginTransaction, challengeId, credential}`, `201 LoginResult`, `401`, `403`, `413`, `429`.
- **`LoginProblem`:** `secondFactors` (array of `totp`, `passkey`).
- **New schemas:**
  - `PasskeyRegistrationStart`, `PasskeyRegistration`, `PasskeyChallenge`, `PasskeyLoginRequest`, `LocalLoginPasskeyRequest`;
  - the WebAuthn JSON objects as `type: object` with `additionalProperties: true`.

## Risks / Trade-offs

- **WebAuthn4J is `0.x`; a minor update may change the API.** → One adapter class (`io.oea.config.webauthn`) and the emulator tests catch changes at the update.
- **Synced passkeys report a signature counter of 0.** → The counter is only enforced when one of the values is not 0, as the WebAuthn specification allows.
- **The relying party is fixed by the public URL; changing the domain invalidates all passkeys.** → The manual names it; persons then fall back to password and TOTP, or the factor reset.
- **The public options endpoint can be called without limit.** → It is subject to the general rate limit (REQ-186), and the challenges expire after 5 minutes.

# US-022: Register Passkey (WebAuthn Credential Registration)

**ID**: US-022
**Story Points**: 8
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-022-passkeys`, commit 2441622 (#142) – `startPasskeyRegistration` and `confirmPasskeyRegistration` under `/me/second-factors/passkey`, page SCR-003

## Story

As a person signing in for the first time with my initial password, I want to register a passkey as part of the login, so that afterwards I get passwordless, phishing-resistant access.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-023: Passkey Enrollment (WebAuthn Credential Registration)](../req/REQ-023-passkey-enrollment.md)

## Acceptance Criteria

**AC1**:

- Given: Kurt has chosen passkey as the method and has a WebAuthn-capable authenticator
- When: he completes the authenticator confirmation
- Then: the public key is persisted; passkey login (UC-01 A3) is possible

**AC2**:

- Given: Kurt cancels the authenticator confirmation
- When: the cancellation occurs
- Then: no credential is stored; the enrollment token remains valid

**AC3**:

- Given: a person already has a registered passkey
- When: they register another one
- Then: both credentials are stored and can each be used individually for login

**AC4**:

- Given: a registration response with a wrong origin or RP ID
- When: the system processes it
- Then: it is rejected; no credential stored

## Technical Notes

- Affected components: enrollment module (WebAuthn challenge generation, attestation verification), credential store
- Affected EntityTypes/Relations: `passkey_credentials` table (`person_id`, `credential_id`, `public_key`, `sign_count`, `aaguid`, `created_at`, `last_used_at`)
- API endpoints: `POST /auth/enrollment/passkey/challenge` (generate challenge); `POST /auth/enrollment/passkey/register` (process attestation and store public key)
- Database changes: new table `passkey_credentials`; multiple rows per person possible

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (virtual WebAuthn authenticator; origin mismatch; multiple registration)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-023 (initial password must be set so that the first login works as trigger), US-009 (passkey login endpoint is the target after enrollment)
- Blocks: US-024 (audit log requires this path)

## Notes

Realigned by OpenSpec change `us-022-passkeys`: there are no enrollment tokens; a person with a local password registers passkeys in the running session after a login within the last 5 minutes, with `POST /me/second-factors/passkey` and `…/{registrationId}/confirm` instead of `/auth/enrollment/passkey/*`. AC2: a cancelled registration expires after 10 minutes and stores nothing. The passkey data is stored in the format of WebAuthn4J (ADR-048): attested credential data, signature counter and backup flags.

8 SP because of WebAuthn complexity: challenge generation, attestation verification (incl. origin check, RP ID check, signature verification), signature counter storage. Attestation format: `none` as default; no attestation CA operation needed. The user handle is the person's opaque UUID, not the email (FIDO2 spec).

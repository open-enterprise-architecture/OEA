# US-021: Set Up TOTP Secret (Register Authenticator App)

**ID**: US-021
**Story Points**: 5
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-021-person-totp` – TOTP enrollment of persons through `startTotpEnrollment` and `confirmTotpEnrollment`, enrollment offer after the password login, page SCR-003

## Story

As a person signing in for the first time with my initial password, I want to set up a TOTP secret in my authenticator app via QR code as part of the login, so that afterwards I get full access with username, password and TOTP code.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-022: TOTP Secret Enrollment](../req/REQ-022-totp-enrollment.md)

## Acceptance Criteria

**AC1**:

- Given: Kurt has chosen TOTP as the method
- When: the page is loaded
- Then: he sees a QR code (key URI format `otpauth://totp/…`) and the secret in plaintext for manual entry

**AC2**:

- Given: Kurt scans the QR code and enters the current 6-digit code
- When: the code is correct (time tolerance ±1 window of 30 s)
- Then: the secret is persisted in encrypted form; login with TOTP (UC-01 A4) is possible

**AC3**:

- Given: Kurt enters a wrong code
- When: he submits the form
- Then: the token remains valid, no secret is stored, Kurt can try again

**AC4**:

- Given: enrollment has been completed
- When: server logs and database entries are present
- Then: the TOTP secret is contained neither in plaintext nor as a hash in logs; in the database it is stored encrypted

## Technical Notes

- Affected components: enrollment module (TOTP secret generation, QR code rendering, verification), credential store
- Affected EntityTypes/Relations: `local_credentials` table (`person_id`, `type=totp`, `secret_encrypted`, `created_at`)
- API endpoints: `GET /auth/enrollment/totp/init` (generate secret, return QR code); `POST /auth/enrollment/totp/verify` (verify code and persist)
- Database changes: row in `local_credentials` (secret AES-256-encrypted)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (correct code, wrong code, secret encryption, log scan)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-023 (initial password must be set so that the first login works as trigger), US-010 (TOTP login endpoint is the target after enrollment)
- Blocks: US-024 (audit log can only write TOTP enrollment events once this path exists)

## Notes

Realigned by OpenSpec change `us-021-person-totp` (owner decision 2026-10-30): there are no enrollment tokens (REQ-021 rejected); the person enrolls in the running session after the password login, where the web portal offers the enrollment and the person can skip it until change 5 makes it mandatory. The endpoints are `POST /me/second-factors/totp` and `/me/second-factors/totp/{credentialId}/confirm`, not `/auth/enrollment/totp/*`. The key URI uses the issuer `OEA` and the e-mail address as account name.

Key URI format: `otpauth://totp/{instance name}:{email}?secret={BASE32}&issuer={instance name}&algorithm=SHA1&digits=6&period=30`. After verification, the secret is no longer held in plaintext on the server; the QR code is displayed only once (no re-display endpoint).

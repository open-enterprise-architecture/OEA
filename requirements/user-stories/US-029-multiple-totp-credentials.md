# US-029: Register Multiple TOTP Authenticators

**ID**: US-029
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a person, I want to be able to register multiple TOTP authenticators on different devices, so that I have a backup authenticator if I lose a device and can log in with TOTP from any of my devices.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-030: Multiple Active TOTP Credentials per Person](../req/REQ-030-multiple-totp-credentials.md)

## Acceptance Criteria

**AC1**:
- Given: Kurt has already registered a TOTP credential
- When: he sets up another TOTP credential in the profile settings (UC-03 A3)
- Then: he afterwards has two active TOTP credentials; the first remains unchanged

**AC2**:
- Given: Kurt has two active TOTP credentials
- When: he logs in and enters the code of his second authenticator
- Then: the login is successful

**AC3**:
- Given: Kurt sets up a new TOTP
- When: he gives the credential a label name (e.g. "Backup-YubiKey")
- Then: this label appears in the overview of his security settings

**AC4**:
- Given: Kurt sets up a TOTP without a label
- When: the credential is saved
- Then: it receives an automatically assigned default label

## Technical Notes

- Affected components: auth module (login verification against multiple TOTP credentials), UC-03 enrollment flow (no more auto-revoke), profile security settings
- Affected EntityTypes/Relations: `local_credentials` (no more `UNIQUE(person_id, type)` for TOTP; new field `label`)
- Database changes: add `label` column to `local_credentials`; remove UNIQUE constraint for `(person_id, type=totp)`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (multi-TOTP login, enrollment without auto-revoke, label default)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-021 (TOTP enrollment foundation), US-025 (additional method via profile settings)
- Blocks: none

## Notes

Mirrors the existing passkey model (multiple passkeys per person allowed from the start). No UI select "which authenticator" is needed at login: the code implicitly identifies the credential. Free-text label field during enrollment ("What would you like to call this device?"). Also affects `local-credential.md` BR-02 (superseded by REQ-030).

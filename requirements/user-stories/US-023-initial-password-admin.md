# US-023: Set Initial Password for a New Person (Admin)

**ID**: US-023
**Story Points**: 2
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-011-local-password` – `POST /admin/persons/{personId}/password` (`setInitialPassword`), required password change `POST /me/password` (`changeOwnPassword`), page SCR-004

## Story

As an administrator, I want to set (or have generated) an initial password when creating a person for local authentication, which is displayed to me once, so that I can pass it on to the person securely.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-024: Set Initial Password by Administrator](../req/REQ-024-initial-password-admin.md)

## Acceptance Criteria

**AC1**:

- Given: Max creates a new person and enables local authentication
- When: he chooses "Generate password" or enters his own
- Then: the hash is stored as `LocalCredential (type=password)` and the plaintext is displayed once

**AC2**:

- Given: the initial password has been displayed
- When: Max leaves the page and comes back
- Then: the plaintext is no longer visible (only the hash in the DB)

**AC3**:

- Given: the initial password is set
- When: the person signs in with it (UC-01 A5)
- Then: the login succeeds; if 2FA is enforced, UC-03 (required action) starts directly in the login flow

**AC4**:

- Given: the system processes the request
- When: server logs are written
- Then: the password plaintext is not contained in the logs

## Technical Notes

- Affected components: admin module (create person), credential store
- Affected EntityTypes/Relations: `local_credentials` table (`person_id`, `type=password`, `password_hash`, `created_at`)
- API endpoints: `POST /admin/persons/{id}/credentials/password` (set hash, return plaintext once)
- Database changes: row in `local_credentials`; hash argon2id/bcrypt

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (set password, hash format, plaintext not retrievable, log scan)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: none (can be built in parallel with login stories)
- Blocks: US-021, US-022 (required action trigger presupposes an existing password)

## Notes

Realigned by OpenSpec change `us-011-local-password`: AC1 – the administrator enters the initial password; generating one follows with US-026. The password is set by a separate operation on an existing person, not when the person is created. AC3 – the enforced second factor as a required action follows with US-031 (2FA enforcement).

v0.2.0: Completely new. v0.1.0 was "person sets own password via enrollment token" (rejected with REQ-021). Now the admin sets the password; no invitation link, no email infrastructure needed. 2 SP because it is conceptually simple – the hash mechanism is reused from REQ-011 (password login).

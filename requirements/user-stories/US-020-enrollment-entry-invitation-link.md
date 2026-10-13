# US-020: Enrollment Entry via Invitation Link

**ID**: US-020
**Story Points**: 3
**Status**: rejected
**Sprint**: unassigned

## Story

As an invited person, I want to reach the enrollment interface via an invitation link, so that I can see which authentication methods I can set up – without having to log in beforehand.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-021: Enrollment Token Validation and Single Use](../req/REQ-021-enrollment-token-validation.md)

## Acceptance Criteria

**AC1**:
- Given: a valid, unconsumed, unexpired enrollment token in the link
- When: Kurt opens the link
- Then: he sees the enrollment interface with the methods enabled for the instance

**AC2**:
- Given: an already consumed or expired token
- When: Kurt opens the link
- Then: he sees a generic error message ("Setup link invalid or expired"), no form

**AC3**:
- Given: a successfully completed enrollment
- When: the same link is opened again
- Then: AC2 applies (token is consumed)

**AC4**:
- Given: the system processes a token
- When: server logs are written
- Then: the token plaintext is not contained in the logs

## Technical Notes

- Affected components: enrollment module (backend), enrollment entry page (frontend)
- Affected EntityTypes/Relations: enrollment token table (`token_hash`, `person_id`, `expires_at`, `consumed_at`)
- API endpoints: `POST /auth/enrollment/validate` (token check; token via request body, not as query parameter, so that it does not end up in server logs)
- Database changes: new table `enrollment_tokens`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (valid, consumed, expired token)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: none (can be built as the first enrollment story)
- Blocks: US-021, US-022, US-023 (all require a valid token as a starting point)

## Notes

**Rejected 2026-06-25**: Enrollment via invitation link rejected. The token approach would have required a secure delivery channel (email or similar). Replaced by just-in-time enrollment as a required action at first login (UC-03 v0.2.0); the user signs in with the initial password and sets up the second factor directly in the login flow. Corresponds to REQ-021 `rejected`.

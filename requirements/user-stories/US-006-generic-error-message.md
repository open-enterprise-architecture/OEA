# US-006: Generic Error Message Without Disclosing Account Existence

**ID**: US-006
**Story Points**: 2
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec changes `us-013-system-admin-login` (local login of the system admin account) and `us-011-local-password`, commit 88e71f8 (#133) (persons) – identical answers, one password hash verification per failed login

## Story

As a security officer, I want failed login attempts to allow no conclusions about the existence of an account, so that attackers cannot perform user enumeration.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-006: No Disclosure of Account Existence](../req/REQ-006-no-account-existence-disclosure.md)

## Acceptance Criteria

**AC1**:

- Given: a login attempt with a non-existent identity
- When: the login fails
- Then: the error message is identical to that for an existing but incorrectly authenticated identity

**AC2**:

- Given: two failed login attempts (existing vs. non-existent identity)
- When: the response times are compared
- Then: they do not differ in an analyzable way (no timing side channel)

## Technical Notes

- Affected components: auth module (error handling, all mechanisms)
- Affected EntityTypes/Relations: none
- API endpoints: all login endpoints
- Database changes: none

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person (with focus on security)
- [ ] Tests written (incl. timing comparison)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001, US-004
- Blocks: none

## Notes

AC2 realized by OpenSpec change `us-011-local-password`: every failed variant (unknown name, wrong password, expired temporary password, person not active, password login of persons off) runs exactly one argon2id verification; a test counts the verifications instead of measuring times, which would be unreliable in CI.

Should be verified again for every new local mechanism (US-010, US-011), since password correctness is an additional leak vector there (see REQ-010).

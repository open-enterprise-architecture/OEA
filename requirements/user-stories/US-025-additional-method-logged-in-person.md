# US-025: Add Another Authentication Method as a Logged-In Person

**ID**: US-025
**Story Points**: 3
**Status**: in-progress
**Sprint**: S5
**Implementation**: OpenSpec change `us-021-person-totp` – TOTP in the profile through the same enrollment operations (AC1), notice instead of an error (AC3), session required (AC4), list of own second factors on SCR-005

## Story

As an already logged-in person, I want to set up another local authentication method in my security settings, so that I retain access after losing a device or as a backup option – without having to request a new invitation link.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-026: Set Up Additional Authentication Method for a Logged-In Person](../req/REQ-026-additional-method-authenticated-person.md)

## Acceptance Criteria

**AC1**:

- Given: Kurt is logged in and has not yet set up a TOTP method
- When: he adds TOTP in the security settings and completes the setup
- Then: TOTP is persisted as an additional method; no enrollment token was needed

**AC2**:

- Given: Kurt is logged in and wants to add a second passkey
- When: he performs the passkey registration
- Then: the new public key is persisted; both passkeys can be used for login

**AC3**:

- Given: Kurt has already set up all methods available for the instance
- When: he opens the security settings
- Then: he sees an informational notice, not an error

**AC4**:

- Given: a request to the enrollment endpoint without a valid session
- When: the request arrives
- Then: it is rejected and redirected to the login

## Technical Notes

- Affected components: enrollment module (reuses US-021/022/023 endpoints with session auth instead of token auth), profile/security settings page (frontend)
- Affected EntityTypes/Relations: no new ones; existing `local_credentials` and `passkey_credentials` tables
- API endpoints: the same enrollment endpoints as US-021/022/023, but reachable via an authenticated gate path (session instead of token); CSRF protection via SameSite=Strict cookie
- Database changes: none new; entry in existing `local_credentials` or `passkey_credentials` table

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (logged-in person adds method; unauthenticated access; all methods already present)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-021, US-022 or US-023 (at least one method-specific enrollment endpoint must exist); UC-01 login must work (session foundation)
- Blocks: none

## Notes

Realigned by OpenSpec change `us-021-person-totp`: the enrollment at the first login and in the profile use one code path in the session; there is no enrollment token. AC2 (second passkey) follows with S5 change 4, so the story stays in progress. AC3 is the list of authenticator apps on the profile page with "Add an authenticator app".

3 SP because the actual enrollment code from US-021/022/023 is reused; the additional effort lies in the session auth gate, the security settings page in the frontend and the CSRF tests. Diagram: the enrollment code path should have a single auth adapter (token or session), no duplicated enrollment code.

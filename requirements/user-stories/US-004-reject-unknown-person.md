# US-004: Rejection of Session Creation for Unknown or Offboarded Persons

**ID**: US-004
**Story Points**: 2
**Status**: done
**Sprint**: S1
**Implementation**: OpenSpec change `us-001-oidc-login`, commit a5bbdb8 (#35) – unknown pairs (`iss`, `sub`) and persons in a status other than `invited` or `active` get no session (spec `identity/oidc-login`, "Person found by immutable identity binding")

## Story

As an operator, I want persons who are no longer (or not) known as active in the repository to receive no session, so that former employees do not gain access despite a valid IdP token.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-004: Rejection of Unknown/Offboarded Person](../req/REQ-004-reject-unknown-offboarded-person.md)

## Acceptance Criteria

**AC1**:

- Given: a valid identity token that cannot be mapped to any person in the repository
- When: the login process is completed
- Then: no session is created

**AC2**:

- Given: a valid identity token whose associated person has the status `offboarded`
- When: the login process is completed
- Then: no session is created

## Technical Notes

- Affected components: auth module (person lookup)
- Affected EntityTypes/Relations: `Person` (lifecycle status)
- API endpoints: OIDC callback endpoint
- Database changes: index on `Person.externalReference` for fast lookup

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001
- Blocks: US-005 (audit log should also cover this case)

## Notes

Set to done on 2026-10-29 in the re-scoping of S4: AC1 and AC2 are covered by the scenarios "Person not active" and "Unknown and inactive identities" of the spec `identity/oidc-login`.

Covers exception flow E2 from UC-01.

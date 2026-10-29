# US-148: Revoke All Access on Offboarding

**ID**: US-148
**Story Points**: 3
**Status**: done
**Sprint**: S4
**Implementation**: OpenSpec change `us-148-person-lifecycle` – `POST /admin/persons/{personId}/status` (`changePersonStatus`)

## Story

As Max – Operator, I want to offboard a leaving person in one step, so that all their sessions end within a minute, all their local role assignments are ended, and their history and audit trail are retained – even while their IdP account is still active.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (A2)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-164](../req/REQ-164-session-termination-revocation.md), [REQ-004](../req/REQ-004-reject-unknown-offboarded-person.md), [REQ-175](../req/REQ-175-security-event-logging.md)

## Acceptance Criteria

**AC1** (Sessions revoked):

- Given: Anna is `active`, holds `enterprise-architect` locally and `viewer` through an IdP group mapping, and has an active session and refresh token
- When: Max (with `person:manage`) sets Anna's status to `offboarded`
- Then: within 60 s every request with her previous session, access token or refresh token, including open event streams, is rejected with 401, and an audit event `auth.session-revoked` exists

**AC2** (Local assignments ended):

- Given: the situation after AC1
- When: Max opens Anna's assignments
- Then: every local assignment shows `revokedAt` and `revokedBy=Max`, and an audit event `role.revoked` exists per assignment

**AC3** (IdP-derived roles no longer apply):

- Given: Anna is `offboarded` and still a member of the mapped IdP group
- When: she logs in through the IdP
- Then: no session is created (REQ-004), so no IdP-derived role applies

**AC4** (History retained):

- Given: Anna is `offboarded`
- When: an auditor reads the audit log and the entity history
- Then: Anna's past changes and audit events are still attributed to her

**AC5** (Missing permission):

- Given: a principal holding `role:assign` but not `person:manage` (custom role)
- When: it sets Anna's status to `offboarded`
- Then: the response is 403

## Technical Notes

- Affected components: person lifecycle, session store, authorization module
- Affected EntityTypes/Relations: `persons.status`, `role_assignments`, sessions
- API endpoints: person status change (contract to be defined, ADR-032 API-4 context)
- Database changes: depends on the lifecycle `status` column (ADR-032 DM-7)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (integration with controlled clock)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-144, US-004
- Blocks: –

## Notes

Realigned by OpenSpec change `us-148-person-lifecycle`: AC1 – the revoked sessions are audited as the existing `session.revoked` (reason `person-status-change`), one per session; OEA issues no refresh tokens and has no event streams yet, so the sessions are the only credentials. AC5 – the operation needs `person:manage` alone. Reactivation of offboarded persons (setting `allowReactivationOfOffboarded`) and status changes of service accounts (US-142) are not part of this change.

Offboarding the last path to `system-admin` is rejected (US-146 AC5).

# US-146: Protect the Last Administration Path

**ID**: US-146
**Story Points**: 3
**Status**: done
**Sprint**: S4
**Implementation**: OpenSpec change `us-144-local-role-assignments`, commit b15925e (#125) – one SoD-7 check under an advisory lock for revoking assignments, deactivating the local system admin account and deleting, deactivating or retargeting IdP group mappings

## Story

As Max – Operator, I want the system to reject any change that would leave the instance without a usable path to `system-admin`, so that administration of the instance cannot be lost through a revocation, an expiry or a deactivation.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (E3)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-157](../req/REQ-157-permission-model.md), [REQ-019](../req/REQ-019-disable-local-admin.md)

## Acceptance Criteria

**AC1** (Revoking the last holder):

- Given: Erika is the only active person holding `system-admin`, the local system admin account is deactivated, and no active IdP group mapping to `system-admin` exists
- When: Max (with `role:assign`) revokes Erika's `system-admin` assignment
- Then: the response is 409 with the message that at least one administration path must remain, and the assignment stays active

**AC2** (Setting `validTo` on the last holder):

- Given: the same situation as AC1
- When: Max sets `validTo` on Erika's `system-admin` assignment
- Then: the response is 409 and the assignment is unchanged

**AC3** (Deactivating the local system admin account):

- Given: the local system admin account is active and no person holds `system-admin` and no IdP group mapping to `system-admin` exists
- When: the local system admin account is to be deactivated
- Then: the response is 409 (consistent with REQ-019)

**AC4** (Another path exists):

- Given: Erika and Paul both hold `system-admin` as active persons
- When: Max revokes Erika's assignment
- Then: the revocation succeeds

**AC5** (Person status change):

- Given: Erika is the only path to `system-admin`
- When: a principal with `person:manage` sets Erika to `inactive` or `offboarded`
- Then: the response is 409 and Erika's status is unchanged

## Technical Notes

- Affected components: authorization module (application layer, SoD-7 invariant), person lifecycle, system admin account
- Affected EntityTypes/Relations: `role_assignments`, `idp_group_mappings`, `system_admin_account`, `persons.status`
- API endpoints: revocation, assignment update, IdP mapping deletion, person status change, system admin account deactivation; error code 409 per ADR-032 API-9
- Database changes: none; the check must be safe against concurrent revocations (two parallel requests must not both succeed)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration, concurrency test for two parallel revocations)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-144, US-019
- Blocks: –

## Notes

Realigned by OpenSpec change `us-144-local-role-assignments`: AC2 – the contract has no operation that changes an existing assignment; `validTo` is set only at creation, and an assignment with `validTo` never counts as a usable path, so its expiry cannot remove the last path. AC3 – a `system-admin` assignment without `validTo` of an active person counts as another path. AC5 (person status change) is implemented by OpenSpec change `us-148-person-lifecycle` with the same check. A mapping counts as a path only after it has granted the role in a login (us-019-admin-safeguards).

Walking skeleton (S1–S2) covers AC1, AC2 and AC4; AC3 and AC5 follow with the system admin account deactivation and person lifecycle stories. Deletion of an IdP group mapping to `system-admin` is covered in US-147.

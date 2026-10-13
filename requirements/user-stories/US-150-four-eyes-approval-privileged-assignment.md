# US-150: Four-Eyes Approval for Privileged Role Assignments

**ID**: US-150
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Max – Operator of a regulated instance with several administrators, I want to optionally require that a second administrator approves every assignment of a privileged role, so that privileged access is granted under dual control and I can evidence this in FINMA/DORA audits.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (A7)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-194](../req/REQ-194-privileged-assignment-four-eyes-approval.md), [REQ-175](../req/REQ-175-security-event-logging.md)

## Acceptance Criteria

**AC1** (Default off with notification):

- Given: the default configuration (`privilegedAssignmentApproval` disabled)
- When: Max assigns `security-admin` to Erika
- Then: the assignment is active immediately, audited with high severity, and all other administrators are notified

**AC2** (Pending request):

- Given: `privilegedAssignmentApproval` is enabled
- When: Max assigns `system-admin` to Erika
- Then: the assignment is `pending`, Erika's permissions are unchanged, eligible approvers are notified, and an audit event `role.assignment-requested` exists

**AC3** (Approval):

- Given: the pending request of AC2
- When: Paul (holding `role:assign`, logged in with a second factor, neither requester nor assignee) approves it
- Then: the assignment is active with `approvedBy=Paul` and effective within 60 s; an audit event `role.assignment-approved` exists

**AC4** (Self-approval rejected):

- Given: the pending request of AC2
- When: Max or Erika tries to approve it
- Then: the response is 403 with reason `self-approval` and the request stays `pending`

**AC5** (Rejection and expiry):

- Given: a pending request
- When: an approver rejects it with a reason, or the approval timeout elapses
- Then: the request is `rejected` or `expired`, never grants permissions, cannot be approved afterwards (409), and the corresponding audit event exists

**AC6** (Enabling requires two administrators):

- Given: only one active person holds `role:assign`
- When: a principal with `settings:manage` tries to enable the setting
- Then: the change is rejected with 422

## Technical Notes

- Affected components: authorization module, settings, notification, admin UI (pending requests list)
- Affected EntityTypes/Relations: `role_assignments` (state, requester, approver), instance settings
- API endpoints: approval and rejection operations for pending assignments (contract to be defined); settings
- Database changes: assignment state and approval attributes, setting `privilegedAssignmentApproval` and approval timeout – not yet in the data model (handover to the business engineer, see REQ-194 Implementation Notes)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (integration; controlled clock for expiry)
- [ ] Documentation updated (operator guide: when to enable)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-144, US-145
- Blocks: –

## Notes

Not part of the walking skeleton. Open points (IdP group mappings to privileged roles, timeout default, notification recipients) are listed in REQ-194 Notes.

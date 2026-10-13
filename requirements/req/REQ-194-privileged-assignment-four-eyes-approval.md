---
id: REQ-194
title: Optional four-eyes approval for privileged role assignments
type: business-rule
priority: should
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-23
  business_objects:
    - role
    - person
  business_rules: []
  stakeholders:
    - SH-06
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-194: Optional Four-Eyes Approval for Privileged Role Assignments

## Statement

When the operator setting `privilegedAssignmentApproval` is enabled (default: disabled), the system **MUST** keep every privileged change in the state `pending` – without any effect on permissions – namely (a) a new local assignment of a privileged authorization role, (b) a new IdP group mapping to a privileged role or a change of a mapping's target to a privileged role, and (c) adding an admin-class permission to a custom role (owner decision Q18), until a second principal holding `role:assign`, who is neither the requester nor the assignee, approves it; when the setting is disabled, the system **MUST** notify all administrators of every new privileged role assignment.

## Rationale

ADR-032 prevents self-administration (SoD-1) but cannot prevent two administrators from colluding (e.g. a security admin assigning `system-admin` to a colleague who reciprocates). Regulated operators (SH-06, FINMA/DORA audits) need to demonstrate that privileged access is granted under dual control. Small organizations with a single administrator cannot use dual control and rely on audit and notification instead.

## Context

Owner decision Q15 (2026-10-13, ADR-032 §6): four-eyes approval for privileged role assignments is optional and chosen by the operator; setting `privilegedAssignmentApproval`, default off. Assignments are always audited and all administrators are notified.

A role is **privileged** if it contains at least one admin-class permission (ADR-032 §3: `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`); built-in privileged roles are `metamodel-admin`, `security-admin` and `system-admin`. Service accounts never hold privileged roles (SoD-4), so the rule concerns persons only.

## Type-Specific Fields

### For type=business-rule

**Trigger**: onCreate of a local role assignment; onUpdate of an approval request (approve, reject); continuous (expiry); onUpdate of the setting `privilegedAssignmentApproval`

**Affected business objects**: [role](../../business-objects/role.md) (authorization role and role assignment, ADR-032 DM-1/DM-4), [person](../../business-objects/person.md)

**Formal statement**:

```text
privileged(role) = role.permissions ∩ adminClass ≠ ∅

on create assignment a (role r, assignee p, requester q):
    if privileged(r) and settings.privilegedAssignmentApproval:
        a.state = pending                       # no effect on effectivePermissions(p)
        notify(approvers(a))
    else:
        a.state = active
        if privileged(r): notify(allAdministrators)
    audit(role.assignment-requested | role.assigned, a)

approvers(a) = { x | x holds role:assign, x.status = active,
                     x ≠ a.requester, x ≠ a.assignee }   # SoD-1 applies to approvers

on approve(a, by x):
    require a.state = pending and x in approvers(a) and x authenticated with second factor
    re-check SoD-1, SoD-4, assignee status and role existence at approval time
    a.state = active; a.approvedBy = x; a.approvedAt = now
    audit(role.assignment-approved, a)           # sodConflict flag as for role.assigned

on reject(a, by x):
    require a.state = pending and x in approvers(a) ∪ {a.requester}
    a.state = rejected; audit(role.assignment-rejected, a, reason)

on now >= a.requestedAt + approvalTimeout and a.state = pending:
    a.state = expired; audit(role.assignment-expired, a); notify(a.requester)
```

**Example**: The operator has enabled `privilegedAssignmentApproval`. Max (security admin) assigns `metamodel-admin` to Sabine. The assignment is shown as pending; Sabine still has only her previous permissions. Erika, a second security admin, approves it; from the next request on (at the latest after 60 s) Sabine holds `metamodel:write`. Max cannot approve his own request, and Sabine cannot approve an assignment to herself.

## Acceptance Criteria

**AC1** (Default off, notification):

- Given: a fresh instance with the default configuration (`privilegedAssignmentApproval` disabled) and two active persons holding `role:assign`
- When: Max assigns `security-admin` to a person
- Then: the assignment is active immediately, the audit event `role.assigned` is written with high severity, and every active administrator except the actor receives a notification naming actor, assignee and role

**AC2** (Pending without effect):

- Given: `privilegedAssignmentApproval` is enabled
- When: Max assigns `system-admin` to Erika
- Then: the assignment has the state `pending`, Erika's effective permissions are unchanged (403 on an operation requiring `settings:manage`), the audit event `role.assignment-requested` exists, and all eligible approvers are notified

**AC3** (Approval by a second administrator):

- Given: a pending assignment of `system-admin` to Erika requested by Max
- When: a third principal Paul, holding `role:assign` and logged in with a second factor, approves it
- Then: the assignment becomes active with `approvedBy = Paul`, Erika's next request after at most 60 s is authorized with `settings:manage`, and the audit event `role.assignment-approved` exists

**AC4** (Requester and assignee cannot approve):

- Given: a pending assignment of `security-admin` to Erika requested by Max, where Erika already holds `role:assign` through another role
- When: Max or Erika tries to approve it
- Then: the request is rejected with 403, the assignment stays `pending`, and an audit event `authz.denied` with reason `self-approval` exists

**AC5** (Rejection):

- Given: a pending privileged assignment
- When: an eligible approver or the requester rejects it with a reason
- Then: the assignment has the state `rejected`, never grants permissions, cannot be approved afterwards (409), and the audit event `role.assignment-rejected` with the reason exists

**AC6** (Expiry):

- Given: a pending privileged assignment and the approval timeout (default 7 days)
- When: the timeout elapses without approval or rejection
- Then: the assignment has the state `expired`, never grants permissions, cannot be approved afterwards (409), the audit event `role.assignment-expired` exists, and the requester is notified

**AC7** (Non-privileged roles unaffected):

- Given: `privilegedAssignmentApproval` is enabled
- When: Max assigns `viewer` or `enterprise-architect` to a person
- Then: the assignment is active immediately without approval

**AC8** (Enabling requires a second administrator):

- Given: only one active person holds `role:assign` (the local system admin account not counted)
- When: a principal with `settings:manage` tries to enable `privilegedAssignmentApproval`
- Then: the change is rejected with 422 and a message that approval requires at least two administrators; every successful change of the setting writes an audit event with old and new value

**AC10** (IdP group mapping to a privileged role):

- Given: `privilegedAssignmentApproval` is enabled
- When: an administrator creates an IdP group mapping to `security-admin`
- Then: the mapping is `pending` and not evaluated at login or token refresh until a second administrator approves it

**AC11** (Admin-class permission on a custom role):

- Given: `privilegedAssignmentApproval` is enabled and a custom role `data-steward` without admin-class permissions
- When: an administrator adds `visibility:configure` to `data-steward`
- Then: the permission is `pending`, holders of `data-steward` do not gain it, and it becomes effective only after approval by a second administrator

**AC9** (Revocation not delayed):

- Given: `privilegedAssignmentApproval` is enabled
- When: a principal with `role:assign` revokes a privileged assignment
- Then: the revocation takes effect without approval (at the latest after 60 s, REQ-164)

## Verification Method

- [ ] Method: test (automated, controlled clock for AC6)
- [ ] Test setup: instance with three persons holding `role:assign` and one target person; setting toggled per test
- [ ] Pass criterion: AC1–AC9 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-157 (permission model, admin-class permissions), REQ-171 (MFA for privileged accounts), REQ-175 (security event logging)
- **Follow-on effects**: REQ-164 (effect of activation within 60 s)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: collusion of two administrators grants privileged access without independent control – medium (detected by audit and access review, not prevented)
- Risk 2: regulated operators cannot evidence dual control of privileged access in audits – medium

## Trade-offs

- vs. operability in small organizations: approval requires at least two administrators; therefore optional and off by default
- vs. speed of onboarding administrators: a pending assignment delays privileged access until approval

## Implementation Notes

- Data impact (handover to the business engineer): role assignment needs a state (`pending | active | rejected | expired`, revoked assignments keep `revokedAt`), `requestedBy`, `requestedAt`, `approvedBy`, `approvedAt`, `decisionReason`; instance settings need `privilegedAssignmentApproval` (boolean, default `false`) and the approval timeout (duration, default 7 days). Neither exists in `docs/data-model.puml` yet (ADR-032 DM-4 covers only active/revoked assignments).
- The notification channel (in-app, e-mail) is not defined by this requirement.
- The access review (UC-23 A8) should list pending requests.

## Notes

Open points for owner/PO decision:

- Whether IdP group mappings to privileged roles and changes that add an admin-class permission to an existing custom role are subject to approval as well; both widen privileged access without a local assignment. This requirement covers local assignments only.
- Approval timeout default (7 days proposed) and whether it is configurable.
- Recipients of the notification when approval is disabled: proposed as all active persons holding `role:assign`; alternatively all holders of any privileged role.
- Behavior of pending requests when the setting is disabled again: proposed that they remain pending and still require approval (no automatic activation).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from UC-23, ADR-032 §6 and owner decision Q15 |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Scope extended to IdP group mappings to privileged roles and admin-class permissions on custom roles (owner decision Q18); AC10, AC11 |

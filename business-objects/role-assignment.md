---
identifier: role-assignment
name_de: Rollenzuweisung (Berechtigung)
name_en: Role Assignment
version: 0.2.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Authorization Role Assignment
  - Access Grant
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: Role Assignment

## Definition

A role assignment grants one [authorization role](./authorization-role.md) to one [person](./person.md) account (human or service account) for a validity period. It is the local source of roles (`source = local`) and is never hard-deleted.

## Description

Role assignments are created by principals with `role:assign` (UC-23). They answer the audit question "who may do what, since when, granted by whom and why" (REQ-175, SH-06). Revocation, expiry and rejection keep the record; the runtime database role has no DELETE grant on the table.

Assignments to persons in status `invited` are allowed (pre-provisioning); they become effective once the person is `active`. Persons in `inactive` keep their assignments but have no access; offboarding revokes all local assignments (UC-23 A2).

**Four-eyes approval** (owner decision Q15): if the operator setting `privilegedAssignmentApproval` is on (default off) and the role is privileged, a new assignment is created in status `pending` and becomes `granted` only after a second administrator – neither the requester nor the assignee – approves it. All administrators are notified of privileged assignments regardless of the setting.

IdP-derived roles are not stored as role assignments; they are derived per session from [IdP group mappings](./idp-group-mapping.md) and recorded in the [session](./session.md).

The EA connection "RoleAssignment" between a Person entity and a business [role](./role.md) (§8.3, with `assignmentType` and `percentage`) is a different object; it grants nothing.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID | Primary key |
| person | reference | required | | target: person (human or service account); immutable | Assignee |
| role | reference | required | | target: authorization-role (not deleted); immutable; for service accounts only roles with `assignableToServiceAccounts` (SoD-4) | Assigned role |
| source | enum | required | local | `[local]`; extensible (e.g. declarative provisioning, pending ADR) | Origin of the assignment |
| status | enum | required | granted | `[pending, granted, rejected, revoked]` | Approval/revocation state (see lifecycle) |
| validFrom | datetime | required | now | TIMESTAMPTZ | Start of validity |
| validTo | datetime | optional | | > validFrom | End of validity (time-boxed access); reaching it expires the assignment without status change |
| reason | string | optional | | max. 500 characters | Justification, e.g. ticket number |
| grantedAt | datetime | required | now | | Creation time (request time if approval is required) |
| grantedBy | principal | required | | person or system admin account; never the assignee (SoD-1) | Creator (requester if approval is required) |
| sodConflict | boolean | required | false | set at creation | The assignment combined `metamodel:write` with visibility permissions for the person and was acknowledged (SoD-3, BR-03) |
| approvalRequired | boolean | required | false | snapshot at creation: role privileged AND `privilegedAssignmentApproval` on | Whether four-eyes approval applies |
| decidedAt | datetime | conditional | | required if `approvalRequired` and status `granted`/`rejected` | Time of the approval decision |
| decidedBy | principal | conditional | | ≠ grantedBy, ≠ assignee, holds `role:assign` | Approver/rejecter |
| decisionComment | string | optional | | max. 500 characters | Reason for approval or rejection |
| revokedAt | datetime | conditional | | required iff status `revoked` | Time of revocation |
| revokedBy | principal | conditional | | never the assignee (SoD-1) | Revoker |
| revocationReason | string | optional | | max. 500 characters | e.g. "offboarding", "mover" |
| expiryRecordedAt | datetime | optional | | set once by the expiry job | When the expiry after `validTo` was audited (`role.assignment-expired`); effectiveness depends on `validTo` only |

`principal` = reference to a person or to the system admin account (two FK columns, exactly one set).

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| assignedTo | [person](./person.md) | n:1 | no | Assignee account |
| grants | [authorization-role](./authorization-role.md) | n:1 | no | Assigned role |
| grantedBy / decidedBy / revokedBy | [person](./person.md) or [system-admin-account](./system-admin-account.md) | n:1 | partly | Acting principals |

## Lifecycle

```text
          ┌──────── approvalRequired ────────┐
(create) ─┤                                   ▼
          │                               pending ──► rejected
          ▼                                  │
       granted ◄──────── approved ───────────┘
          │        (expires at validTo, no status change)
          ▼
       revoked            (pending → revoked = withdrawn)
```

**States**:

- `pending`: privileged assignment awaiting approval (Q15); not effective
- `granted`: effective while `validFrom ≤ now < validTo` and the person is `active`
- `rejected`: approval refused; terminal
- `revoked`: withdrawn before or during validity; terminal

**Transitions**:

- `→ granted` / `→ pending`: principal with `role:assign`; audit `role.assigned` (high severity if privileged; `sodConflict: true` for SoD-3 combinations); notification to all administrators for privileged roles
- `pending → granted | rejected`: second administrator with `role:assign`; audit `role.assignment-approved` / `role.assignment-rejected`
- `pending | granted → revoked`: principal with `role:assign`, or automatically when the person is offboarded; audit `role.revoked`; all sessions of the person are revoked (REQ-164)
- expiry at `validTo`: audit `role.assignment-expired`; sessions revoked

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | A principal MUST NOT create, approve, change or revoke its own assignments; the system admin account counts as the person it is linked to (SoD-1) | onCreate, onUpdate | ADR-032 SoD-1 |
| BR-02 | Assignments to service accounts are restricted to roles with `assignableToServiceAccounts = true`; roles with an admin-class permission are never assignable to service accounts (422) | onCreate | ADR-032 SoD-4, REQ-176 |
| BR-03 | Holding `metamodel:write` and `visibility:configure`/`visibility-scope:assign` through different roles is accepted but flagged (`sodConflict`) and listed in the access review (SoD-3) | onCreate | Q13 |
| BR-04 | Revoking, expiring (`validTo`) or rejecting an assignment that is the last usable path to `system-admin` is rejected with 409 (SoD-7) | onUpdate | ADR-032 SoD-7 |
| BR-05 | Role assignments are never hard-deleted | onDelete | REQ-175 |
| BR-06 | If `approvalRequired`, the approver MUST differ from `grantedBy` and from the assignee | onUpdate | Q15 |
| BR-07 | A privileged assignment is effective in a session only if the session has a verified second factor (SoD-8) | onRequest | REQ-171 |
| BR-08 | Any revocation or expiry revokes all sessions of the assignee; changes are effective within 60 s (authorization cache) | onUpdate | REQ-164 |

## Examples

**Example 1**: Time-boxed assignment for an external consultant

```yaml
person: 1042
role: enterprise-architect
source: local
status: granted
validFrom: 2026-10-14T00:00:00Z
validTo: 2027-03-31T23:59:59Z
reason: "CHG-4711 external consultant"
grantedBy: { person: 7 }
approvalRequired: false
```

**Example 2**: Privileged assignment with four-eyes approval

```yaml
person: 15
role: security-admin
status: pending
grantedBy: { person: 7 }
approvalRequired: true
# later: status granted, decidedBy { person: 9 }, decidedAt set
```

## Distinction from Similar Objects

- **NOT** the EA connection `role-assignment` (§8.3): links a Person entity to a business role; no access effect.
- **NOT** [idp-group-mapping](./idp-group-mapping.md): derives roles from IdP group membership, per session.

## Usage in Use Cases

- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md)
- [UC-01: Login](../requirements/use-cases/UC-01-login.md)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

The API shows a derived `status`: `revoked` if revoked, `expired` once `validTo` has passed, `scheduled` before `validFrom`, otherwise `active` (change `us-144-local-role-assignments`, design D1). `pending` and `rejected` occur only with four-eyes approval (US-150).

Created for ADR-032 DM-4 and owner decision Q15. Replaces the table `person_roles` (composite key with `valid_from DATE`, `assignment_type`, `percentage`); `assignment_type` and `percentage` belong to the EA RoleAssignment connection. Approval is modeled on the assignment itself (status + decision fields) rather than a separate approval record because an assignment has at most one approval decision.

Open: whether IdP group mappings to privileged roles also require four-eyes approval when `privilegedAssignmentApproval` is on.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft from ADR-032 DM-4 and owner decision Q15 |
| 0.2.0 | 2026-10-29 | Business Engineer | `sodConflict`, `expiryRecordedAt`; API status `scheduled`/`active`/`expired`/`revoked` derived from status and validity (change `us-144-local-role-assignments`) |

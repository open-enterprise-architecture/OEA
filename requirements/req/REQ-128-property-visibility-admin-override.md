---
id: REQ-128
title: Admin Override for Property Visibility
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - metamodel-configuration
    - role
    - entity
  stakeholders:
    - SH-03
    - SH-06
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-128: Admin Override for Property Visibility

## Statement

The system MUST provide a configurable **admin override**: users with a system role marked as `overridesPropertyVisibility=true` (default: `system-admin`) MUST be able to read all properties, regardless of `visibilityMode`. The override status MUST be recorded in the audit log when a restricted property is read by an override user. The list of override roles MUST be configurable only by principals holding the security administration permission `visibility:configure` (not by metamodel administrators); every change of the list MUST be audited, and a change that adds a role the acting principal holds MUST be flagged as self-benefiting (REQ-161).

## Rationale

Without an admin override, security administrators and system admins are blind in an emergency (e.g. error analysis, data migration) to fields they restricted themselves. The override is explicitly configurable instead of hardcoded so that it can be adapted to the organization.

Security review SEC-03 / owner decision Q4 (2026-10-13): if the metamodel editor (EA manager) can add their own role to the override list, the restriction is circumvented by the people it is meant to restrict. Override configuration therefore belongs to security administration, separate from metamodel administration.

## Acceptance Criteria

**AC1** (Override effective):

- When: a user with the `system-admin` role retrieves an entity with restricted properties
- Then: all values are returned; `visibilityMode` has no effect

**AC2** (Audit log entry):

- When: an override user reads a `role-restricted` or `connection-scoped` field
- Then: the audit log contains an entry with type `property-override-access`, user ID, entity ID, and property name

**AC3** (Override configurable by security administration only):

- Given: a security administrator with `visibility:configure`
- When: they add another role to the override role list
- Then: users of this role receive override access from now on, and an audit event `visibility.override-changed` with actor, old and new list exists

**AC4** (No implicit override):

- When: a user has the `EA-Manager` role, but `EA-Manager` is not in the override list
- Then: visibility restrictions apply normally; no automatic override through a hierarchy assumption

**AC5** (Metamodel administrators cannot change the override):

- Given: a user with metamodel administration permission but without `visibility:configure`
- When: they try to change the override role list via UI or API
- Then: the request is rejected with 403 and the list is unchanged

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-126 (enforcement), REQ-005 (audit log), REQ-157 (permission model), REQ-161 (governance of visibility configuration)
- **Consequences**: –

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-03 / owner decision Q4: override list configurable only with `visibility:configure`, audited, self-benefit flagged (AC3 rewritten, AC5 added) |

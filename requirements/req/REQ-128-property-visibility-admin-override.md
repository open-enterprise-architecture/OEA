---
id: REQ-128
title: Admin Override for Property Visibility
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

The system MUST provide a configurable **admin override**: users with a system role marked as `overridesPropertyVisibility=true` (default: `system-admin`) MUST be able to read all properties, regardless of `visibilityMode`. The override status MUST be recorded in the audit log when a restricted property is read by an override user. The list of override roles MUST be configurable in the metamodel editor (EA manager can add or remove further roles).

## Rationale

Without an admin override, EA managers and system admins are blind in an emergency (e.g. error analysis, data migration) to fields they restricted themselves. The override is explicitly configurable instead of hardcoded so that it can be adapted to the organization.

## Acceptance Criteria

**AC1** (Override effective):

- When: a user with the `system-admin` role retrieves an entity with restricted properties
- Then: all values are returned; `visibilityMode` has no effect

**AC2** (Audit log entry):

- When: an override user reads a `role-restricted` or `connection-scoped` field
- Then: the audit log contains an entry with type `property-override-access`, user ID, entity ID, and property name

**AC3** (Override configurable):

- When: Kurt edits the override role list in the metamodel editor and adds another role
- Then: users of this role receive override access from now on

**AC4** (No implicit override):

- When: a user has the `EA-Manager` role, but `EA-Manager` is not in the override list
- Then: visibility restrictions apply normally; no automatic override through a hierarchy assumption

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-126 (enforcement), REQ-005 (audit log)
- **Consequences**: –

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

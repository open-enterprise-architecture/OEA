---
id: REQ-128
title: Admin Override for Property Visibility
type: functional
priority: should
status: proposed
version: 0.4.0
created: 2026-06-28
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
    - UC-23
  business_objects:
    - metamodel-configuration
    - authorization-role
    - entity
  stakeholders:
    - SH-03
    - SH-06
  adrs:
    - ADR-032
    - ADR-034
supersedes: []
superseded_by: []
---

# REQ-128: Admin Override for Property Visibility

## Statement

The system MUST provide a configurable **property-visibility override**: principals holding an authorization role marked `overridesPropertyVisibility=true` MUST be able to read all properties of the objects they can already read, regardless of `visibilityMode`. The override role list MUST be **empty by default** – no role, including `system-admin` and `security-admin`, holds the override unless it is added explicitly; without the override, access to a restricted property is granted only through its `readRoles` (REQ-124, REQ-126). The override MUST NOT grant access to objects the principal cannot otherwise read. Every read of a restricted property through the override MUST be recorded in the audit log. The override MUST NOT apply to raw analytics (REQ-179). The override role list MUST be changeable only by principals holding `visibility:configure` (not by metamodel administrators); every change of the list MUST be audited, and a change that adds a role the acting principal holds MUST be flagged as self-benefiting (REQ-161).

## Rationale

Exceptional situations (error analysis, data migration) may require a principal to see restricted fields without being added to the `readRoles` of every property. The override is explicitly configurable instead of hardcoded so that it can be adapted to the organization.

Security review SEC-03 / owner decision Q4 (2026-10-13): if the metamodel editor (EA manager) can add their own role to the override list, the restriction is circumvented by the people it is meant to restrict. Override configuration therefore belongs to security administration, separate from metamodel administration.

Owner decision Q14 (2026-10-13, ADR-032 §4): nobody reads restricted properties by default, not even administrators. Administrators are not implicitly trusted with restricted content (e.g. security classifications, personal data categories); access follows `readRoles` like for any other role, and the override is an explicit, audited exception.

ADR-034 §3.2: the override does not apply to raw analytics, because the audit record required per override read cannot be produced per value inside SQL. Principals read restricted properties in raw analytics only through `readRoles` or connection scope.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Override role list (set of authorization role IDs), default: empty

**Processing**:

- On every read channel (REQ-160) except raw analytics (REQ-179): a restricted property is readable if the principal holds a role in its `readRoles`, satisfies the connection scope (REQ-127), or holds a role in the override role list
- The override is evaluated only for objects that pass object-level authorization (REQ-159)

**Outputs**:

- Audit event `property-override-access` per override read; audit event `visibility.override-changed` per list change

**Error cases**:

- Change of the override role list without `visibility:configure` → 403, list unchanged
- Unknown role ID in the override role list → 422, list unchanged

## Acceptance Criteria

**AC1** (Override effective once configured):

- Given: the override role list contains the role `migration-support`
- When: a principal holding `migration-support` and `viewer` retrieves an entity with restricted properties
- Then: all property values are returned; `visibilityMode` has no effect

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

**AC6** (Empty by default):

- Given: a fresh instance with the default configuration and a property with `visibilityMode=role-restricted` whose `readRoles` contain only `iso`
- When: a principal holding `system-admin`, `security-admin` and `viewer`, but not `iso`, retrieves an entity with this property
- Then: the property value is not returned (redacted, REQ-160), and the override role list retrieved via the admin API is empty

**AC7** (No object access through the override):

- Given: a principal holding only a role from the override role list (no `entity:read`, no `portal:read`)
- When: it retrieves an entity
- Then: the request is rejected with 403

**AC8** (Not applied to raw analytics):

- Given: a principal holding `analytics:query` and a role in the override role list, but no role in the `readRoles` of a `role-restricted` property
- When: it queries that property through raw analytics (REQ-179)
- Then: the values are masked (`NULL`, redaction marker set) and no `property-override-access` event is written

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: fresh instance with default configuration; property with `role-restricted` visibility; principals with and without override roles
- [ ] Pass criterion: AC1–AC8 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-126 (enforcement), REQ-005 (audit log), REQ-157 (permission model), REQ-161 (governance of visibility configuration)
- **Consequences**: REQ-160 (override applies on every read channel except raw analytics), REQ-179

## Notes

- US-128 and existing test material that assume `system-admin` as default override role must be aligned with the empty default (AC6).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-03 / owner decision Q4: override list configurable only with `visibility:configure`, audited, self-benefit flagged (AC3 rewritten, AC5 added) |
| 0.3.0 | 2026-10-13 | requirements-engineer | ADR-032 / owner decision Q14: override role list empty by default (no implicit `system-admin` override); restricted properties readable only via `readRoles` unless a role is explicitly added; override grants no object access; AC1 rewritten, AC6–AC7 and verification method added |
| 0.4.0 | 2026-10-13 | requirements-engineer | ADR-034 §3.2: override does not apply to raw analytics (statement, processing, AC8) |

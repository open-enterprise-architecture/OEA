---
id: REQ-157
title: Permission model with explicit permission matrix
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-04
    - UC-21
    - UC-23
  business_objects:
    - authorization-role
    - role-assignment
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-157: Permission Model with Explicit Permission Matrix

## Statement

The system **MUST** define a fixed set of named permissions and a matrix that maps each built-in authorization role to permissions, with metamodel administration and security administration as separate roles, and **MUST** store authorization roles separately from business roles modeled as EA content.

## Rationale

Without a defined permission model every controller decides individually which role is needed, and broken function-level authorization ships (OWASP API1/API5). If business roles modeled as EA content were used directly as authorization roles, an architect with write access to EA content could grant themselves administrative rights.

## Context

Source: security review SEC-01 (`security-reviews/requirements-baseline-2026-10.md`, proposal PR-01), owner decision Q4 (separate roles for metamodel administration and security administration) and Q1 (`portal-reader` is an authenticated role).

Role management is covered by [UC-23](../use-cases/UC-23-manage-roles-and-role-assignments.md). The authorization model (permission catalog, built-in role matrix, separation-of-duties rules SoD-1 to SoD-8) is decided in [ADR-032](../../adrs/ADR-032-authorization-model.md); the matrix in ADR-032 §4 is the binding reference for the shipped configuration.

Owner decision Q13 (2026-10-13): one person may hold `metamodel-admin` and `security-admin` at the same time (necessary in small organizations with a single administrator); such a combined holding is accepted but flagged (ADR-032 SoD-3). A single role, built-in or custom, must still never combine `metamodel:write` with `visibility:configure` or `visibility-scope:assign` (SoD-2).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Built-in permission catalog and role-to-permission matrix (shipped with the system)
- Custom roles created by a principal with `role:assign`

**Processing**:

- Minimum permission catalog: `portal:read`, `entity:read`, `entity:write`, `entity:delete`, `entity:restore`, `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `catalog:manage`, `dashboard:manage`, `viewpoint:manage`, `plateau:go-live`, `audit:read`, `analytics:query`, `webhook:manage`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`
- `admin`-class permissions (privileged): `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`; `portal:read` and `viewpoint:manage` are not admin-class
- Minimum built-in roles: `portal-reader`, `viewer`, `analyst`, `enterprise-architect`, `integration-writer`, `auditor`, `metamodel-admin`, `security-admin`, `system-admin`
- `portal-reader` holds only `portal:read`; `viewer` holds `portal:read` and `entity:read`; `analyst` holds `portal:read`, `entity:read` and `analytics:query`; `enterprise-architect` holds, among others, `viewpoint:manage` (full matrix: ADR-032 §4)
- `metamodel-admin` holds `metamodel:write` but not `visibility:configure`; `security-admin` holds `visibility:configure` and `visibility-scope:assign` but not `metamodel:write`
- No single role (built-in or custom) contains `metamodel:write` together with `visibility:configure` or `visibility-scope:assign` (SoD-2); a person holding `metamodel-admin` and `security-admin` together is allowed, and the assignment that creates the combination is flagged as SoD conflict (Q13, SoD-3)
- Effective permissions = union of the permissions of all active role assignments; no implicit hierarchy
- Only principals with `role:assign` can create, change or delete authorization role assignments

**Outputs**:

- Permission matrix retrievable via the admin API and documented

**Error cases**:

- Custom role saved with unknown permission names → validation error
- Role assignment change without `role:assign` → 403

## Acceptance Criteria

**AC1**:

- Given: the built-in roles
- When: the permission matrix is retrieved via the admin API
- Then: every role lists its permissions explicitly, and no permission is granted implicitly by a role hierarchy

**AC2**:

- Given: an architect with `entity:write` but without `role:assign`
- When: they create, edit or delete a Role entity or a role assignment in the EA model
- Then: no authorization role assignment changes and their effective permissions are unchanged

**AC3**:

- Given: a custom role created by an administrator
- When: it is saved without an explicit permission list
- Then: it has no permissions

**AC4**:

- Given: a person holding only `metamodel-admin`
- When: they call an operation that requires `visibility:configure`
- Then: the response is 403; conversely a person holding only `security-admin` receives 403 for an operation that requires `metamodel:write`

**AC5**:

- Given: the shipped configuration
- When: the permission matrix is retrieved via the admin API
- Then: it contains the permissions `portal:read` and `viewpoint:manage` and the built-in role `analyst` with exactly `portal:read`, `entity:read` and `analytics:query`, and it matches the matrix of ADR-032 §4 for every built-in role

**AC6**:

- Given: a person holding `metamodel-admin`
- When: a security administrator additionally assigns `security-admin` to that person
- Then: the assignment is stored, the audit event `role.assigned` carries `sodConflict: true`, and the access review lists the person as SoD conflict

## Verification Method

- [ ] Method: test (automated) + inspection of the matrix document
- [ ] Test setup: integration tests per built-in role against representative operations
- [ ] Pass criterion: AC1–AC6 green; matrix document matches the shipped configuration and ADR-032 §4
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001 (authenticated principal)
- **Follow-on effects**: REQ-158 (enforcement per operation), REQ-159 (object-level authorization), REQ-161 (visibility governance), REQ-171 (MFA for privileged accounts), REQ-176 (roles assignable to service accounts), REQ-029 (no 2FA exemption for `admin`-class roles)
- **Conflicts**: concept §21.8 sentence that metamodel roles can be used directly as authorization roles (superseded by ADR-032 §2; concept change pending)

## Risks of Non-Fulfillment

- Risk 1: inconsistent, ad-hoc authorization per endpoint; privilege escalation – critical
- Risk 2: self-assignment of administrative rights through EA content editing – high

## Implementation Notes

- The `roles` table (system-managed) and business roles as EA content must be distinguishable in the data model; follow-up for the business engineer (`business-objects/role.md` currently has `permissionLevel: read|write|admin` without a resource list) – specified in ADR-032 DM-1 to DM-5
- `portal-reader` is referenced in `api/openapi.yaml` but not defined in the data model (ADR-032 DM-3, API-3)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-01 (PR-01), owner decisions Q1 and Q4 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Aligned with ADR-032: permissions `portal:read` and `viewpoint:manage` and built-in role `analyst` added to the minimum lists; owner decision Q13 (combined `metamodel-admin` + `security-admin` allowed but flagged, SoD-3) noted; reference to UC-23; AC5–AC6 added |

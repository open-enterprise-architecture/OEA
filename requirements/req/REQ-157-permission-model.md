---
id: REQ-157
title: Permission model with explicit permission matrix
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-04
    - UC-21
  business_objects:
    - role
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
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

UC-01 "Out of Scope" names role management as a separate use case; that use case does not exist yet (follow-up for solution architect and PO).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Built-in permission catalog and role-to-permission matrix (shipped with the system)
- Custom roles created by a principal with `role:assign`

**Processing**:

- Minimum permission catalog: `entity:read`, `entity:write`, `entity:delete`, `entity:restore`, `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `catalog:manage`, `dashboard:manage`, `plateau:go-live`, `audit:read`, `analytics:query`, `webhook:manage`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`
- `admin`-class permissions (privileged): `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`
- Minimum built-in roles: `viewer`, `portal-reader`, `enterprise-architect`, `auditor`, `integration-writer`, `metamodel-admin`, `security-admin`, `system-admin`
- `metamodel-admin` holds `metamodel:write` but not `visibility:configure`; `security-admin` holds `visibility:configure` and `visibility-scope:assign` but not `metamodel:write`
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

## Verification Method

- [ ] Method: test (automated) + inspection of the matrix document
- [ ] Test setup: integration tests per built-in role against representative operations
- [ ] Pass criterion: AC1–AC4 green; matrix document matches the shipped configuration
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001 (authenticated principal)
- **Follow-on effects**: REQ-158 (enforcement per operation), REQ-159 (object-level authorization), REQ-161 (visibility governance), REQ-171 (MFA for privileged accounts), REQ-176 (roles assignable to service accounts), REQ-029 (no 2FA exemption for `admin`-class roles)
- **Conflicts**: concept §21.8 sentence that metamodel roles can be used directly as authorization roles

## Risks of Non-Fulfillment

- Risk 1: inconsistent, ad-hoc authorization per endpoint; privilege escalation – critical
- Risk 2: self-assignment of administrative rights through EA content editing – high

## Implementation Notes

- The `roles` table (system-managed) and business roles as EA content must be distinguishable in the data model; follow-up for the business engineer (`business-objects/role.md` currently has `permissionLevel: read|write|admin` without a resource list)
- `portal-reader` is referenced in `api/openapi.yaml` but not defined in the data model

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-01 (PR-01), owner decisions Q1 and Q4 |

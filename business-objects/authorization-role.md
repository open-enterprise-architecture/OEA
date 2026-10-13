---
identifier: authorization-role
name_de: Berechtigungsrolle
name_en: Authorization Role
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Access Role
  - Security Role
  - RBAC Role
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: Authorization Role

## Definition

An authorization role is a named, system-managed set of permissions from the fixed permission catalog that determines what a principal may do in OEA. It is access-control configuration, not EA content.

## Description

OEA uses role-based access control with a fixed permission catalog and configurable roles ([ADR-032](../adrs/ADR-032-authorization-model.md)). Code checks permissions, never role names. A principal's effective permissions are the union of the permissions of all its roles that are active at request time; there is no role hierarchy.

Two kinds of authorization roles exist:

- **Built-in roles** (`isBuiltin = true`): shipped with the system, not deletable, permission set immutable (SoD-5): `portal-reader`, `viewer`, `analyst`, `enterprise-architect`, `integration-writer`, `auditor`, `metamodel-admin`, `security-admin`, `system-admin` (matrix: ADR-032 §4).
- **Custom roles**: defined by principals with `role:assign` (e.g. `iso`, `dpo`, `external-auditor`). They can be used as `readRoles`/`writeRoles` of property visibility (REQ-124); such roles may have an empty permission set.

A role is **privileged** if it contains at least one admin-class permission. Privileged roles require a second factor to become effective in a session (SoD-8, REQ-171) and are never assignable to service accounts (SoD-4, REQ-176). "Privileged" is derived, never stored.

Principals receive roles through [role assignments](./role-assignment.md) (source `local`) and [IdP group mappings](./idp-group-mapping.md) (source `idp`); the local [system admin account](./system-admin-account.md) holds exactly `system-admin`.

Authorization roles are distinct from [business roles](./role.md) of the EA model; no business role or EA connection grants a permission (ADR-032 §2).

## Attributes

### AuthorizationRole

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | kebab-case, unique, immutable | Stable identifier, e.g. `enterprise-architect`, `iso`; referenced by assignments, IdP mappings and property visibility |
| name | string | required | | max. 100 characters | Display name in corporate language; translations via `i18n_entries` |
| description | string | optional | | max. 1000 characters | Purpose of the role |
| isBuiltin | boolean | required | false | read-only | Built-in role shipped with the release |
| permissions | reference (list) | required | [] | target: Permission; only catalog identifiers; empty list = no permissions (REQ-157 AC3) | Permission set of the role |
| assignableToServiceAccounts | boolean | required | false | `true` only if no admin-class permission (SoD-4) | Whether the role may be assigned to service accounts |
| overridesPropertyVisibility | boolean | required | false | `false` for every built-in role by default (Q14); changeable only with `visibility:configure`; audited | Member of the property-visibility override list (REQ-128): widens readable properties of already readable objects; grants no object access |
| createdAt / createdBy | datetime / principal | required | | createdBy = person or system admin account | Creation |
| updatedAt / updatedBy | datetime / principal | optional | | | Last change of name, description, permissions or flags |
| deletedAt / deletedBy | datetime / principal | optional | | custom roles only | Soft delete (see BR-07) |

### Permission (fixed catalog)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | `<resource>:<action>`, e.g. `entity:write`; fixed per release | Stable permission identifier (ADR-032 §3) |
| category | string | required | | resource part of `id` | Grouping in the permission matrix |
| adminClass | boolean | required | | | `true` = privileged permission |
| sortOrder | integer | required | 0 | | Display order |

Catalog (ADR-032 §3): `portal:read`, `entity:read`, `entity:write`, `entity:delete`, `entity:restore`, `catalog:manage`, `dashboard:manage`, `viewpoint:manage`, `plateau:go-live`, `webhook:manage`, `analytics:query`, `audit:read`, and the admin-class permissions `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`.

The catalog is seeded per release and read-only at runtime; descriptions are system texts (vue-i18n bundle), not data.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| grants | Permission | n:m | yes | Permission set (table `role_permissions`) |
| assignedVia | [role-assignment](./role-assignment.md) | 1:n | yes | Local assignments to persons and service accounts |
| mappedFrom | [idp-group-mapping](./idp-group-mapping.md) | 1:n | yes | IdP groups that derive this role |
| usedInVisibility | [metamodel-configuration](./metamodel-configuration.md) (PropertyDefinition `readRoles`/`writeRoles`) | n:m | yes | Property visibility (REQ-124, ADR-032 DM-9) |
| derivedInSession | [session](./session.md) | 1:n | yes | IdP-derived roles recorded per session (DM-10) |

## Lifecycle

```text
(built-in)  shipped ─────────────────────────────► (immutable)
(custom)    created → in use → deleted (soft)
```

**States**:

- `created` / `in use`: a custom role exists and can be assigned, mapped and referenced
- `deleted`: soft-deleted (`deletedAt` set); no new assignments or mappings; history retained for audit

**Transitions**:

- `→ created`: principal with `role:assign`; audit `role.created`
- permission set or flags changed: principal with `role:assign` (flag `overridesPropertyVisibility`: `visibility:configure`); audit `role.changed`; effective for all holders within 60 s
- `→ deleted`: principal with `role:assign`, only if unreferenced (BR-07); audit `role.deleted`

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | A role MUST contain only permission identifiers of the catalog; unknown identifiers are rejected (422) | onCreate, onUpdate | REQ-157 |
| BR-02 | No role (built-in or custom) may contain `metamodel:write` together with `visibility:configure` or `visibility-scope:assign` (SoD-2) | onCreate, onUpdate | ADR-032 SoD-2, REQ-157 AC4 |
| BR-03 | Built-in roles cannot be deleted and their permission sets cannot be changed (SoD-5) | onUpdate, onDelete | ADR-032 SoD-5 |
| BR-04 | `assignableToServiceAccounts = true` is allowed only if the role contains no admin-class permission; adding an admin-class permission to such a role is rejected (SoD-4) | onCreate, onUpdate | REQ-176 |
| BR-05 | A principal MUST NOT change the permission set of a role it currently holds (SoD-1) | onUpdate | ADR-032 SoD-1 |
| BR-06 | The override list is empty by default: no built-in role has `overridesPropertyVisibility = true` on delivery; a change of the flag that benefits the acting principal is audited and flagged `selfBenefiting` (SoD-6) | onCreate (seed), onUpdate | Q14, REQ-128, REQ-161 |
| BR-07 | A role referenced by a `granted` or `pending` role assignment, an active IdP group mapping or a property visibility configuration MUST NOT be deleted (422) | onDelete | ADR-032 §5 |
| BR-08 | Effective permissions of a principal = union of the permissions of all its active roles; no implicit hierarchy; privileged permissions are effective only in sessions with a verified second factor (SoD-8) | onRequest | REQ-157, REQ-171 |

## Examples

**Example 1**: Built-in role

```yaml
id: metamodel-admin
name: Metamodel Administrator
isBuiltin: true
permissions: [metamodel:write]
assignableToServiceAccounts: false
overridesPropertyVisibility: false
# privileged (derived): true
```

**Example 2**: Custom role used only for property visibility

```yaml
id: iso
name: Information Security Officer
isBuiltin: false
permissions: []
assignableToServiceAccounts: false
overridesPropertyVisibility: false
```

## Term Mapping

| Area | Designation | Note |
|---|---|---|
| OEA API | role (`/admin/roles`) | Always the authorization role |
| Concept §21.8 | authorization role / RBAC role | §8.2 "Role" is the business role |
| Entra ID / Authentik | group, app role | Source of IdP group mappings, not an OEA role itself |

## Distinction from Similar Objects

- **NOT** [role](./role.md): the business role is EA content and grants nothing.
- **NOT** [role-assignment](./role-assignment.md): the assignment binds a role to a principal for a validity period.
- **NOT** [idp-group-mapping](./idp-group-mapping.md): the mapping derives a role from an IdP group.

## Usage in Use Cases

- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md)
- [UC-21: Configure Property Visibility](../requirements/use-cases/UC-21-configure-property-visibility.md) (roles as `readRoles`/`writeRoles`)
- [UC-01: Login](../requirements/use-cases/UC-01-login.md) (effective permissions after login)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Created for ADR-032 DM-1 to DM-3: the former `roles` table mixed business and authorization attributes (security review SEC-01). The table is renamed `authorization_roles` in `docs/data-model.puml` to make the separation visible; business attributes (`type`, `permission_level`, `is_standard_role`) were removed. Permission and role-permission are modeled as sections of this business object because they have no lifecycle of their own (catalog fixed per release).

Soft delete instead of hard delete because revoked role assignments and session records keep referencing the role for audit and access review.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft from ADR-032 (DM-1, DM-2, DM-3, DM-9) and owner decision Q14 |

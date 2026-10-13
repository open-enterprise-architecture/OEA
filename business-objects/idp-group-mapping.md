---
identifier: idp-group-mapping
name_de: IdP-Gruppen-Zuordnung
name_en: IdP Group Mapping
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Group Mapping
  - Claim Mapping
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-032-authorization-model.md
  - adr: adrs/ADR-006-auth-stack-choice.md
---

# Business Object: IdP Group Mapping

## Definition

An IdP group mapping binds an immutable, provider-controlled group or app-role identifier of a configured identity provider to an [authorization role](./authorization-role.md); every person whose token from that issuer contains the identifier holds the role for the duration of the session (source `idp`).

## Description

IdP group mappings let operators federate access through their identity provider (SH-06 concern "identity federation"). Only immutable identifiers are accepted – Entra ID group object ID or app role, Authentik group UUID; display names are rejected because they are mutable and not unique (REQ-167, SEC-08).

Group claims are evaluated at login and at every token refresh against the IdP, at the latest every 15 minutes (owner decision Q16). Claims from other issuers are ignored. A missing or truncated groups claim (e.g. Entra ID overage) yields no IdP-derived roles and a warning event (fail closed). The derived roles are recorded per [session](./session.md) for audit and access review (DM-10).

The remote system admin mapping of UC-02/REQ-014 is an IdP group mapping with role `system-admin` (DM-6).

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID | Primary key |
| authProvider | reference | required | | target: auth provider (configured OIDC provider) | Provider whose tokens are evaluated |
| issuer | string | required | | = issuer of the provider | Only tokens with this exact `iss` are evaluated |
| claimType | enum | required | group | `[group, app-role]` | Kind of claim |
| groupIdentifier | string | required | | immutable ID; format validated per provider (Entra: GUID or app role value; Authentik: UUID) | Value matched in the token claim |
| displayLabel | string | optional | | max. 200 characters | Human-readable label; informational only, never evaluated |
| role | reference | required | | target: authorization-role (not deleted) | Role derived from group membership |
| active | boolean | required | true | | Inactive mappings derive nothing |
| createdAt / createdBy | datetime / principal | required | | | Creation |
| updatedAt / updatedBy | datetime / principal | optional | | | Last change |
| deletedAt / deletedBy | datetime / principal | optional | | | Soft delete (session records keep referencing the mapping) |

Uniqueness: (`authProvider`, `claimType`, `groupIdentifier`, `role`) among non-deleted mappings.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| derives | [authorization-role](./authorization-role.md) | n:1 | no | Role granted to group members |
| evaluatedIn | [session](./session.md) | 1:n | yes | Sessions in which the role was derived |
| issuedBy | auth provider | n:1 | no | Configured identity provider (business object pending) |

## Lifecycle

```text
active ⇄ inactive → deleted (soft)
```

**Transitions**:

- create/change: principal with `role:assign`; audit `idp-mapping.created` / `idp-mapping.changed`
- deactivate/delete: principal with `role:assign`; effective for all affected sessions within 60 s; audit `idp-mapping.changed` / `idp-mapping.deleted`; rejected if it removes the last path to `system-admin` (SoD-7, 409)

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `groupIdentifier` MUST match the identifier format of the provider; display names are rejected | onCreate, onUpdate | REQ-167, REQ-014 |
| BR-02 | A claim is evaluated only if the token's `iss` equals `issuer`; a missing or truncated groups claim yields no IdP-derived role and a warning event | onLogin, onRefresh | REQ-167 |
| BR-03 | A principal MUST NOT create or change a mapping for a group contained in its own current token (SoD-1) | onCreate, onUpdate | ADR-032 SoD-1 |
| BR-04 | Service accounts are never targets of IdP group mappings (they have no IdP sessions) | onLogin | ADR-032 SoD-4 |
| BR-05 | Group claims are re-evaluated at every token refresh, at the latest every 15 minutes | onRefresh | Q16 |
| BR-06 | Deleting or deactivating the last path to `system-admin` is rejected (409) | onUpdate, onDelete | ADR-032 SoD-7 |

## Examples

**Example 1**: Viewer access for an Entra ID group

```yaml
authProvider: entra-prod
issuer: "https://login.microsoftonline.com/<tenant-id>/v2.0"
claimType: group
groupIdentifier: "3f2b9c1e-6a4d-4b8e-9f10-2c7d5e8a1b44"
displayLabel: "EA-Viewers"
role: viewer
active: true
```

**Example 2**: Remote system admin (replaces `system-admin-account` mode `remote`)

```yaml
authProvider: authentik
claimType: group
groupIdentifier: "a1d0c6e8-3f4b-4c2a-9e7d-5b8f1a2c3d4e"
role: system-admin
```

## Distinction from Similar Objects

- **NOT** [role-assignment](./role-assignment.md): local, per person, stored; the mapping is per group and evaluated per session.
- **NOT** the person's identity binding (`person_identities`): that identifies the person; the mapping only derives roles.

## Usage in Use Cases

- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (A1)
- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md) (remote system admin)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Created for ADR-032 DM-5 and DM-6, REQ-167 and owner decision Q16. References an `auth_providers` table that is modeled only as a placeholder in `docs/data-model.puml`; a dedicated business object `auth-provider` is pending.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft from ADR-032 DM-5/DM-6, REQ-167, Q16 |

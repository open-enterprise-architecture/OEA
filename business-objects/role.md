---
identifier: role
name_de: Rolle (Geschäftsrolle)
name_en: Role (Business Role)
version: 0.2.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Business Role
  - Funktion
  - Verantwortlichkeit
related_capabilities: []
references:
  - concept: concept/20-entities/08-organization-roles-persons.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: Role (Business Role)

## Definition

A role is a business-functional responsibility in the modeled enterprise (e.g. "Process Owner", "Data Owner", "Enterprise Architect"), independent of the person who currently fills it. It is **EA content** and has no effect on access to OEA.

## Description

Business roles are entities of the metatype `role` in the EA repository (concept §8.2). Persons of the modeled enterprise are linked to them through the connection type `role-assignment` (Person entity → Role entity, concept §8.3). Both are shipped with the starter package as metamodel configuration (ADR-032 DM-8) and are maintained by architects with `entity:write`, versioned with the entity history and part of solutions and plateaus like any other EA content.

Business roles are decoupled from persons so that personnel changes do not trigger architecture changes.

**Separation from authorization** ([ADR-032](../adrs/ADR-032-authorization-model.md) §2): a business role never grants or removes a permission in OEA. Access is controlled exclusively by [authorization roles](./authorization-role.md) and their [role assignments](./role-assignment.md). The same name may exist in both worlds (business role "Enterprise Architect", authorization role `enterprise-architect`); they are not linked. The sentence of concept §21.8 that metamodel roles "can be used directly as authorization roles" is superseded by ADR-032.

The only place where EA content influences access is connection-scoped property visibility (REQ-127); connections of a type designated as `scopingConnectionType` require `visibility-scope:assign` and are not affected by this business object.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | | Role designation, e.g. "Enterprise Architect" (corporate language, monolingual like every entity name) |
| description | string | optional | | | Free-text description |
| type | enum | required | | `[functional, process, project, governance, architectural]` | Role type (concept §8.2) |
| responsibilities | string | optional (list) | | min. 1 entry recommended | What the role is responsible for |
| authorities | string | optional (list) | | | What the role may decide in the modeled enterprise (business authority, not an OEA permission) |
| isStandardRole | boolean | required | false | | From reference model vs. organization-specific |

All attributes are property definitions of the metatype `role`; they are stored as entity property values, not in a dedicated table.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| filledBy | Person entity (EA content, metatype `person`) | n:m | yes | Persons of the modeled enterprise filling the role, via the connection type `role-assignment` with `validFrom`, `validTo`, `assignmentType`, `percentage` (§8.3) |
| belongsToUnit | organization-unit | n:1 | yes | Organizational unit to which the role is assigned |
| reportsToRole | role | n:1 | yes | Superior role |

**Notes**: There is deliberately **no** relationship to [authorization-role](./authorization-role.md), [role-assignment](./role-assignment.md) or the account object [person](./person.md). The account of a person is linked to its Person entity only through the account link `persons.entity_id` (ADR-032 DM-7).

## Lifecycle

```text
draft → active → deprecated
```

**States**:

- `draft`: Role is defined but not yet in use
- `active`: Role is used in the model (people fill it, RACI assignments reference it)
- `deprecated`: Role is no longer newly assigned in the model; existing RoleAssignment connections expire

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Every `active` role MUST have at least one `responsibility` (concept constraint `role-has-responsibilities`) | onCreate, onUpdate | – |
| BR-02 | At most one role with RACI status `accountable` may be assigned per activity (concept constraint `single-accountable-per-activity`) | onUpdate | – |
| BR-03 | Creating, changing or deleting a business role or a `role-assignment` connection MUST NOT change any authorization role assignment or any effective permission (REQ-157 AC2, UC-23 E6) | onCreate, onUpdate, onDelete | ADR-032 §2 |

## Examples

**Example 1**: Architectural business role

```yaml
metatype: role
name: Enterprise Architect
type: architectural
responsibilities:
  - Conduct architecture reviews
  - Moderate the ADR workflow
authorities:
  - Approve architecture decisions with the ARB
isStandardRole: true
```

## Delimitation from Similar Objects

- **NOT** [authorization-role](./authorization-role.md): the authorization role grants permissions in OEA and is system-managed configuration; the business role describes responsibility in the modeled enterprise and grants nothing.
- **NOT** [person](./person.md): role is the function, the Person entity is the actual human who fills it; the account object `person` is the OEA principal.
- **NOT** organization-unit: role is business-functional, OrganizationUnit is structural.

## Usage in Use Cases

- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (E6: business role edited instead of authorization role)

## Concept Reference

- [§8.2 Role, §8.3 Person](../concept/20-entities/08-organization-roles-persons.md)
- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md) (superseded in the role/authorization statement by ADR-032)

## Notes

Version 0.2.0 implements ADR-032 DM-1 and DM-8: the former mix of business role and authorization attribute (`permissionLevel: read|write|admin` per resource without a resource list) allowed self-escalation through EA content editing (security review SEC-01). `permissionLevel` and the authorization statement were removed; authorization moved to [authorization-role](./authorization-role.md). UC-01 was removed from "Usage in Use Cases" because login depends on authorization roles, not business roles.

The starter package (metatype `role`, connection type `role-assignment`) is metamodel configuration and is maintained outside `business-objects/`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | Business Engineer | Initial draft in the context of UC-01 Login |
| 0.2.0 | 2026-10-13 | Business Engineer | ADR-032 DM-1/DM-8: business role only (EA content, metatype `role`); `permissionLevel` and the authorization statement removed; BR-03 (no authorization effect) added; authorization moved to `authorization-role` |

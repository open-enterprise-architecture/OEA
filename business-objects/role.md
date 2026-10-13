---
identifier: role
name_de: Rolle
name_en: Role
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Funktion
  - Verantwortlichkeit
related_capabilities: []
references:
  - concept: concept/20-entities/08-organization-roles-persons.md
---

# Business Object: Role

## Definition

A role is a business-functional responsibility in the EA repository, independent of the person who currently fills it. Roles are decoupled from persons so that personnel changes do not trigger architecture changes.

## Description

Roles from the EA metamodel (§8.2) are used directly as authorization roles in the sense of RBAC (see concept §21.8): a person who fills a specific role receives the corresponding access rights in the system. Role is thus the central link between business responsibility in the EA repository and technical authorization at login.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | | Role designation, e.g. "Enterprise Architect" |
| description | string | optional | | | Free-text description |
| type | enum | required | | `[functional, process, project, governance, architectural]` | Role type |
| responsibilities | string | optional (list) | | min. 1 entry recommended | What the role is responsible for |
| authorities | string | optional (list) | | | What the role may decide |
| isStandardRole | boolean | required | false | | From reference model vs. organization-specific |
| permissionLevel | enum | required | read | `[read, write, admin]` | Default access level per resource for authorization (see §21.8) |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| filledBy | [person](./person.md) | n:m | yes | Persons currently entrusted with this role (via RoleAssignment) |
| belongsToUnit | organization-unit | 1:1 | yes | Organizational unit to which the role is assigned |
| reportsToRole | role | 1:1 | yes | Superior role |

## Lifecycle

```
draft → active → deprecated
```

**States**:
- `draft`: Role is defined but not yet in use
- `active`: Role is actively assigned and used for authorization
- `deprecated`: Role is no longer newly assigned, existing assignments expire

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Every `active` role MUST have at least one `responsibility` (concept constraint `role-has-responsibilities`) | onCreate, onUpdate | – |
| BR-02 | At most one role with RACI status `accountable` may be assigned per activity (concept constraint `single-accountable-per-activity`) | onUpdate | – |

## Examples

**Example 1**: Architectural role
```yaml
name: Enterprise Architect
type: architectural
responsibilities:
  - Conduct architecture reviews
  - Moderate the ADR workflow
authorities:
  - Approve architecture decisions with the ARB
isStandardRole: true
permissionLevel: admin
```

## Delimitation from Similar Objects

- **NOT** [person](./person.md): Role is the function, person is the actual human who fills it.
- **NOT** organization-unit: Role is business-functional, OrganizationUnit is structural.

## Usage in Use Cases

- [UC-01: Login](../requirements/use-cases/UC-01-login.md)

## Concept Reference

- [§8.2 Role](../concept/20-entities/08-organization-roles-persons.md)
- [§21.8 Authentication/Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

First modeling in the context of UC-01 Login. `permissionLevel` is a simplification of the RBAC/ABAC model described in §21.8 for the walking skeleton scope; property-level authorization will follow in a later use case.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | Business Engineer | Initial draft in the context of UC-01 Login |

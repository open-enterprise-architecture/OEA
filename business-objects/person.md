---
identifier: person
name_de: Person
name_en: Person
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Mitarbeiter:in
  - User
  - Benutzer (im Kontext der Tool-Authentifizierung)
related_capabilities: []
references:
  - concept: concept/20-entities/08-organization-roles-persons.md
---

# Business Object: Person

## Definition

A person is a specific employee or external contributor who bears responsibility in the EA repository through assigned roles ([role](./role.md)) or uses the tool. Person is deliberately decoupled from role so that personnel changes do not trigger architecture changes.

## Description

In the EA repository, a person is typically **referenced, not mastered**: master data (name, email, employment status) comes from HR systems or an identity provider (e.g. Active Directory, Okta) and is only referenced in the repository or held at a reduced level of detail (see `detailLevel`). This protects against GDPR conflicts caused by duplicate storage of personnel data.

In the context of tool authentication (see [UC-01 Login](../requirements/use-cases/UC-01-login.md)), a person is the identity that authenticates against OEA; the actual authorization is performed via the roles assigned to them.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | | Display name |
| employeeId | string | optional | | | Internal employee number, if available |
| email | string | required | | valid email address | Primary contact and login identity |
| active | boolean | required | true | | Employment/account status |
| startDate | date | optional | | | Start of affiliation |
| endDate | date | optional | | | End of affiliation |
| externalReference | reference | optional | | target: external-identity (e.g. AD, Workday, OIDC provider) | Reference to the master system |
| detailLevel | enum | required | reference-only | `[full, reference-only, anonymized]` | Controls how much personal data is held in the repository itself |
| uiTheme | enum | required | system | `[light, dark, system]` | Preferred UI color scheme; `system` adopts the OS setting (prefers-color-scheme) |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| fillsRoles | [role](./role.md) | n:m | yes | Roles that the person currently or historically fills (see RoleAssignment) |
| memberOf | organization-unit | n:m | yes | Organizational units to which the person is assigned |
| primaryOrgUnit | organization-unit | 1:1 | yes | Primary organizational unit |

**Notes**: `fillsRoles` is resolved over time via the `RoleAssignment` relation (role, person, validFrom, validTo, assignmentType, percentage), see concept §8.3.

## Lifecycle

```
invited → active → inactive
              ↓
          offboarded
```

**States**:
- `invited`: Person is created/referenced in the system but not yet authenticated
- `active`: Person is active, can log in and fill roles
- `inactive`: Person is temporarily without access (e.g. parental leave), role assignments are retained
- `offboarded`: Person has left the company/mandate, access revoked, history is retained for audit

**Transitions**:
- `invited → active`: first successful login (see UC-01)
- `active → inactive`: by the HR master system or manually by roles with authorization rights
- `* → offboarded`: triggered by the HR master system, access is denied at the next login attempt

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | An `active` person MUST fill at least one role (see concept constraint `person-minimum-role`) | onUpdate, onLogin | – |
| BR-02 | Personal data with `detailLevel: full` may only be held if there is no HR master system connection | onCreate, onUpdate | GDPR Art. 5 (data minimization) |

## Examples

**Example 1**: Referenced person (standard case)
```yaml
name: Kurt Lead Enterprise Architect
email: kurt@example-kmu.de
active: true
detailLevel: reference-only
externalReference:
  system: oidc-provider
  id: kurt.architekt
fillsRoles:
  - role: enterprise-architect
```

## Delimitation from Similar Objects

- **NOT** [role](./role.md): Role describes business-functional responsibility, independent of who currently fills it. Person is the actual human.
- **NOT** organization-unit: OrganizationUnit is the structural unit in which a person is organizationally located, not the person itself.

## Usage in Use Cases

- [UC-01: Login](../requirements/use-cases/UC-01-login.md)
- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [UC-03: Set Up Authentication Method](../requirements/use-cases/UC-03-set-up-authentication-method.md)

## Concept Reference

- [§8.3 Person](../concept/20-entities/08-organization-roles-persons.md)
- [§8.7 Mastership with HR Systems](../concept/20-entities/08-organization-roles-persons.md)
- [§21.8 Authentication/Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

First modeling in the context of UC-01 Login. Attributes reduced to what is needed for authentication/authorization; full HR-relevant attributes (skills, RACI) will follow in dedicated use cases as needed.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | Business Engineer | Initial draft in the context of UC-01 Login |
| 0.2.0 | 2026-06-28 | Business Engineer | Added `uiTheme` attribute (REQ-135: dark mode preference per person, persisted server-side) |

---
identifier: person
name_de: Person (Konto)
name_en: Person (Account)
version: 0.4.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Mitarbeiter:in
  - User
  - Benutzer (im Kontext der Tool-Authentifizierung)
  - Principal
  - Service Account (personType = service-account)
related_capabilities: []
references:
  - concept: concept/20-entities/08-organization-roles-persons.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: Person (Account)

## Definition

A person is the account through which a human (or, as service account, an external system) is known to and acts in OEA; it is the principal to which authorization roles are assigned. The human as part of the modeled enterprise is represented separately by a Person entity in the EA repository, to which the account can be linked.

## Description

In the EA repository, a person is typically **referenced, not mastered**: master data (name, email, employment status) comes from HR systems or an identity provider (e.g. Entra ID, Authentik) and is only referenced or held at a reduced level of detail (see `detailLevel`). This protects against GDPR conflicts caused by duplicate storage of personnel data.

**Account and Person entity** ([ADR-032](../adrs/ADR-032-authorization-model.md) §2):

- The **account** (this business object, table `persons`) authenticates and receives [authorization roles](./authorization-role.md) through [role assignments](./role-assignment.md) and [IdP group mappings](./idp-group-mapping.md).
- The **Person entity** (EA content, metatype `person`, §8.3) fills [business roles](./role.md) through RoleAssignment connections; this grants no access.
- The **account link** (`entityId`) connects both. It is needed for connection-scoped property visibility (REQ-127: traversal starts at "the user's Person entity"). Because it affects access, it is set only by principals with `person:manage`, never for the own account (SoD-1), and audited (`person.account-linked`).

**Identity binding**: a human account is bound to the immutable pair (`issuer`, `subject`) of an identity provider (REQ-166); logins are matched only on this pair, never on e-mail (REQ-004). A person may have several bindings (one per configured provider). Local authentication uses [local credentials](./local-credential.md) instead.

**Service accounts** (`personType = service-account`) represent external systems (ADR-026). They authenticate only with [machine credentials](./machine-credential.md), hold only roles flagged `assignableToServiceAccounts`, have no sessions, no identity binding and no account link.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| personType | enum | required | human | `[human, service-account]`; immutable | Kind of principal |
| name | string | required | | | Display name |
| employeeId | string | optional | | null for service accounts | Internal employee number, if available |
| email | string | required | | valid email address; unique | Contact address; **not** a login identity (REQ-166) |
| status | enum | required | invited | `[invited, active, inactive, offboarded]`; service accounts never `invited` | Lifecycle status (replaces `active: boolean`) |
| statusChangedAt | datetime | required | | | Time of the last status change |
| statusChangedBy | principal | optional | | person or system admin account; empty = system transition | Actor of the last status change |
| startDate | date | optional | | | Start of affiliation |
| endDate | date | optional | | | End of affiliation |
| externalReference | composite | optional | | fields `system`, `id` | Reference to the HR master system (§8.7); not used for login |
| identities | composite (list) | optional | | human only; see Identity Binding | Bindings to identity providers |
| entityId | reference | optional | | target: Person entity (metatype `person`); unique; human only | Account link to EA content (ADR-032 DM-7) |
| entityLinkedAt / entityLinkedBy | datetime / principal | conditional | | set with `entityId`; principal ≠ this account | Who set the account link and when |
| detailLevel | enum | required | reference-only | `[full, reference-only, anonymized]`; service accounts always `reference-only` | Controls how much personal data is held |
| uiLanguage | string | required | de | BCP 47 `[de, en, fr, it]`; ignored for service accounts | UI language |
| uiTheme | enum | required | system | `[light, dark, system]` | Preferred UI color scheme (REQ-135) |
| createdAt | datetime | required | | | Creation (invitation) time |

### Identity Binding (table `person_identities`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| authProvider | reference | required | | target: configured OIDC provider | Provider |
| issuer | string | required | | exact `iss` of the provider | Issuer |
| subject | string | required | | immutable subject claim (`sub`; Entra ID: `oid` with the tenant in the issuer) | Subject |
| linkMethod | enum | required | | `[admin-entered, email-match]` | How the binding was created |
| linkedAt | datetime | required | | | Creation |
| confirmedAt / confirmedBy | datetime / principal | conditional | | required before first use if `linkMethod = email-match` (only with `email_verified = true`, REQ-166 AC4) | Administrator confirmation |
| lastLoginAt | datetime | optional | | | Last login via this binding |

Uniqueness: (`issuer`, `subject`) across all persons.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| holdsRoles | [role-assignment](./role-assignment.md) | 1:n | yes | Local authorization role assignments (never hard-deleted) |
| ownsCredentials | [local-credential](./local-credential.md) | 1:n | yes | Local authentication material (human only) |
| ownsMachineCredentials | [machine-credential](./machine-credential.md) | 1:n | yes | Client secrets (service account only) |
| hasSessions | [session](./session.md) | 1:n | yes | Login sessions (human only) |
| linkedEntity | Person entity (EA content) | 1:1 | yes | Account link (`entityId`) |
| linkedAdminAccount | [system-admin-account](./system-admin-account.md) | 1:1 | yes | Local system admin account linked to this person (SoD-1) |
| fillsRoles | [role](./role.md) | n:m | yes | Business roles, resolved through the linked Person entity and its RoleAssignment connections (§8.3); no authorization effect |
| memberOf | organization-unit | n:m | yes | Organizational units (EA content, via the Person entity) |

**Notes**: `fillsRoles` refers to business roles only (ADR-032 DM-4). Effective permissions come exclusively from `holdsRoles` and IdP-derived session roles.

## Lifecycle

```text
invited → active ⇄ inactive
   │         │        │
   └─────────┴────────┴──► offboarded
```

**States**:

- `invited`: account created (pre-provisioned), not yet logged in; role assignments may be prepared but are not effective; login allowed (REQ-004)
- `active`: can log in; role assignments are effective
- `inactive`: temporarily without access (e.g. parental leave); login rejected (REQ-004); assignments retained; all sessions revoked (REQ-164)
- `offboarded`: left the organization or mandate; login rejected; all sessions revoked and all local assignments revoked (UC-23 A2); history and audit retained

**Transitions**:

- `invited → active`: first successful login (REQ-007); system transition
- `active → inactive`, `inactive → active`: principal with `person:manage` or HR master system
- `* → offboarded`: principal with `person:manage` or HR master system; rejected if it removes the last path to `system-admin` (SoD-7)
- `offboarded → invited`: reactivation, only if the setting `allowReactivationOfOffboarded` is enabled (default off, owner decision Q19); principal with `person:manage`; the person returns without role assignments and without local factors, and existing identity bindings must be confirmed again
- Service accounts: created as `active`; `active ⇄ inactive` and `→ offboarded` with `service-account:manage`

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | The concept constraint `person-minimum-role` applies to the Person **entity** in the EA model (business roles); an account without authorization role assignment can log in with restricted access (REQ-003) | onUpdate (EA content) | concept §8.3 |
| BR-02 | Personal data with `detailLevel: full` may only be held if there is no HR master system connection | onCreate, onUpdate | GDPR Art. 5 (data minimization) |
| BR-03 | Session creation requires a matching identity binding (`issuer`, `subject`) or a local credential, and status `invited` or `active`; e-mail is never used to match a login | onLogin | REQ-004, REQ-166 |
| BR-04 | Leaving status `active` revokes all sessions and refresh tokens within 60 s; `offboarded` additionally revokes all local role assignments | onUpdate | REQ-164, UC-23 A2 |
| BR-05 | The account link `entityId` is set only by a principal with `person:manage`, never for its own account (or the account linked to its system admin account), and is audited | onUpdate | ADR-032 SoD-1, DM-7 |
| BR-06 | Service accounts have no identity binding, no local credentials, no sessions, no account link and are never targets of IdP group mappings | onCreate, onUpdate | ADR-032 SoD-4, REQ-176 |
| BR-07 | Reactivation of an `offboarded` person is rejected unless the setting `allowReactivationOfOffboarded` is enabled; a reactivated person starts in `invited` without role assignments and without local credentials | onUpdate | owner decision Q19, REQ-004 |

## Examples

**Example 1**: Human account with OIDC binding and account link

```yaml
personType: human
name: Kurt Lead Enterprise Architect
email: kurt@example-sme.com
status: active
detailLevel: reference-only
identities:
  - issuer: "https://login.microsoftonline.com/<tenant-id>/v2.0"
    subject: "6f1a…"          # Entra ID oid
    linkMethod: admin-entered
entityId: 3311                # Person entity "Kurt" in the EA model
# authorization roles: see role-assignment (enterprise-architect, metamodel-admin)
```

**Example 2**: Service account

```yaml
personType: service-account
name: ci-scanner
email: ci-scanner@example-sme.com
status: active
detailLevel: reference-only
```

## Delimitation from Similar Objects

- **NOT** the Person entity (EA content): the entity describes the human in the modeled enterprise and fills business roles; the account is the OEA principal.
- **NOT** [role](./role.md) / [authorization-role](./authorization-role.md): roles describe responsibility or permissions, independent of who holds them.
- **NOT** [system-admin-account](./system-admin-account.md): the bootstrap account is not a person, but may be linked to one.
- **NOT** organization-unit.

## Usage in Use Cases

- [UC-01: Login](../requirements/use-cases/UC-01-login.md)
- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [UC-03: Set Up Authentication Method](../requirements/use-cases/UC-03-set-up-authentication-method.md)
- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md)

## Concept Reference

- [§8.3 Person](../concept/20-entities/08-organization-roles-persons.md)
- [§8.7 Mastership with HR Systems](../concept/20-entities/08-organization-roles-persons.md)
- [§21.8 Authentication/Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

First modeled in the context of UC-01 Login. Version 0.3.0 implements ADR-032 DM-4/DM-7 and the security review follow-ups SEC-06/SEC-07: the boolean `active` could not express `inactive` vs. `offboarded` and REQ-004 checks the lifecycle status; e-mail-based matching allowed account takeover, hence the separate identity binding with a unique (`issuer`, `subject`) constraint. The binding is a separate table instead of reusing `external_ref_system/external_ref_id`, because those reference the HR master system and a person may log in through more than one provider. `personType` was already in the data model and is now documented here.

Open questions:

- Can an `offboarded` account be reactivated (re-hire), or is a new account created?
- Retention and anonymization of offboarded accounts (security review SEC-28, PR-33).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | Business Engineer | Initial draft in the context of UC-01 Login |
| 0.2.0 | 2026-06-28 | Business Engineer | Added `uiTheme` attribute (REQ-135: dark mode preference per person, persisted server-side) |
| 0.3.0 | 2026-10-13 | Business Engineer | ADR-032 / security review: `active` → lifecycle `status`; identity binding (`issuer`, `subject`, unique); account link `entityId` to the Person entity (DM-7); `personType` documented; `fillsRoles` limited to business roles, authorization via `role-assignment` (DM-4); BR-01 reworded, BR-03 to BR-06 added |
| 0.4.0 | 2026-10-13 | Lukas Mathis | Reactivation of offboarded persons configurable (`allowReactivationOfOffboarded`, default off; owner decision Q19) |

---
identifier: system-admin-account
name_de: System-Admin-Account
name_en: System Admin Account
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Bootstrap-Admin
  - Break-Glass-Account
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
---

# Business Object: System Admin Account

## Definition

A system admin account is a privileged, instance-wide access for administering an OEA instance itself (initial configuration, IdP setup, emergency access) – independent of the [Person](./person.md) and [Role](./role.md) objects modeled in the EA repository from a business perspective.

## Description

The system admin account solves the chicken-and-egg problem of a freshly installed OEA instance: before any person with a RoleAssignment exists in the repository, an access must already exist in order to be able to configure the instance at all (see [UC-02 Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)). It is therefore deliberately decoupled from person/role and is not, for example, a special RoleAssignment.

According to [ADR-006](../adrs/ADR-006-auth-stack-choice.md), a system admin account can be realized in two ways:

- **local**: a built-in account with its own credential (password or setup token), independent of any external identity provider
- **remote**: a mapping "whoever logs in with group/role claim X at Entra ID/Authentik receives system admin rights" – without a locally stored credential

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| mode | enum | required | local | `[local, remote]` | Bootstrapping mode |
| localCredentialHash | string | optional | | only set if `mode=local` | Password/setup token hash (never plaintext) |
| remoteIdpReference | composite | optional | | only set if `mode=remote`; fields: `provider` (entra-id \| authentik), `claimType` (group \| role), `claimValue` (string) | Mapping to an IdP claim that grants system admin rights |
| createdAt | datetime | required | | | Time of bootstrapping |
| lastUsedAt | datetime | optional | | | Last successful use |
| active | boolean | required | true | | Whether the account is currently usable |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| optionallyLinkedTo | [person](./person.md) | 1:1 | yes | Subsequent, purely informational link to a person, in case the admin later also maps themselves as a regular person with a role in the repository – no business necessity |

**Notes**: There is deliberately **no** mandatory relationship to person or role – that is the entire purpose of this object compared to the regular authentication from UC-01.

## Lifecycle

```text
bootstrapped → active → deactivated
```

**States**:

- `bootstrapped`: Account was created during initial installation, not yet used
- `active`: Account is actively usable
- `deactivated`: Account was deliberately deactivated (e.g. after establishing regular IdP governance, see business rule BR-02)

**Transitions**:

- `bootstrapped → active`: first successful login with the account
- `active → deactivated`: manually by an existing system admin account or a person with the corresponding authority

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | At most one bootstrapping process may be active per instance at any time (no parallel initial setup) | onCreate | – |
| BR-02 | A local system admin account SHOULD be deactivatable as soon as at least one external IdP with an admin mapping is configured (recommendation, not a hard constraint) | onUpdate | – |
| BR-03 | Instance-wide enforcement of a second factor for regular persons (see REQ-020) does NOT apply to the system admin account | onLogin | – |

## Examples

**Example 1**: Local bootstrap admin

```yaml
mode: local
localCredentialHash: "<argon2id-hash>"
createdAt: 2026-06-24T10:00:00Z
active: true
```

**Example 2**: Remote-mapped admin (Entra ID)

```yaml
mode: remote
remoteIdpReference:
  provider: entra-id
  claimType: group
  claimValue: "oea-system-admins"
createdAt: 2026-06-24T10:00:00Z
active: true
```

## Delimitation from Similar Objects

- **NOT** [person](./person.md) / [role](./role.md): These model business responsibility within the EA repository; the system admin account is pure instance administration and may exist before any person is created in the repository.
- **NOT** the regular local authentication from REQ-011 (username/password without a second factor): that presupposes an already existing person with a RoleAssignment; the system admin account exists independently of this and explicitly serves bootstrapping.

## Usage in Use Cases

- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

First modeling in the context of UC-02 (bootstrapping). Deliberately kept minimal – multi-admin scenarios (several parallel system admin accounts) and recovery/break-glass procedures are noted as open questions in UC-02, not decided here in advance.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | Business Engineer | Initial draft in the context of UC-02 Bootstrapping |

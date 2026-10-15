---
identifier: system-admin-account
name_de: System-Admin-Account
name_en: System Admin Account
version: 0.4.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Bootstrap-Admin
  - Break-Glass-Account
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: System Admin Account

## Definition

A system admin account is the local, privileged bootstrap account for administering an OEA instance (initial configuration, IdP setup, emergency access). It is not a [person](./person.md) and holds exactly the permissions of the built-in [authorization role](./authorization-role.md) `system-admin`.

## Description

The system admin account solves the chicken-and-egg problem of a freshly installed OEA instance: before any person holds an authorization role, an access must exist in order to configure the instance (see [UC-02 Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)). It is therefore deliberately decoupled from persons and role assignments.

**Paths to system administration** ([ADR-032](../adrs/ADR-032-authorization-model.md) §1, §7, DM-6):

- **local**: this business object – a built-in account with its own [local credentials](./local-credential.md) (password and second factor)
- **remote**: an [IdP group mapping](./idp-group-mapping.md) with role `system-admin` ("whoever logs in with group ID X from issuer Y receives `system-admin`"). The remote mode is no longer an attribute of this business object; it is configured and audited like any other IdP group mapping, with an immutable group identifier (REQ-014, REQ-167).
- **assigned**: a [role assignment](./role-assignment.md) of `system-admin` to an active person

SoD-7 requires at least one usable path at all times; deactivating the local account is rejected if it is the last one.

The account may be linked to the person of the administrator (`linkedPerson`). For separation of duties (SoD-1) both count as the same principal: the account cannot change the role assignments, IdP mappings or account link of the linked person.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID | Primary key |
| loginName | string | required | | unique; `^[a-z][a-z0-9._-]{2,63}$` | Login name of the local account |
| status | enum | required | bootstrapped | `[bootstrapped, active, deactivated]` | Lifecycle status (replaces `active: boolean`) |
| linkedPerson | reference | optional | | target: person (human); unique | Optional link to the administrator's person (SoD-1) |
| createdAt | datetime | required | | | Time of bootstrapping |
| firstLoginAt | datetime | optional | | | First successful login |
| lastUsedAt | datetime | optional | | | Last successful use |
| deactivatedAt / deactivatedBy | datetime / principal | optional | | | Deactivation |

Removed in 0.2.0: `mode`, `remoteIdpReference` (now an IdP group mapping, DM-6), `localCredentialHash` (now a password [local credential](./local-credential.md) owned by this account, argon2id only per REQ-169), `active` (now `status`).

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| ownsCredentials | [local-credential](./local-credential.md) | 1:n | no (after bootstrap) | Password, second factor, break-glass recovery password |
| linkedTo | [person](./person.md) | 1:1 | yes | Same principal for SoD-1 |
| hasSessions | [session](./session.md) | 1:n | yes | Login sessions |
| alternativePath | [idp-group-mapping](./idp-group-mapping.md) (role `system-admin`) | – | yes | Remote system administration (counts for SoD-7) |

## Lifecycle

```text
bootstrapped → active → deactivated
```

**States**:

- `bootstrapped`: created during installation, not yet used
- `active`: used at least once; a second factor is required from the end of the setup phase on (ADR-042)
- `deactivated`: deliberately deactivated (e.g. after establishing IdP governance, BR-02); sessions revoked

**Transitions**:

- `bootstrapped → active`: first successful login (during the setup phase with password only, ADR-042)
- `active → deactivated`: by a principal with `system-admin` permissions; rejected if no other path to `system-admin` remains (SoD-7)

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | At most one bootstrapping process may be active per instance at any time (no parallel initial setup) | onCreate | REQ-015 |
| BR-02 | A local system admin account SHOULD be deactivatable as soon as at least one active IdP group mapping to `system-admin` exists (recommendation, not a hard constraint) | onUpdate | REQ-019 |
| BR-03 | During the setup phase the account logs in with password only; the first action that gives another identity access (identity binding, IdP group mapping, role assignment, person credential, service account) requires an enrolled and confirmed second factor and ends the setup phase; afterwards every login requires the second factor; no configuration (REQ-020, REQ-029) can exempt it | onLogin, onUpdate | REQ-171, ADR-042 |
| BR-04 | The account holds exactly the permissions of `system-admin` and no other role | onRequest | ADR-032 §1 |
| BR-05 | The account and its linked person count as one principal for SoD-1 | onUpdate (authorization configuration) | ADR-032 SoD-1 |
| BR-06 | Deactivating the account is rejected (409) if it is the last usable path to `system-admin` | onUpdate | ADR-032 SoD-7 |

## Examples

**Example 1**: Local bootstrap admin

```yaml
loginName: admin
status: active
createdAt: 2026-06-24T10:00:00Z
firstLoginAt: 2026-06-24T10:05:00Z
credentials:
  - type: password        # argon2id
  - type: totp
```

**Example 2**: Remote system admin (IdP group mapping, not this object)

```yaml
# idp-group-mapping
authProvider: entra-prod
claimType: group
groupIdentifier: "3f2b9c1e-6a4d-4b8e-9f10-2c7d5e8a1b44"   # group object ID, never the display name
displayLabel: "oea-system-admins"
role: system-admin
```

## Delimitation from Similar Objects

- **NOT** [person](./person.md): the system admin account exists before any person and is pure instance administration.
- **NOT** a [role assignment](./role-assignment.md) of `system-admin`: that requires an existing person.
- **NOT** the regular local authentication (REQ-011) of persons.

## Usage in Use Cases

- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (administration path, SoD-1/SoD-7)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Version 0.2.0 aligns the business object with ADR-032 DM-6, REQ-167 (immutable claim identifiers, security review SEC-08), REQ-171 (MFA, SEC-10), REQ-169 (argon2id) and REQ-173 (break-glass recovery credential = temporary password credential with expiry). The account was previously not represented in `docs/data-model.puml`; it is now the table `system_admin_accounts`. Its credentials reuse `local_credentials` (owner = system admin account) instead of an embedded hash, so that password policy, second factor, revocation and break-glass follow the same rules as for persons.

**Setup token** (REQ-017, change `us-013-local-bootstrapping`): while no account exists, the backend keeps one setup token as SHA-256 hash with an expiry in the single-row table `bootstrap_setup_tokens` and hands the token over only as a file inside the container. The bootstrapping deletes the row; a restart without account issues a new token. The token is a short-lived technical artifact and not a business object of its own.

Open: multi-admin scenarios (several local system admin accounts) are not constrained by the data model; UC-02 keeps this as an open question.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | Business Engineer | Initial draft in the context of UC-02 Bootstrapping |
| 0.2.0 | 2026-10-13 | Business Engineer | ADR-032 DM-6: remote mode = IdP group mapping to `system-admin` (immutable IDs, REQ-167); BR-03 aligned with REQ-171 (MFA after first login); credentials moved to `local-credential`; `status` lifecycle; `linkedPerson` for SoD-1; BR-04 to BR-06 |
| 0.3.0 | 2026-10-15 | Business Engineer | `loginName` pattern; setup token handover and `bootstrap_setup_tokens` (REQ-017) |
| 0.4.0 | 2026-10-15 | Business Engineer | ADR-042: setup phase without second factor until the first access of another identity (BR-03, lifecycle, `firstLoginAt`) |

---
identifier: machine-credential
name_de: Maschinen-Credential
name_en: Machine Credential
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Client Secret
  - Service Account Credential
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-026-external-system-integration.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: Machine Credential

## Definition

A machine credential is the OAuth2 client-credentials secret of a service account ([person](./person.md) with `personType = service-account`); it is the only mechanism for machine access to OEA (owner decision Q5).

## Description

External systems (scanners, CI/CD, CMDB importers) authenticate with `client_id` and `client_secret` at the token endpoint and receive an access token of at most 900 s that carries only the `client_id` (REQ-176). The `client_id` is stable per service account across secret rotations and is the `agent_id` in the audit log (REQ-155, ADR-026).

Secrets are stored only as argon2id hashes, have a mandatory expiry (default 90 days, maximum configurable) and are shown in plaintext only once on creation or rotation. Rotation creates a new record with the same `client_id`; the previous record becomes `rotated` and is immediately invalid.

Permissions of a service account come only from [role assignments](./role-assignment.md) of roles flagged `assignableToServiceAccounts`, never from the credential or token.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID | Primary key of the secret version |
| clientId | string | required | | UUID; stable per service account; at most one `active` record per clientId | OAuth2 client ID, audit `agent_id` |
| serviceAccount | reference | required | | target: person with `personType = service-account` | Owner |
| clientSecretHash | string | required | | argon2id | Hashed secret |
| status | enum | required | active | `[active, rotated, revoked]` | State |
| createdAt / createdBy | datetime / principal | required | | holds `service-account:manage` | Issue |
| expiresAt | datetime | required | createdAt + 90 days | ≤ createdAt + configured maximum | Mandatory expiry (REQ-176) |
| lastUsedAt | datetime | optional | | | Last successful token grant |
| revokedAt / revokedBy | datetime / principal | optional | | set on rotation or revocation | End of validity |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| belongsTo | [person](./person.md) (service account) | n:1 | no | Owner service account |

## Lifecycle

```text
active → rotated
   ↓
revoked        (expired = active with expiresAt in the past)
```

**Transitions**:

- `→ active`: creation or rotation by a principal with `service-account:manage` after re-authentication (REQ-172); audit event
- `active → rotated`: on rotation; immediately invalid
- `active → revoked`: manual revocation or deactivation of the service account
- expiry: a grant with an expired secret fails with 401 and an audit event; the admin UI lists secrets expiring within 14 days

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Every machine credential MUST have an `expiresAt`; default 90 days, never beyond the configured maximum | onCreate | REQ-176 |
| BR-02 | The secret is stored only as argon2id hash and returned in plaintext only once | onCreate | REQ-176, REQ-155 |
| BR-03 | Issued access tokens have a lifetime ≤ 900 s and contain the `client_id` but no permissions | onTokenGrant | REQ-176, ADR-032 §7 |
| BR-04 | Only service accounts own machine credentials; no other machine credential type (e.g. API keys) is accepted | onCreate, onRequest | REQ-176 AC4 |

## Examples

```yaml
clientId: "9b3c6e1a-…"
serviceAccount: 2001   # "ci-scanner"
status: active
createdAt: 2026-10-13T10:00:00Z
expiresAt: 2027-01-11T10:00:00Z
```

## Distinction from Similar Objects

- **NOT** [local-credential](./local-credential.md): interactive login material of humans and the system admin account.
- **NOT** [session](./session.md): service accounts have no sessions.

## Usage in Use Cases

- UC-22 (external system integration, see the [use case index](../requirements/use-cases/README.md))
- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (A4)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Business object created for the existing table `machine_credentials` (previously only in `docs/data-model.puml`) to implement REQ-176 (security review SEC-15). Changes against the previous table: surrogate key `id` so that `client_id` stays stable across rotations (the previous design created a new `client_id` on rotation, contradicting "stable agent_id"); `expires_at` mandatory; `active` replaced by `status`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft from REQ-155, REQ-176 (SEC-15) |

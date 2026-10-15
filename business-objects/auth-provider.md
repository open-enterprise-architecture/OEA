---
identifier: auth-provider
name_de: Authentifizierungs-Provider
name_en: Authentication Provider
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Identity Provider
  - OIDC Provider
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-006-auth-stack-choice.md
  - adr: adrs/ADR-036-dev-test-identity-provider.md
---

# Business Object: Authentication Provider

## Definition

An authentication provider is an external OpenID Connect identity provider (Entra ID, Authentik or another OIDC-compliant provider, ADR-006) that the instance trusts for the login of persons. It is identified by its exact issuer.

## Description

Persons are bound to a provider through the immutable pair (`issuer`, `subject`) ([person](./person.md), REQ-166); [IdP group mappings](./idp-group-mapping.md) grant authorization roles for group identifiers of exactly one provider (REQ-167). Providers are configured by principals holding `auth-config:manage` through the admin API; outside the development profile the issuer must use `https` and must not be a loopback address (ADR-036 §6, rules G2/G3).

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID | Primary key |
| providerType | enum | required | generic-oidc | `[entra-id, authentik, generic-oidc]` | Kind of provider (ADR-006); determines provider-specific rules |
| name | string | required | | | Display name |
| issuer | string | required | | unique; exact `iss` value; `https` and no loopback outside `dev` | Issuer URL; claims of other issuers are ignored |
| clientId | string | required | | | OIDC client ID of OEA at the provider |
| clientSecret | secret | optional | | encrypted with the key-encryption key (REQ-188); write-only | Client secret of a confidential client; never returned |
| scopes | string | required | `openid profile email` | must contain `openid` | Requested scopes |
| active | boolean | required | true | | Inactive providers accept no logins |
| createdAt / updatedAt | datetime | required / optional | | | Change tracking |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| bindsPersons | [person](./person.md) (identity bindings) | 1:n | yes | Persons bound by (`issuer`, `subject`) |
| mapsGroups | [idp-group-mapping](./idp-group-mapping.md) | 1:n | yes | Group identifiers of this provider mapped to roles |

## Lifecycle

```text
active ⇄ inactive
```

A provider referenced by identity bindings or group mappings is deactivated, not deleted.

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Outside the development profile the issuer uses `https` and is not a loopback address | onCreate, onUpdate | ADR-036 §6 (G2, G3) |
| BR-02 | The client secret is stored encrypted and never returned by any API | onCreate, onUpdate, onRead | REQ-188 |
| BR-03 | Creating or changing a provider requires an authentication within the last 5 minutes | onCreate, onUpdate | REQ-172 |
| BR-04 | Every change is audited (`auth-provider.changed`) | onCreate, onUpdate | REQ-175 |

## Examples

**Example 1**: Keycloak development realm

```yaml
providerType: generic-oidc
name: Keycloak (development)
issuer: http://localhost:8180/realms/oea-dev   # allowed only in the profile dev
clientId: oea-portal
clientSecret: (write-only)
scopes: openid profile email
active: true
```

## Distinction from Similar Objects

- **NOT** the local authentication of persons (REQ-009 to REQ-011): local credentials belong to the person or the system admin account.
- **NOT** the [IdP group mapping](./idp-group-mapping.md): the provider only defines whom OEA trusts; roles come from mappings.

## Usage in Use Cases

- [UC-01: Login](../requirements/use-cases/UC-01-login.md)
- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Created with change `us-013-system-admin-login`; replaces the placeholder note on `auth_providers` in `docs/data-model.puml`. Claim names and provider-specific 2FA policies follow when the OIDC login uses them.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-15 | Business Engineer | Initial draft for change `us-013-system-admin-login` (providers via admin API, owner decision 2026-10-15) |

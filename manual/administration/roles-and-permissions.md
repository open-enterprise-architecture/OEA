---
title: Roles and permissions
covers:
  screens: []
  stories: [US-143, US-147]
---

# Roles and permissions

Every operation of OEA requires a permission, and permissions come only from roles. OEA ships a fixed permission catalog and built-in roles; `GET /admin/permissions` and `GET /admin/roles` return them.

## Built-in roles

| Role | Permissions | For |
|---|---|---|
| `portal-reader` | `portal:read` | reading shared content in the portal |
| `viewer` | `portal:read`, `entity:read` | reading the repository |
| `analyst` | `viewer` + `analytics:query` | analytics queries |
| `enterprise-architect` | `viewer` + `entity:write`, `entity:delete`, `entity:restore`, `catalog:manage`, `dashboard:manage`, `viewpoint:manage`, `plateau:go-live`, `webhook:manage` | maintaining the architecture |
| `integration-writer` | `entity:read`, `entity:write` | integrations that write entities |
| `auditor` | `audit:read` | reading and verifying the audit log |
| `metamodel-admin` | `metamodel:write` | changing and importing the metamodel |
| `security-admin` | `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage` | visibility rules, persons and roles |
| `system-admin` | `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage` | administering the instance |

The local system admin account holds the role `system-admin`. **Privileged roles** – roles with an admin-class permission (`metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`) – are effective only in a session confirmed with a second factor. For a login through an identity provider, the provider has to report the second factor in `amr`.

## Grant roles through IdP groups

You grant roles by mapping a group of the identity provider to a role. Every person in that group gets the role at their next login:

```bash
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"groupIdentifier\": \"<group-uuid>\", \"roleId\": \"viewer\"}" \
  "$OEA/admin/idp-group-mappings"
```

- The group identifier must be the immutable UUID of the group (the group object ID of Entra ID, Authentik and Keycloak); display names are rejected with `422`.
- A person cannot map a group that is contained in their own groups claim (separation of duties).
- `GET /admin/idp-group-mappings` lists the mappings; `PUT` and `DELETE /admin/idp-group-mappings/{mappingId}` change and remove one.
- Like the first identity binding, the first group mapping ends the setup phase.

## When group membership changes

OEA re-evaluates the group membership with the refresh token of the provider at the latest 15 minutes after the previous evaluation: if you remove a person from a group at the provider, the derived role is gone within 15 minutes. Without a refresh token, with an unreachable provider, or with a missing or truncated groups claim, no IdP-derived role applies (audit event `auth.idp-groups-unusable`).

A session established through an identity provider ends at the first of: 30 minutes without activity, 12 hours after the login, or the expiry or rejection of the refresh token. OEA never extends a token of the provider.

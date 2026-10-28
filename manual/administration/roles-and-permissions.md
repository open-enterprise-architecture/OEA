---
title: Roles and permissions
covers:
  screens: []
  stories: [US-014, US-018, US-143, US-147]
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
- Every mapping shows in `lastMatchedAt` when it last granted its role in a login; `null` means it never has. A privileged role counts only if the person logged in with a second factor.
- If you map a group to `system-admin` and no login at this instance has shown that group yet, OEA saves the mapping anyway and returns the warning `group-not-seen` in `warnings`: the mapping grants system administration to nobody known until a member of the group logs in. Check the group identifier at the provider. OEA cannot see the group members, so it can only report what logins have shown.

## Hand over system administration to an IdP group {#hand-over-system-administration-to-an-idp-group}

You don't need the local system admin account once your administrators sign in through the identity provider:

1. Map the administrators' group to the role `system-admin` (see above). Its members need a second factor at the provider, otherwise the role is not effective.
2. Have a member of the group log in. The mapping's `lastMatchedAt` is then set, and only from then on does it count as a working path to system administration.
3. Deactivate the local system admin account (see [Deactivate the local system admin account](./operations#deactivate-the-local-system-admin-account)). OEA refuses this as long as no mapping to `system-admin` has worked in a login.

## When group membership changes

OEA re-evaluates the group membership with the refresh token of the provider at the latest 15 minutes after the previous evaluation: if you remove a person from a group at the provider, the derived role is gone within 15 minutes. Without a refresh token, with an unreachable provider, or with a missing or truncated groups claim, no IdP-derived role applies (audit event `auth.idp-groups-unusable`).

A session established through an identity provider ends at the first of: 30 minutes without activity, 12 hours after the login, or the expiry or rejection of the refresh token. OEA never extends a token of the provider.

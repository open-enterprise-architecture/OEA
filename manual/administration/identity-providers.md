---
title: Identity providers and persons
covers:
  screens: []
  stories: [US-004, US-007, US-023, US-026, US-027, US-028, US-030, US-031, US-148]
---

# Identity providers and persons

Persons log in through an identity provider (IdP) with OpenID Connect. You store the provider in OEA, create the persons and bind each person to their identity at the provider. The stored, enabled providers are the only source of the login.

## Register OEA at the identity provider

Register OEA as a confidential web client with the authorization code flow and PKCE (`S256`) and the redirect URI `<public URL>/api/v1/auth/oidc/callback`. The ID token must carry the group membership as immutable group identifiers in the claim `groups` and, for privileged roles, the authentication methods in `amr`:

| Provider | Scopes | Groups | Second factor |
|---|---|---|---|
| Microsoft Entra ID | `openid profile email offline_access` (`offline_access` yields the refresh token) | token configuration: groups claim with group object IDs; too many groups produce an overage indicator, which OEA treats as no groups | `amr` contains `mfa` when a Conditional Access policy required MFA |
| Authentik | `openid profile email` | property mapping that emits the group UUIDs as `groups` | property mapping for `amr` (`pwd`, `otp`, `mfa`) |
| Keycloak | `openid profile email` | mapper that emits group identifiers, not names | authentication flow with method references (`pwd`, `otp`) and a *max age* of at least the OEA session lifetime (for example 43200 s); with max age 0, Keycloak drops `amr` from later tokens |

## Store the provider

The issuer must use `https` and must not be a loopback address. The client secret is write-only: OEA never returns it.

```bash
read -rs OEA_OIDC_CLIENT_SECRET
jq -n --arg s "$OEA_OIDC_CLIENT_SECRET" '{name: "Corporate IdP", type: "oidc", vendor: "entra-id",
    issuerUrl: "https://login.microsoftonline.com/<tenant-id>/v2.0", clientId: "<client-id>", clientSecret: $s}' |
  curl "${auth[@]}" -d @- "$OEA/admin/auth/providers" | tee provider.json
PROVIDER=$(jq -r .id provider.json)
```

`GET` and `PUT /admin/auth/providers/{id}` read and change a provider; `"enabled": false` deactivates it. The backend refuses to start if an enabled provider has an issuer without `https` or on a loopback address.

If the backend reaches the provider under another address than the browsers (split-horizon network), map the issuer to the internal address in the configuration; the issuer in the discovery document and in the tokens is still checked against the stored issuer:

```yaml
oea:
  security:
    oidc:
      discovery-locations:
        - issuer: https://login.example.org/realms/oea
          location: https://idp.internal.example.org/realms/oea
```

## Create persons and bind identities

A person gets a session only if the pair of issuer and subject (`sub`) is bound to a person in status `invited` or `active`. Bindings are never derived from e-mail addresses. The first login changes `invited` to `active`.

```bash
PERSON=$(curl "${auth[@]}" -d '{"name": "Ada Lovelace", "email": "ada@example.org"}' "$OEA/admin/persons" | jq -r .id)
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"subject\": \"<subject>\"}" \
  "$OEA/admin/persons/$PERSON/identities"
```

Use the immutable subject of the provider (Entra ID: the object ID `oid`). The first binding ends the setup phase and needs a session confirmed with a second factor (see [First administrator](../setup/first-administrator#the-setup-phase)); without it, the answer is `403` with `urn:oea:problem:second-factor-required`.

Further operations: `GET /admin/persons`, `GET /admin/persons/{personId}`, `GET` and `DELETE /admin/persons/{personId}/identities[/{identityId}]`.

## Set an initial password {#set-an-initial-password}

If password login of persons is possible (see [Operations](./operations#password-login-of-persons)), you can give a person an initial password instead of an identity binding (you need `person:manage`). Enter one that meets the [password policy](#password-policy), or let OEA generate one:

```bash
curl "${auth[@]}" -d '{"password": "<meets the password policy>"}' "$OEA/admin/persons/$PERSON/password"
# or: generated, returned this once in "password"
curl "${auth[@]}" -d '{"generate": true}' "$OEA/admin/persons/$PERSON/password"
```

- OEA stores only a hash. The answer contains the time the initial password expires (default 72 hours). It contains the password only if OEA generated it, and only this once; pass it on to the person through a separate channel.
- An entered password that does not meet the policy is refused with `422` and problem type `password-policy-violation`; `invalidFields` names every unmet rule.
- The person logs in with the e-mail address of the person record and must choose an own password before doing anything else.
- A person who already has a password, your own person, a service account or a person that is neither invited nor active is refused.
- Setting the password is recorded in the audit trail (`person.password-set`).

## Reset a password {#reset-a-password}

A person who forgot the password gets a new temporary one from you (you need `person:manage`):

```bash
curl "${auth[@]}" -d '{"reason": "forgotten"}' "$OEA/admin/persons/$PERSON/password/reset"
```

- OEA revokes the old password at once and answers with a generated temporary password and its expiry. The answer is the only place the password appears; pass it on through a separate channel.
- Every session of the person ends. At the next login, the person must choose an own password before doing anything else.
- Your own person, a person without a password and a service account are refused.
- If the person holds a role with an administrative permission through a local role assignment, only the local system admin account may reset the password; anyone else is refused with `403`, recorded as `authz.denied`. Roles from IdP groups are not taken into account, because such persons log in through the identity provider.
- The reset is recorded in the audit trail (`person.password-reset`) with the reason, never with the password.
- If the person has an authenticator app, the next login still needs its code before the password change.

## Reset second factors {#reset-second-factors}

A person who lost every device with an authenticator app gets the second factors reset by you (you need `person:manage`):

```bash
curl "${auth[@]}" -d '{"reason": "phone lost"}' "$OEA/admin/persons/$PERSON/second-factors/reset"
```

- OEA revokes every second factor of the person and ends all sessions of the person; the answer names how many second factors were revoked.
- The person then logs in with the password alone, and OEA offers to set up a new authenticator app.
- Your own person, a person without a second factor and a service account are refused. As for the password reset, only the local system admin account resets a person who holds a role with an administrative permission through a local role assignment.
- The reset is recorded in the audit trail (`person.second-factors-reset`) with the reason.

## Second-factor policy {#second-factor-policy}

Your organization can require a second factor for every password login of a person (you need `settings:manage`):

```bash
curl "${auth[@]}" "$OEA/admin/settings/second-factor-policy"
curl "${auth[@]}" -X PUT -d '{"enforced": true, "exemptRoles": ["viewer"]}' \
  "$OEA/admin/settings/second-factor-policy"
```

- While `enforced` is `true`, a person without a passkey or authenticator app must set one up after the password login before doing anything else; other requests are refused with problem type `second-factor-enrollment-required`.
- `exemptRoles` lists roles that need no second factor. A person is exempt only if they hold at least one role through a local role assignment and every such role is in the list; a person without roles is not exempt. A role with an administrative permission, or an unknown role, is refused with `422`.
- While the enforcement is on, a person who is not exempt cannot remove the last second factor; your [factor reset](#reset-second-factors) still works, and the person sets up a new one at the next login.
- The enforcement covers the password login of persons. Logins through an identity provider follow its rules, and the local system admin account keeps its own (see [First administrator](../setup/first-administrator)).
- The operator can force the enforcement (see [Operations](./operations#second-factor-enforcement)); `enforcedByOperator` then is `true`, and `enforced: false` is refused with `422`.
- Every change is recorded in the audit trail (`settings.second-factor-policy-changed`). Announce the enforcement before you switch it on: it applies at the next password login of each person.

## Password policy {#password-policy}

The password policy applies to every new local password: initial passwords, resets and own changes (you need `settings:manage`):

```bash
curl "${auth[@]}" "$OEA/admin/settings/password-policy"
curl "${auth[@]}" -X PUT -d '{"minLength": 16, "requireUppercase": true, "requireLowercase": true,
  "requireDigits": true, "requireSpecialChars": true, "expiryDays": 90, "historyCount": 5,
  "personPasswordLogin": true}' "$OEA/admin/settings/password-policy"
```

| Field | Meaning |
|---|---|
| `minLength` | Minimum length, 12 to 128 |
| `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars` | Character types a password must contain; special characters are all printable ASCII characters that are neither letters nor digits |
| `expiryDays` | Days after which a self-chosen password must be changed at the next login; `0` = never |
| `historyCount` | Number of previous passwords, including the current one, that must not be reused; `0` to `24` |
| `personPasswordLogin` | Password login of persons; works only while the operator permits it (`personPasswordLoginPermitted`) |

- The answer also contains the operator's `floors`. A value below a floor, or a minimum length shorter than the number of required character types, is refused with `422`; `invalidFields` names each field.
- Every change is recorded in the audit trail (`settings.password-policy-changed`) with the old and the new values.
- Passwords already set stay valid; the rules apply when a password is set and, for the expiry, at the next login.

## Take access away: inactive and offboarded {#take-access-away}

A person is `invited` until the first login, then `active`. With `person:manage` you change the status:

```bash
curl "${auth[@]}" -d '{"status": "inactive", "reason": "parental leave"}' "$OEA/admin/persons/$PERSON/status"
curl "${auth[@]}" -d '{"status": "offboarded", "reason": "left the company"}' "$OEA/admin/persons/$PERSON/status"
```

| Change | Effect |
|---|---|
| `active` → `inactive` | all sessions end at once; login is refused; local role assignments are kept |
| `inactive` → `active` | login works again; the kept assignments apply again |
| `invited`, `active` or `inactive` → `offboarded` | all sessions end at once; login is refused; every local role assignment is revoked (`revokedBy` = you, reason "offboarding") |

- Roles from IdP groups need no action: an offboarded or inactive person gets no session, even while still a member of the group.
- The person and its history stay: earlier changes and audit entries remain attributed to it.
- Other changes are refused with `409` (`invalid-status-transition`): an unchanged status, `invited` → `active` (only the first login does that), and every change of an offboarded person. Reactivating offboarded persons is not possible yet.
- You cannot change your own status (`403`). Service accounts are refused with `422`.
- If the person is the last way to administer the instance (an open-ended `system-admin` assignment), the change is refused with `409` (`last-admin-path`, see [Assign roles to a single person](./roles-and-permissions#assign-roles-to-a-single-person)).
- Every change is recorded in the audit trail (`person.status-changed`, `session.revoked` per ended session, `role.revoked` per revoked assignment).

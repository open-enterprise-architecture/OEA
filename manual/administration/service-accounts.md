---
title: Service accounts
covers:
  screens: [SCR-041]
  stories: [US-142, US-149]
---

# Service accounts

External systems such as scanners, CI pipelines or a CMDB importer use the API without a person logging in. Each of them gets a **service account** with a client ID and a client secret. Everything a service account changes is recorded in the audit log under its client ID, so machine changes can always be told apart from changes made by persons.

You manage service accounts on the page **Service accounts** (link in the header). You need the permission `service-account:manage`, which the role `system-admin` has. Choosing roles for a service account also needs `role:assign`.

The external system exchanges the client ID and secret for an access token of 15 minutes and calls the API with it; see [API access without a browser](../api/access-without-browser). The column **Last use** shows when a service account last obtained a token.

## Create a service account

1. Choose **New service account**.
2. Enter a name (1 to 100 characters, unique among service accounts regardless of upper and lower case) and optionally a description, such as the system the account serves.
3. Choose how many days the secret stays valid: 90 days unless you enter something else, at most the maximum of your instance (365 days unless it is [configured](#maximum-secret-validity) differently).
4. Choose the roles. Only roles that service accounts may hold are offered; `integration-writer` is selected in advance.
5. Choose **Create service account**.

OEA then shows the **client secret once**. Copy it and store it right away, for example as a secret of your CI/CD system. After you close the dialog, nobody can display it again: OEA stores only a hash of it. If the secret is lost, [rotate](#rotate-the-secret) it.

The secret starts with `oea_cs_`, so secret scanners can recognize it if it ever ends up in a repository or a log.

## Roles of a service account

A service account holds only roles that are marked as assignable to service accounts. Of the built-in roles, these are:

| Role | Lets the external system |
|---|---|
| `integration-writer` | read and write entities |
| `viewer` | read entities |
| `auditor` | read the audit log |

A role with an administrative permission is never assignable to a service account, so a leaked secret can never be used to administer OEA. A request with such a role is refused with `422` naming the role, and the attempt is recorded in the audit log. Custom roles can be marked as assignable if they contain no administrative permission (see [Roles and permissions](./roles-and-permissions)).

To change the roles, choose **Edit roles** in the row of the service account. Every added and removed role is recorded in the audit log like any role assignment.

## Expiry of the secret

Every secret expires. The column **Secret expires** shows when; a secret that expires within 14 days is marked **Expires soon**. Rotate it in time and hand the new secret to the external system.

## Rotate the secret {#rotate-the-secret}

Choose **Rotate secret** and confirm. OEA creates a new secret, shows it once and invalidates the previous one **at once**, together with every access token obtained with it; the client ID stays the same. Rotate the secret when it expires soon, when it may have leaked, or when a person who knew it leaves.

## Deactivate a service account

Choose **Deactivate** and confirm. The service account becomes inactive, its secret is revoked and its roles are removed. **A deactivation is final**: a deactivated service account cannot be reactivated, and every further change is refused with `409`. For a new integration, create a new service account; its new client ID keeps the audit log unambiguous.

## Log in again for sensitive actions

Creating a service account, rotating its secret and deactivating it need a login within the last 5 minutes. Otherwise OEA asks you to log in again (`401 reauthentication-required`). Changing the roles does not need a new login.

In the setup phase, the first service account ends the setup phase like the first role assignment. It needs a second factor (see [First administrator](../setup/first-administrator#the-setup-phase)).

## Through the API

The page uses the operations under `/admin/service-accounts`; with a [session of the API](./index#log-in-for-administration-through-the-api):

```bash
# create; the answer contains the secret once
curl "${auth[@]}" -d '{"name": "ci-scanner", "roles": ["integration-writer"], "secretValidityDays": 30}' \
  "$OEA/admin/service-accounts" > ci-scanner.json
CLIENT_ID=$(jq -r .id ci-scanner.json)
# store the secret of ci-scanner.json in your secret store, then delete the file

# list; secretExpiresSoon marks secrets that expire within 14 days
curl "${auth[@]}" "$OEA/admin/service-accounts?active=true" | jq '.[] | {name, roles, secretExpiresAt, secretExpiresSoon}'

# rotate; the answer contains the new secret once
curl "${auth[@]}" -X POST -d '{}' "$OEA/admin/service-accounts/$CLIENT_ID/credentials/rotate" > ci-scanner.json

# deactivate for good
curl "${auth[@]}" -X DELETE "$OEA/admin/service-accounts/$CLIENT_ID"
```

## Maximum secret validity {#maximum-secret-validity}

The longest validity an administrator can choose is a setting of the backend, 365 days by default. Set it between 1 and 3650 days; the backend does not start with another value:

```bash
# environment of the backend (or --oea.service-accounts.secret-max-validity-days=180)
OEA_SERVICEACCOUNTS_SECRETMAXVALIDITYDAYS=180
```

If the maximum is below 90 days, a secret without a chosen validity gets the maximum.

## Cleanup of expired tokens {#token-cleanup}

OEA deletes access tokens one day after their expiry; their issue stays in the audit log as `token.issued`. The job runs every hour; set another interval as an ISO 8601 duration:

```bash
# environment of the backend (or --oea.access-tokens.cleanup-interval=PT30M)
OEA_ACCESSTOKENS_CLEANUPINTERVAL=PT30M
```

---
title: First administrator
covers:
  screens: []
  stories: [US-013]
---

# First administrator

A new instance has no account. You create the first one – the local **system admin account** – with a one-time setup token that only someone with access to the server can read. No identity provider is needed for this.

## Read the setup token

As long as no system admin account exists, the backend writes a setup token to the file `/run/oea/setup-token` inside the container and logs where to find it. The token itself never appears in the log or in an API response:

```bash
docker compose exec backend cat /run/oea/setup-token
```

## Create the system admin account

Open the setup page `/setup` of the web interface (the start page shows *Setup required* while no account exists) and fill in:

| Field | Meaning |
|---|---|
| Setup token | the token from the file |
| Login name | the login name of the system admin account |
| Password, Repeat password | 12 to 128 characters |
| Instance slug | short, stable name of the instance; it is used in external references (`urn:oea:<slug>:<id>`) and cannot be changed later |

Choose *Create system admin account*. The page confirms the account and the instance slug.

Through the API you send the same data to `POST /api/v1/setup`; `GET /api/v1/setup/status` tells you whether setup is still required.

- Bootstrapping works exactly once. Every further attempt is rejected, and the token is no longer valid.
- A missing, wrong or expired token is rejected without creating an account.
- Every completed and every rejected attempt is recorded in the audit trail (`system.bootstrapped`, `system.bootstrap-rejected`).

## The setup phase

After the bootstrapping, the instance is in its **setup phase**: the system admin account logs in with login name and password only. The setup phase ends with the first action that gives another identity access – the first identity binding or IdP group mapping (see [Identity providers and persons](../administration/identity-providers)). This action needs a second factor:

1. Log in with the system admin account (see [Logging in](../usage/login)).
2. Open *Profile* and choose *Set up a second factor*, then *Set up authenticator app*. Scan the QR code with your authenticator app and enter the code from the app. The session is now confirmed with the second factor.
3. Create the first identity binding or group mapping. This ends the setup phase for good (audit event `system.setup-phase-ended`).

From then on, every login of the system admin account needs a code of the authenticator app.

::: warning
During the setup phase, only the password and the login throttling protect the instance. End the setup phase before you make the instance publicly reachable.
:::

The system admin account administers the instance (identity providers, persons, roles). It does not work with architecture content and cannot import metamodel packages; these tasks belong to persons with the corresponding roles (separation of duties, see [Roles and permissions](../administration/roles-and-permissions)).

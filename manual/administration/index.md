---
title: Administration
covers:
  screens: []
  stories: []
---

# Administration

Administration has no screens in this version; you administer the instance through the [API](../api/). The examples of this chapter use `curl` and `jq` and keep passwords and secrets out of command lines.

- [Identity providers and persons](./identity-providers): connect an OpenID Connect provider and bind persons to their identities
- [Roles and permissions](./roles-and-permissions): the built-in roles and how you grant them through IdP groups
- [Metamodel packages](./metamodel-packages): import the starter configuration or your own metamodel
- [Operations](./operations): database roles, request limits, key rotation, audit trail verification and PostgreSQL upgrades

## Log in for administration through the API

Log in with the system admin account. The session is the cookie `__Host-OEA_SESSION`; the CSRF token is in the response body and must accompany every changing request:

```bash
OEA=https://oea.example.org/api/v1
read -rs OEA_ADMIN_PASSWORD

jq -n --arg p "$OEA_ADMIN_PASSWORD" '{loginName: "admin", password: $p}' |
  curl -sS -D login.headers -o login.json -H 'Content-Type: application/json' -d @- "$OEA/auth/local/login"
SESSION=$(sed -n 's/^set-cookie: __Host-OEA_SESSION=\([^;]*\).*/\1/Ip' login.headers)
CSRF=$(jq -r .csrfToken login.json)
auth=(-sS -H "Cookie: __Host-OEA_SESSION=$SESSION" -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json')
```

After the setup phase, the password step answers `401` with the problem type `urn:oea:problem:second-factor-required` and a `loginTransaction`; complete the login with `POST /auth/local/login/totp` and `{"loginTransaction": "…", "code": "…"}`.

Changing a provider and enrolling a second factor need a login within the last 5 minutes; log in again if the answer is `401 reauthentication-required`. Log out at the end:

```bash
curl "${auth[@]}" -X POST "$OEA/auth/logout"
```

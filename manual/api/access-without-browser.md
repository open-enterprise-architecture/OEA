---
title: API access without a browser
covers:
  screens: []
  stories: [US-142, US-149]
---

# API access without a browser

Scripts, CI pipelines and integrations call the API with an **access token** instead of a browser session. They obtain it with the client ID and secret of a [service account](../administration/service-accounts): the OAuth2 client credentials grant. Everything such a client changes is recorded in the audit log under the client ID of its service account.

## Request a token

Send the client ID and the secret as HTTP Basic authentication to `POST /oauth2/token`, with `grant_type=client_credentials` in the form body. Keep the secret out of the command line and the shell history, for example by reading it from your secret store into an environment variable:

```bash
OEA=https://oea.example.org/api/v1
CLIENT_ID=9b3c6e1a-…            # the ID of the service account
read -rs OEA_CLIENT_SECRET       # or provided by your CI system

TOKEN=$(curl -sS --fail -u "$CLIENT_ID:$OEA_CLIENT_SECRET" \
  -d grant_type=client_credentials "$OEA/oauth2/token" | jq -r .access_token)
```

`curl -u` sends the credentials in the `Authorization` header. The answer:

```json
{ "access_token": "oea_at_…", "token_type": "Bearer", "expires_in": 900 }
```

The token is valid for **900 seconds** (15 minutes). There is no refresh token: request a new token when the old one is about to expire, or when a request is answered with `401`.

## Call the API with the token

Send the token in the `Authorization` header of every request:

```bash
curl -sS -H "Authorization: Bearer $TOKEN" "$OEA/me" | jq .principal
curl -sS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d @entities.json "$OEA/entities/batch"
```

- A request with a token needs no CSRF token.
- OEA accepts the token only in the `Authorization` header, never as a query parameter or a cookie.
- A request that carries a token and a session cookie at the same time is refused with `401`.
- The token itself carries no information. Its permissions are those of the roles of the service account at the time of each request, so a role change applies to the next request.
- A service account cannot use the operations of the own account, such as changing a password, and cannot administer OEA.

## Errors

| Answer | Meaning |
|---|---|
| `401` with `{"error": "invalid_client"}` at `/oauth2/token` | The client ID or secret is wrong, the secret has expired, or the service account is deactivated. OEA gives the same answer in every case. |
| `400` with `unsupported_grant_type` or `invalid_request` | Only `grant_type=client_credentials` is supported. |
| `401` with `WWW-Authenticate: Bearer` | The token is unknown, expired or revoked. Request a new one. |
| `403` | A role of the service account lacks a required permission. |
| `429` with `Retry-After` | Too many failed token requests (5 within 15 minutes per client ID, 100 within 10 minutes per address), or the token endpoint is busy. Wait as many seconds as `Retry-After` says. |

Rotating the secret or deactivating the service account revokes all its tokens at once.

## What the audit log records

- `token.issued` for every token, with the client ID; `token.denied` for a refused token request, with the client ID as sent, marked as unverified; `token.revoked` for every token revoked by a rotation or deactivation.
- Every change made with a token carries the actor type `service-account` and the client ID as `agentId`. The header `X-Agent-Id` is stored only as an informational label.
- Neither the secret nor the token ever appears in the audit log or the application log.

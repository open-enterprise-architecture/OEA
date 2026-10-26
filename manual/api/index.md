---
title: Using the API
covers:
  screens: []
  stories: []
---

# Using the API

Every function of OEA is available through its REST API. The API is described by the contract `api/openapi.yaml` (OpenAPI 3.1); the pages of this chapter are generated from it and list only the operations implemented in this version.

## Base URL

All operations are under `/api/v1` of the instance, for example `https://oea.example.org/api/v1/health`.

## Authentication

There is no anonymous access, except for a few public operations (health, setup, login). The web interface uses a **session**:

1. Log in with `POST /auth/local/login` (local system admin account) or through an identity provider (`GET /auth/oidc/{providerId}/start`).
2. The session is the cookie `__Host-OEA_SESSION`. The login answer contains a `csrfToken`.
3. Send the token in the header `X-XSRF-TOKEN` with every changing request (`POST`, `PUT`, `PATCH`, `DELETE`).
4. End the session with `POST /auth/logout`.

[Log in for administration through the API](../administration/#log-in-for-administration-through-the-api) shows this with `curl`.

## Permissions

Every operation states its required access:

- **public**: no authentication
- **any authenticated principal**: any session; checks on the object itself may apply
- **permission**: the listed permissions are required; *one of the permissions* means that one of them suffices; *additionally … when …* names permissions that a particular request needs in addition

Permissions come from roles, see [Roles and permissions](../administration/roles-and-permissions).

## Errors

Errors are answered as RFC 9457 problem details (`application/problem+json`). The field `type` names the problem as `urn:oea:problem:<name>`, for example `urn:oea:problem:validation-failed`; the reference lists the problem types per response.

- `401`: not authenticated, or the session has ended
- `403`: a required permission is missing; the answer does not reveal which one
- `404`: the object does not exist – or you may not see it
- `413`: the request body is too large (see [Request size limits](../administration/operations#request-size-limits))
- `422`: the request is well-formed but not valid

## Reference

The reference has one page per area of the API; open it in the sidebar.

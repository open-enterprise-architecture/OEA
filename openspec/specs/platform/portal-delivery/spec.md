# platform/portal-delivery Specification

## Purpose

Defines how the operator setup delivers the web portal: the backend serves the built portal from the same origin as the API, including links into the portal, with the security headers of REQ-187 and a caching policy for its files.

## Requirements

### Requirement: Backend serves the web portal

The backend image SHALL contain the built web portal and SHALL serve it from the same origin as the API. Every `GET` on a path outside `/api` that matches no file SHALL be answered with the portal's `index.html`; every unknown path under `/api` SHALL keep its 404 problem response. The portal files SHALL be readable without a session. A backend without a configured portal directory SHALL serve no portal.

Trace: #76, WS-03, REQ-187, ADR-036 §3, owner decision 2026-11-07

#### Scenario: Portal in the operator setup

- **WHEN** the operator setup is started with `docker compose up` and `/` is requested from the backend
- **THEN** the response is the portal's `index.html` with status 200

#### Scenario: Link into the portal

- **WHEN** `/portal/catalogs/{id}` is requested directly, for example after a reload
- **THEN** the response is the portal's `index.html` with status 200

#### Scenario: Unknown API path

- **WHEN** `/api/v1/unknown` is requested
- **THEN** the response is a 404 problem and not the portal

#### Scenario: Without a portal directory

- **WHEN** the backend runs without a configured portal directory, as in the development environment
- **THEN** `/` answers 404 and the API works unchanged

### Requirement: Security headers of the web portal

Every response with a portal file SHALL carry the security headers of the API and a content security policy that allows only the portal's own origin and forbids framing, inline scripts and `eval`. The built portal SHALL run under this policy without a policy violation.

Trace: REQ-187 AC1, #76, WS-03

#### Scenario: Headers of index.html

- **WHEN** the portal's `index.html` is requested
- **THEN** the response carries `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and `X-Frame-Options: DENY`
- **AND** its content security policy has `default-src 'self'`, `script-src 'self'`, `connect-src 'self'`, `object-src 'none'`, `base-uri 'none'`, `form-action 'self'` and `frame-ancestors 'none'`, and contains neither `'unsafe-eval'` nor `'unsafe-inline'` for scripts

#### Scenario: Portal runs under the policy

- **WHEN** the built portal is loaded in a browser from the backend, and a reader logs in and opens a catalog
- **THEN** the browser reports no content security policy violation

#### Scenario: API headers unchanged

- **WHEN** an API response is inspected
- **THEN** its content security policy is still `default-src 'none'; frame-ancestors 'none'`

### Requirement: Caching of the portal files

The portal's `index.html` and every file it is served for SHALL carry `Cache-Control: no-cache`. The files under `/assets/`, whose names contain a content hash, SHALL carry `Cache-Control: public, max-age=31536000, immutable`. Authenticated API responses SHALL keep `Cache-Control: no-store`.

Trace: REQ-187 AC1, #76

#### Scenario: New version after an update

- **WHEN** the backend is updated and a reader reloads the portal
- **THEN** the browser revalidates `index.html` and loads the new hashed files

#### Scenario: Hashed asset

- **WHEN** a file under `/assets/` is requested
- **THEN** the response carries `Cache-Control: public, max-age=31536000, immutable`

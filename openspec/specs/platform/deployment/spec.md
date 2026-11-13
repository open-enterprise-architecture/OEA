# platform/deployment Specification

## Purpose

Defines how OEA is started: a one-command development environment with a real identity provider, and an operator setup that is safe by default because it never starts with default credentials.

## Requirements

### Requirement: One-command development environment

The development environment SHALL start with a single command and SHALL include PostgreSQL, the one-shot migration step, a real OIDC identity provider with the development realm, the backend and the frontend development server. All services of the development file SHALL start only when the development profile is requested explicitly.

Trace: REQ-075, REQ-183, ADR-036

#### Scenario: Development environment starts

- **WHEN** a developer runs `docker compose -f compose.dev.yaml --profile dev up` on a machine with Docker
- **THEN** the migration step completes and exits, the identity provider serves the development realm, and the backend health endpoint responds with HTTP 200 within 60 seconds

#### Scenario: Development services require the profile

- **WHEN** `docker compose -f compose.dev.yaml up` is run without `--profile dev`
- **THEN** no service of the development file starts

#### Scenario: Real identity provider in development

- **WHEN** the development environment and the integration test setup are inspected
- **THEN** authentication uses a real OIDC identity provider container and no mock token issuer is part of the application code

### Requirement: Development ports bound to loopback

Every port published by the development environment SHALL be bound to the loopback interface only.

Trace: REQ-183, ADR-036

#### Scenario: No development port on external interfaces

- **WHEN** the development Compose file is checked in CI
- **THEN** the check fails if any published port is not bound to `127.0.0.1`

### Requirement: Operator setup requires explicit secrets

The operator Compose file SHALL contain no identity provider service, no literal secret and no default value for any secret, and SHALL refuse to start any service while a required secret is unset.

Trace: REQ-075, REQ-156, REQ-183, REQ-188, ADR-036

#### Scenario: Empty environment

- **WHEN** `docker compose up` is executed with the operator Compose file and an empty environment (no `.env`)
- **THEN** it fails with a message naming the missing variables, and no service starts with default credentials

#### Scenario: All secrets supplied

- **WHEN** `docker compose up` is executed with the operator Compose file and every required secret set explicitly
- **THEN** the migration step applies all migrations, the application connects to PostgreSQL, and the health endpoint responds with HTTP 200 within 60 seconds

#### Scenario: Default secret in the operator file

- **WHEN** the operator Compose file is checked in CI
- **THEN** the check fails if it contains a literal secret or a `${VAR:-…}` / `${VAR-…}` default for a secret variable

### Requirement: Client address behind a trusted reverse proxy

The backend SHALL take the client address, scheme, host and port of a request from the `X-Forwarded-*` headers only when the direct peer is a trusted proxy, and from the connection otherwise. The throttling per source address, the audit source address and the origin check SHALL use this client address and origin.

Trace: REQ-168, REQ-175, REQ-187, ADR-036 §8; AA-01 of the application security audit 2026-11

#### Scenario: Two clients behind the trusted proxy are throttled separately

- **WHEN** 100 failed local logins arrive within 10 minutes through a trusted proxy with `X-Forwarded-For: 203.0.113.10`, and then a login with the correct password arrives through the same proxy with `X-Forwarded-For: 203.0.113.20`
- **THEN** further logins for `203.0.113.10` are answered with 401 and `Retry-After`, and the login for `203.0.113.20` succeeds

#### Scenario: Throttling just below the limit

- **WHEN** 99 failed local logins for different login names arrive within 10 minutes through a trusted proxy with `X-Forwarded-For: 203.0.113.10`
- **THEN** the next login with the correct password from `203.0.113.10` succeeds

#### Scenario: Forwarded header from an untrusted peer

- **WHEN** a request whose direct peer is not a trusted proxy carries `X-Forwarded-For: 203.0.113.30`
- **THEN** the header is ignored, and the throttling and the audit use the address of the direct peer

#### Scenario: Audit source address

- **WHEN** a failed login arrives through a trusted proxy with `X-Forwarded-For: 203.0.113.40`
- **THEN** the audit event `auth.login-failed` records the source address `203.0.113.40`

#### Scenario: Origin behind a TLS proxy

- **WHEN** a cookie-authenticated `POST` with a valid CSRF token and `Origin: https://oea.example.org` arrives through a trusted proxy that sends `X-Forwarded-Proto: https` and `X-Forwarded-Host: oea.example.org`
- **THEN** the origin is accepted

#### Scenario: Foreign origin behind a TLS proxy

- **WHEN** the same request carries `Origin: https://evil.example.org`
- **THEN** it is rejected with 403 and has no effect

### Requirement: Trusted proxies configurable

By default, the backend SHALL trust loopback and the private networks as proxies; the operator SHALL be able to replace this list with `OEA_TRUSTED_PROXIES`.

Trace: REQ-168, ADR-036 §8; AA-01 of the application security audit 2026-11; owner decision 2026-11-13

#### Scenario: Default trusts the Compose gateway

- **WHEN** a request with `X-Forwarded-For: 203.0.113.50` arrives from a peer in `172.16.0.0/12` and `OEA_TRUSTED_PROXIES` is not set
- **THEN** the client address is `203.0.113.50`

#### Scenario: Operator narrows the trusted proxies

- **WHEN** the operator sets `OEA_TRUSTED_PROXIES` to an address range that does not contain the direct peer, and a request from that peer carries `X-Forwarded-For`
- **THEN** the header is ignored

### Requirement: Proxy requirements documented

The installation guide of the user manual SHALL state which headers the reverse proxy sends and overwrites, which proxies are trusted by default, and how the operator narrows them.

Trace: REQ-196, ADR-045; AA-01 of the application security audit 2026-11

#### Scenario: Installation guide names the headers

- **WHEN** the installation guide is read in English or German
- **THEN** it names `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-Port`, the default trusted networks and the variable `OEA_TRUSTED_PROXIES`

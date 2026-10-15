# Spec Delta

## Purpose

Defines how OEA is started: a one-command development environment with a real identity provider, and an operator setup that is safe by default because it never starts with default credentials.

## ADDED Requirements

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

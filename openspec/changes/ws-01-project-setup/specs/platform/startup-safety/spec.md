# Spec Delta

## Purpose

Defines the startup checks that keep unsafe configurations out of production and the guarantee that no test-only authentication code ships in the production artifact.

## ADDED Requirements

### Requirement: Supported PostgreSQL version

The application SHALL refuse to start against a PostgreSQL server older than the minimum supported version 15.

Trace: REQ-156 AC3

#### Scenario: PostgreSQL too old

- **WHEN** the application starts with a datasource pointing to PostgreSQL 14
- **THEN** startup fails with a clear message naming the minimum supported version

### Requirement: No unsafe settings outside development

Outside the development profile the application SHALL refuse to start if a required secret is empty, too short, a known default or contained in the known-development-secrets list, if the CORS configuration allows a wildcard, `http` or loopback origin, or if Swagger UI is enabled. Error messages SHALL name the offending setting and never its value.

Trace: REQ-183 AC2, REQ-183 AC3, REQ-188, ADR-036

#### Scenario: Development secret in production

- **WHEN** the application starts outside the development profile with a database password whose SHA-256 hash is in the known-development-secrets list
- **THEN** startup fails with a message naming the password property, without the secret value

#### Scenario: Unsafe CORS origin

- **WHEN** the application starts outside the development profile with `*` or an `http` origin in the allowed CORS origins
- **THEN** startup fails with a message naming the CORS property

#### Scenario: Swagger UI enabled

- **WHEN** the application starts outside the development profile with Swagger UI enabled
- **THEN** startup fails and Swagger UI is not reachable

### Requirement: Development profile only with loopback issuers

When the development profile is active, every configured identity provider issuer SHALL be a loopback address; otherwise the application SHALL refuse to start.

Trace: REQ-183 AC5

#### Scenario: Development profile with a real issuer

- **WHEN** the application starts with the development profile and an issuer that is not a loopback address (e.g. `https://login.example.com`)
- **THEN** startup fails with a message naming the issuer property

### Requirement: No test-only authentication code in the production artifact

The build SHALL fail if the production artifact contains test-only authentication classes, token stubs, static signing keys, test libraries for authentication, development configuration files or identity provider realm files.

Trace: REQ-183 AC1, ADR-036

#### Scenario: Stub class in main code

- **WHEN** a class whose name matches a stub pattern (e.g. `FakeJwtIssuer`) or a dependency on a test-only authentication library is added to the main code
- **THEN** the build fails

#### Scenario: Production artifact inspection

- **WHEN** the production artifact is inspected in CI
- **THEN** the check fails if it contains classes matching the forbidden patterns, banned libraries, `application-dev*` files or realm files

### Requirement: Known development secrets list complete

Every secret value used in the development files SHALL be contained, as SHA-256 hash, in the known-development-secrets list of the production artifact.

Trace: REQ-183 AC6

#### Scenario: New development secret without hash

- **WHEN** a new secret literal is added to `compose.dev.yaml`, `dev/` or the realm file without adding its hash to the list
- **THEN** the CI check fails and names the file containing the unlisted secret

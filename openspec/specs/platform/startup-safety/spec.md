# platform/startup-safety Specification

## Purpose

Defines the startup checks that keep unsafe configurations out of production and the guarantee that no test-only authentication code ships in the production artifact.

## Requirements

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

When the development profile is active, the issuer of every stored identity provider SHALL be a loopback address; otherwise the application SHALL refuse to start before it accepts connections.

Trace: REQ-183 AC5, ADR-036 §6

#### Scenario: Development profile with a real issuer

- **WHEN** the application starts with the development profile and a stored provider whose issuer is not a loopback address (e.g. `https://login.example.com`)
- **THEN** startup fails with a message naming the provider and its issuer

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

### Requirement: Safe stored providers outside development

Outside the development profile the application SHALL refuse to start, before it accepts connections, if an enabled stored provider has an issuer that does not use `https` or an issuer on a loopback address. Error messages SHALL name the provider and never a secret.

Trace: REQ-183 AC2, ADR-036 §6 G2, G3

#### Scenario: Stored provider with an unsafe issuer

- **WHEN** the application starts outside the development profile while the database contains an enabled provider with the issuer `http://idp.example.org` or `https://localhost/realms/x`
- **THEN** startup fails with a message naming the provider and the violated rule

#### Scenario: Disabled provider ignored

- **WHEN** the unsafe provider is disabled
- **THEN** the application starts and the provider is not offered for login

### Requirement: Signature verification not configurable

Signature verification of identity tokens SHALL always use the provider's published keys with asymmetric algorithms; no setting SHALL allow the algorithm `none`, symmetric algorithms or a key other than the provider's (G1). Startup SHALL not depend on the provider being reachable.

Trace: REQ-166 AC2, ADR-036 §6 G1

#### Scenario: Provider unreachable at startup

- **WHEN** the application starts while an enabled provider cannot be reached
- **THEN** the application starts, and logins through that provider fail with the technical error message until it is reachable

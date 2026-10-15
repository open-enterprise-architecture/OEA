# Spec Delta

## MODIFIED Requirements

### Requirement: Development profile only with loopback issuers

When the development profile is active, the issuer of every stored identity provider SHALL be a loopback address; otherwise the application SHALL refuse to start before it accepts connections.

Trace: REQ-183 AC5, ADR-036 §6

#### Scenario: Development profile with a real issuer

- **WHEN** the application starts with the development profile and a stored provider whose issuer is not a loopback address (e.g. `https://login.example.com`)
- **THEN** startup fails with a message naming the provider and its issuer

## ADDED Requirements

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

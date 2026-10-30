# Spec Delta

## MODIFIED Requirements

### Requirement: Overview of the own second factors

A principal with a local password SHALL list its active second factors – authenticator apps and passkeys – with type, label, `createdAt` and `lastUsedAt`, never with a secret or a public key.

Trace: US-029 AC3, REQ-030 AC3, US-025 AC3, REQ-026 AC3, US-022

#### Scenario: Two authenticators

- **WHEN** a person with two active TOTP credentials lists the own second factors
- **THEN** the answer contains both with their labels and times, and no secret

#### Scenario: Authenticator app and passkey

- **WHEN** a person with a TOTP credential and a passkey lists the own second factors
- **THEN** the answer contains both with their types `totp` and `passkey` and their labels

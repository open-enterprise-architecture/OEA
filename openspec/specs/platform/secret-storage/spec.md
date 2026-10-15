# platform/secret-storage Specification

## Purpose

Defines how OEA stores secrets such as TOTP secrets and OIDC client secrets: encrypted with a key-encryption key that is kept outside the database and can be rotated.

## Requirements

### Requirement: Secrets encrypted with an external key

Secrets stored by the application SHALL be encrypted with AES-256-GCM under a key-encryption key read from a file outside the database; each value SHALL record the identifier of the key that encrypted it, and no API SHALL return a stored secret.

Trace: REQ-188 AC1, REQ-188 AC3

#### Scenario: Database dump

- **WHEN** a database dump is searched for a stored TOTP secret or OIDC client secret
- **THEN** neither is found in plaintext, Base32 or Base64

### Requirement: Key rotation

With an old and a new key configured, a re-encryption command SHALL re-encrypt every stored secret with the new key without data loss.

Trace: REQ-188 AC2

#### Scenario: Rotation

- **WHEN** a new key is configured as current and the re-encryption command runs
- **THEN** all secrets carry the new key identifier and a login with an existing TOTP credential still succeeds

### Requirement: Key checked at startup

Outside the development profile the application SHALL refuse to start if the key file is missing, unreadable or shorter than 256 bit, or if the key is a known development key.

Trace: REQ-188, REQ-183 AC2, ADR-036 §6 (G4)

#### Scenario: Missing key

- **WHEN** the application starts outside the development profile without a readable key file of 256 bit
- **THEN** startup fails naming the key property, without the key value

# Delta for platform/audit-value-encryption

## Purpose

Defines how the audit trail stores the old and new values of restricted properties: encrypted with an audit value key that is wrapped by the key-encryption key, and how these keys survive a rotation of the key-encryption key.

## ADDED Requirements

### Requirement: Restricted values encrypted in the audit trail

A change of a property whose visibility mode is not `public` at the time of the event SHALL store its old and new value only as AES-256-GCM ciphertext with the ID of the audit value key, never as plaintext. Changes of public properties and system fields SHALL stay plaintext.

Trace: REQ-160 AC4, ADR-033 §3

#### Scenario: Restricted value written

- **GIVEN** `investmentCost` is `role-restricted`
- **WHEN** an enterprise architect with a write role changes it from `100` to `120`
- **THEN** the audit change has `value_protection` `encrypted`, a key ID, no plaintext value, and its ciphertext decrypts to `100` and `120`

#### Scenario: Public value written

- **WHEN** the same entity's public property `Vendor` changes
- **THEN** its audit change is stored as plaintext

#### Scenario: Chain stays verifiable

- **WHEN** `oea audit verify` runs after encrypted changes were written
- **THEN** the hash chain is intact

### Requirement: Audit value keys

The audit value key SHALL be a random data key, stored only wrapped by the key-encryption key (REQ-188), never in plaintext in the database. A rotation of the key-encryption key with `oea secrets-reencrypt` SHALL re-wrap every audit value key, so that encrypted values stay readable after the old key-encryption key is removed.

Trace: REQ-188, ADR-033 §3

#### Scenario: Rotation of the key-encryption key

- **GIVEN** audit values encrypted under an audit value key wrapped by key A
- **WHEN** key B becomes current, `oea secrets-reencrypt` runs and key A is removed
- **THEN** the audit value key is wrapped by key B and the values still decrypt

# Spec Delta

## Purpose

Defines how administrators grant identities of external identity providers access to OEA: OIDC providers, persons with immutable identity bindings, and IdP group mappings to authorization roles – and that the first such access requires the second factor of the system admin.

## ADDED Requirements

### Requirement: OIDC providers

A principal with `auth-config:manage` SHALL be able to create, read, update and deactivate OIDC providers. Outside the development profile the issuer SHALL use `https` and SHALL NOT be a loopback address; client secrets SHALL be write-only. Changes SHALL require an authentication within the last 5 minutes and SHALL be audited.

Trace: ADR-036 §6 (G2, G3), REQ-172, REQ-188 AC3, REQ-175

#### Scenario: Provider with an unsafe issuer

- **WHEN** a provider with an `http` issuer or an issuer on `localhost` is saved outside the development profile
- **THEN** it is rejected with 422 naming the issuer

#### Scenario: Client secret never returned

- **WHEN** a provider with a client secret is saved and read again
- **THEN** the response indicates that a client secret is set but never contains it, and an audit event `auth-provider.changed` exists

### Requirement: Persons and identity bindings

A principal with `person:manage` SHALL be able to create persons and bind a person to the immutable pair of issuer and subject of a configured provider; a pair SHALL be bound to at most one person, and bindings SHALL never be created from an e-mail address.

Trace: REQ-004, REQ-166, ADR-032 DM-4

#### Scenario: Binding created

- **WHEN** an administrator creates a person and binds it to the subject of a user of a configured provider
- **THEN** the person exists in status `invited`, the binding stores provider, issuer, subject and the method `admin-entered`, and audit events `person.created` and `person.identity-bound` exist

#### Scenario: Pair already bound

- **WHEN** the same issuer and subject are bound to a second person
- **THEN** the request is rejected with 409 and the first binding is unchanged

### Requirement: IdP group mappings

A principal with `role:assign` SHALL be able to map an immutable group identifier of a configured provider to an authorization role and to deactivate or delete the mapping; identifiers that do not match the provider's identifier format, such as display names, SHALL be rejected. Every change SHALL be audited.

Trace: US-147 AC1, US-147 AC4, US-147 AC7, REQ-167

#### Scenario: Mapping created

- **WHEN** an administrator maps the group identifier of a configured provider to the role `viewer`
- **THEN** the mapping is stored with issuer, group identifier, role, creator and creation time, and an audit event `idp-mapping.created` exists

#### Scenario: Display name instead of identifier

- **WHEN** an administrator enters a display name such as "EA-Viewers" as group identifier for a provider whose identifiers are UUIDs
- **THEN** the request is rejected with 422

#### Scenario: Mapping deleted

- **WHEN** an administrator deletes a mapping
- **THEN** it no longer applies and an audit event `idp-mapping.deleted` exists

### Requirement: Second factor before the first foreign access

The first identity binding or IdP group mapping of an instance SHALL be accepted only if the acting system admin has an enrolled second factor and the session is second-factor-verified; with this action the setup phase SHALL end irreversibly.

Trace: REQ-171 AC1, ADR-042

#### Scenario: First mapping without second factor

- **WHEN** the system admin creates the first IdP group mapping in a session that is not second-factor-verified
- **THEN** the request is rejected with 403 and problem type `second-factor-required`, nothing is stored, and the setup phase continues

#### Scenario: First mapping with second factor

- **WHEN** the system admin repeats the request after enrolling and confirming a TOTP credential in the session
- **THEN** the mapping is stored, the setup phase has ended, and an audit event `system.setup-phase-ended` naming the triggering action exists

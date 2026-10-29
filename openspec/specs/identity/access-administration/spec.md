# identity/access-administration Specification

## Purpose

Defines how administrators grant identities of external identity providers access to OEA: OIDC providers, persons with immutable identity bindings, and IdP group mappings to authorization roles – and that the first such access requires the second factor of the system admin.

## Requirements

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

### Requirement: No mapping of own groups

A person SHALL not create or change a mapping for a group that is contained in the groups claim of the person's own current session.

Trace: US-147 AC5, ADR-032 SoD-1

#### Scenario: Own group rejected

- **WHEN** a person holding `role:assign` creates or changes a mapping for a group contained in the groups claim of the person's own session
- **THEN** the request is rejected with 403 and reason `self-administration`, nothing is stored, and an audit event `authz.denied` exists

### Requirement: Second factor before the first foreign access

The first identity binding or IdP group mapping of an instance SHALL be accepted only if the acting system admin has an enrolled second factor and the session is second-factor-verified; with this action the setup phase SHALL end irreversibly.

Trace: REQ-171 AC1, ADR-042

#### Scenario: First mapping without second factor

- **WHEN** the system admin creates the first IdP group mapping in a session that is not second-factor-verified
- **THEN** the request is rejected with 403 and problem type `second-factor-required`, nothing is stored, and the setup phase continues

#### Scenario: First mapping with second factor

- **WHEN** the system admin repeats the request after enrolling and confirming a TOTP credential in the session
- **THEN** the mapping is stored, the setup phase has ended, and an audit event `system.setup-phase-ended` naming the triggering action exists

### Requirement: Last match of IdP group mappings

Every IdP group mapping SHALL show when it last granted its role effectively in a login or re-evaluation; a mapping that never did SHALL show no time.

Trace: US-018 AC1, REQ-018

#### Scenario: Mapping that has granted its role

- **WHEN** a member of a mapped group logs in through the mapping's provider and receives the role effectively, and an administrator lists the mappings
- **THEN** the mapping shows the time of that login as its last match

#### Scenario: Mapping that has never matched

- **WHEN** an administrator lists a mapping whose group has not been presented in any login since the mapping was created
- **THEN** the mapping shows no last match

### Requirement: Warning for a system admin mapping without evidence of members

Creating or changing a mapping to the role `system-admin` SHALL store the mapping and SHALL return a warning that the mapping currently grants access to nobody known, if the mapping has never matched and no current session of the mapping's provider carries its group identifier. The warning SHALL NOT block the change.

Trace: US-018 AC1, US-018 AC2, REQ-018

#### Scenario: Mapping to a group never seen

- **WHEN** an administrator maps a group identifier that no login at this instance has presented to the role `system-admin`
- **THEN** the mapping is stored and the response carries the warning `group-not-seen`

#### Scenario: Mapping to a group with a current member session

- **WHEN** an administrator maps a group identifier to `system-admin` that is contained in the groups claim of a current session of the same provider
- **THEN** the mapping is stored and the response carries no warning

### Requirement: Deactivation of the local system admin account

A principal with `auth-config:manage` SHALL be able to read the state of the local system admin account and to deactivate it. Deactivation SHALL revoke the account's sessions and end its password login, while IdP logins stay unaffected. Both outcomes SHALL be audited as `system-admin-account.deactivated`.

Trace: US-019 AC1, REQ-019

#### Scenario: Deactivation with a working remote mapping

- **WHEN** a mapping to `system-admin` has matched in a login and an administrator deactivates the local system admin account
- **THEN** the account has the status `deactivated`, its sessions are revoked, a login with its password fails like a wrong password, a member of the mapped group can still log in with `system-admin`, and an audit event `system-admin-account.deactivated` with outcome `success` exists

#### Scenario: Deactivation without the permission

- **WHEN** a person without `auth-config:manage` deactivates the local system admin account
- **THEN** the request is rejected with 403 and the account stays usable

### Requirement: The last path to system administration is kept

A change SHALL be rejected with 409 and problem type `last-admin-path` if no usable path to `system-admin` would remain afterwards. This applies to revoking a role assignment, changing the status of a person, deactivating the local system admin account, and deleting, deactivating or retargeting an IdP group mapping. Concurrent changes SHALL NOT both pass the check.

Trace: US-019 AC2, US-146 AC1, US-146 AC4, US-146 AC5, REQ-019, ADR-032 SoD-7

#### Scenario: Deactivation without another path

- **WHEN** the local system admin account is the only path to `system-admin`, because there is no mapping to `system-admin` or no such mapping has matched yet and no person holds `system-admin`, and an administrator deactivates it
- **THEN** the request is rejected with 409 and the problem type `last-admin-path`, the account stays usable, and an audit event `system-admin-account.deactivated` with outcome `denied` exists

#### Scenario: Revoking the last holder

- **WHEN** a person is the only path to `system-admin`, because the local account is deactivated and no mapping to `system-admin` is usable, and an administrator revokes her assignment of `system-admin`
- **THEN** the request is rejected with 409 and problem type `last-admin-path`, and the assignment stays granted

#### Scenario: Another path exists

- **WHEN** two active persons hold `system-admin` and an administrator revokes the assignment of one of them
- **THEN** the revocation succeeds

#### Scenario: Deleting the last usable mapping

- **WHEN** a matched mapping to `system-admin` is the only path because the local account is deactivated and no person holds `system-admin`, and an administrator deletes or deactivates the mapping or changes its role
- **THEN** the request is rejected with 409 and problem type `last-admin-path`, and the mapping is unchanged

#### Scenario: Parallel revocations

- **WHEN** two persons are the only paths to `system-admin` and their assignments are revoked by two concurrent requests
- **THEN** exactly one revocation succeeds and the other is rejected with 409

#### Scenario: Status change of the last holder

- **WHEN** a person is the only path to `system-admin` and an administrator sets her to `inactive` or `offboarded`
- **THEN** the request is rejected with 409 and problem type `last-admin-path`, and the person's status is unchanged

### Requirement: Usable paths to system administration

A usable path to `system-admin` SHALL be one of: the local system admin account if it is not deactivated; an active, approved, not deleted IdP group mapping to `system-admin` of an enabled provider that has matched at least once; a granted, currently valid assignment of `system-admin` without `validTo` to an active person.

Trace: US-019 AC2, US-146 AC3, ADR-032 SoD-7

#### Scenario: Local account with a person as the other path

- **WHEN** an active person holds an open-ended `system-admin` assignment, no mapping exists, and an administrator deactivates the local system admin account
- **THEN** the deactivation succeeds

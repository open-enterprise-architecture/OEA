# Spec Delta

## ADDED Requirements

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

Deactivating the local system admin account SHALL be rejected with 409 if no other usable path to `system-admin` remains. A usable path is an active, approved, not deleted IdP group mapping to `system-admin` of an enabled provider that has matched at least once.

Trace: US-019 AC2, REQ-019, ADR-032 SoD-7

#### Scenario: Deactivation without another path

- **WHEN** the local system admin account is the only path to `system-admin`, because there is no mapping to `system-admin` or no such mapping has matched yet, and an administrator deactivates it
- **THEN** the request is rejected with 409 and the problem type `last-admin-path`, the account stays usable, and an audit event `system-admin-account.deactivated` with outcome `denied` exists

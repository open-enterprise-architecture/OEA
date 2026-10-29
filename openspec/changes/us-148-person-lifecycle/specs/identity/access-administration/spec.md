# Spec Delta

## MODIFIED Requirements

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

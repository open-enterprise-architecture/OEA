# Spec Delta

## MODIFIED Requirements

### Requirement: The last path to system administration is kept

A change SHALL be rejected with 409 and problem type `last-admin-path` if no usable path to `system-admin` would remain afterwards. This applies to revoking a role assignment, deactivating the local system admin account, and deleting, deactivating or retargeting an IdP group mapping. Usable paths are:

- the local system admin account if it is not deactivated;
- an active, approved, not deleted IdP group mapping to `system-admin` of an enabled provider that has matched at least once;
- a granted, currently valid assignment of `system-admin` without `validTo` to an active person.

Concurrent changes SHALL NOT both pass the check.

Trace: US-019 AC2, US-146 AC1, US-146 AC3, US-146 AC4, REQ-019, ADR-032 SoD-7

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

#### Scenario: Local account with a person as the other path

- **WHEN** an active person holds an open-ended `system-admin` assignment, no mapping exists, and an administrator deactivates the local system admin account
- **THEN** the deactivation succeeds

#### Scenario: Parallel revocations

- **WHEN** two persons are the only paths to `system-admin` and their assignments are revoked by two concurrent requests
- **THEN** exactly one revocation succeeds and the other is rejected with 409

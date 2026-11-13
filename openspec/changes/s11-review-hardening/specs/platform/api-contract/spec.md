## ADDED Requirements

### Requirement: Implementation status of the security schemes

Every security scheme of the contract that the release does not accept SHALL be marked as planned, and every scheme the release accepts SHALL NOT be marked; an instance whose accepted schemes and planned markers disagree SHALL NOT start. A request that presents only the credentials of a planned scheme SHALL be answered like an unauthenticated request.

Trace: REQ-158, REQ-176; API-1 of the architecture review 2026-11

#### Scenario: Markers match the accepted schemes

- **WHEN** the instance starts with a contract in which only the session cookie scheme is unmarked and the bearer, client-credentials and basic schemes are marked as planned
- **THEN** the instance starts

#### Scenario: Accepted scheme marked as planned

- **WHEN** the instance starts with a contract that marks the session cookie scheme as planned
- **THEN** the instance does not start and names the scheme

#### Scenario: Planned scheme without marker

- **WHEN** the instance starts with a contract in which the bearer scheme is not marked as planned
- **THEN** the instance does not start and names the scheme

#### Scenario: Credentials of a planned scheme

- **WHEN** a request to an operation that requires authentication carries only `Authorization: Bearer <token>` and no session cookie
- **THEN** it is answered with 401 like an unauthenticated request

#### Scenario: Contract check in CI

- **WHEN** a security scheme carries an `x-oea-status` other than `planned`
- **THEN** the contract check fails and names the scheme

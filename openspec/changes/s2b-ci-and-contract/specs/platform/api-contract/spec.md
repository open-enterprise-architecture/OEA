# Spec Delta

## Purpose

Defines how the published API contract states the implementation status of its operations and how the instance answers operations that the contract describes but the release does not implement yet.

## ADDED Requirements

### Requirement: Implementation status in the contract

Every operation of the contract that the release does not implement SHALL be marked as planned, and every operation the release implements SHALL NOT be marked; an instance whose implemented operations and planned markers disagree SHALL NOT start.

Trace: REQ-158, retrospective A1

#### Scenario: Planned marker matches the implementation

- **WHEN** the instance starts with a contract in which every unimplemented operation is marked as planned and no implemented operation is
- **THEN** the instance starts

#### Scenario: Implemented operation still marked as planned

- **WHEN** the instance starts with a contract that marks an implemented operation as planned
- **THEN** the instance does not start and names the operation

#### Scenario: Unimplemented operation without planned marker

- **WHEN** the instance starts with a contract in which an unimplemented operation is not marked as planned
- **THEN** the instance does not start and names the operation

### Requirement: Answer of planned operations

A planned operation SHALL be authenticated and authorized like an implemented one, and a request that passes both SHALL be answered with 501 and the problem type `urn:oea:problem:not-implemented` without changing any state.

Trace: REQ-187, REQ-158, retrospective A1

#### Scenario: Authorized call of a planned operation

- **WHEN** a principal with the permission declared by a planned operation calls it
- **THEN** the response is 501 with the problem type `urn:oea:problem:not-implemented`

#### Scenario: Planned operation without session

- **WHEN** a caller without session calls a planned operation that is not public
- **THEN** the response is 401

#### Scenario: Planned operation without permission

- **WHEN** a principal without the permission declared by a planned operation calls it
- **THEN** the response is 403

# platform/request-limits Specification

## Purpose

Defines the limits that protect the instance against oversized or excessive requests, so that one principal cannot exhaust memory or database capacity for all users.

## Requirements

### Requirement: Size limit of request bodies

Every request body SHALL be limited before the request is processed: a body whose declared length exceeds the limit SHALL be rejected without being read, and a body without declared length SHALL be rejected as soon as it exceeds the limit. A rejected request SHALL be answered with 413 and the problem type `urn:oea:problem:request-too-large` and SHALL NOT change any state.

Trace: REQ-186, security re-review WS-02

#### Scenario: Oversized batch with declared length

- **WHEN** an enterprise architect sends an entity batch whose declared length is above 10 MB
- **THEN** the response is 413 with the problem type `urn:oea:problem:request-too-large` and no entity is written

#### Scenario: Oversized body without declared length

- **WHEN** a principal streams a catalog creation request without declared length that grows beyond 1 MB
- **THEN** the response is 413 with the problem type `urn:oea:problem:request-too-large` and no catalog is created

#### Scenario: Body within the limit

- **WHEN** an enterprise architect sends an entity batch of 500 entities below 10 MB
- **THEN** the batch is processed as before

### Requirement: Configurable limits

The limit SHALL be 1 MB by default and 10 MB for the entity batch, and each limit SHALL be configurable by the operator; the package import keeps its own limit and the problem type `urn:oea:problem:package-too-large`.

Trace: REQ-186, REQ-184

#### Scenario: Limit configured by the operator

- **WHEN** the operator lowers the default limit to 100 KB and a principal sends a 200 KB request body
- **THEN** the response is 413 with the problem type `urn:oea:problem:request-too-large`

#### Scenario: Oversized package

- **WHEN** a metamodel administrator uploads a package above the package limit
- **THEN** the response is 413 with the problem type `urn:oea:problem:package-too-large`

### Requirement: Authentication before the size check

The size check SHALL NOT disclose anything to unauthenticated callers: a request without a valid session or token SHALL be answered with 401 before the body size is evaluated.

Trace: REQ-186, REQ-187

#### Scenario: Oversized body without session

- **WHEN** a caller without session sends an entity batch above 10 MB
- **THEN** the response is 401

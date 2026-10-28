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

### Requirement: Per-principal rate limit

Every request of an authenticated principal SHALL count against a per-principal limit of 600 requests per minute. A request above the limit SHALL be answered with 429, the problem type `urn:oea:problem:too-many-requests` and a `Retry-After` header before its body is read, and SHALL NOT change any state. The limit of one principal SHALL NOT affect other principals.

Trace: REQ-186 AC1, security re-review WS-02, issue #74

#### Scenario: Principal above the limit

- **WHEN** an enterprise architect sends a 601st catalog query within one minute
- **THEN** the response is 429 with the problem type `urn:oea:problem:too-many-requests` and a `Retry-After` header with the seconds until the next request is accepted

#### Scenario: Other principals unaffected

- **WHEN** one enterprise architect is above the limit and a viewer queries a catalog
- **THEN** the viewer's query is answered with 200

#### Scenario: Requests accepted again after the waiting time

- **WHEN** a principal that was answered with 429 sends a request after the `Retry-After` seconds have elapsed
- **THEN** the request is processed

### Requirement: Rate limit of batch writes

Every entity batch request of a principal SHALL additionally count against a limit of 10 batch requests per minute; a batch request above this limit SHALL be answered like a request above the per-principal limit, without processing any item.

Trace: REQ-186 (batch write requests), issue #74

#### Scenario: Eleventh batch within a minute

- **WHEN** an enterprise architect sends an 11th entity batch within one minute
- **THEN** the response is 429 with the problem type `urn:oea:problem:too-many-requests` and `Retry-After`, and no entity of that batch is written

### Requirement: Configurable rate limits

The per-principal limit and the batch limit SHALL be configurable by the operator as requests per minute; each limit SHALL be at least 1.

Trace: REQ-186

#### Scenario: Limit lowered by the operator

- **WHEN** the operator sets the per-principal limit to 5 requests per minute and a principal sends a 6th request within one minute
- **THEN** the response is 429 with `Retry-After`

### Requirement: Reaching a rate limit is audited

When a principal reaches a rate limit, the audit trail SHALL receive one event `auth.throttled` with scope `principal`, the limit and the principal, at most once per principal, limit and minute; the rejected requests themselves SHALL NOT be audited individually.

Trace: REQ-175, REQ-186, issue #74

#### Scenario: Throttled principal in the audit trail

- **WHEN** an enterprise architect sends 650 requests within one minute
- **THEN** the audit trail contains exactly one event `auth.throttled` with scope `principal` for that principal and minute

### Requirement: Rate limit declared in the contract

Every implemented operation that requires authentication SHALL declare the 429 response in the contract.

Trace: REQ-186, ADR-013

#### Scenario: Contract check

- **WHEN** the contract check runs in CI
- **THEN** it fails if an implemented operation that requires authentication does not declare the 429 response

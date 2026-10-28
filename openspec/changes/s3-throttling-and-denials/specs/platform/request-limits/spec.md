# Spec Delta

## ADDED Requirements

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

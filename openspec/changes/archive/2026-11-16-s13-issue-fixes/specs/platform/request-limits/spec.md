## ADDED Requirements

### Requirement: Limits apply to the decoded path

The rate limit and the size limit of batch writes SHALL apply to every request whose decoded and normalized path is the batch path, whatever its percent-encoding. A request whose path decodes to another operation SHALL count under the limits of that operation.

Trace: REQ-186, security audit 2026-11 T-1, issue #263

#### Scenario: Percent-encoded batch path

- **WHEN** a principal sends batch writes to `/api/v1/entities/b%61tch` above the batch rate limit
- **THEN** the request above the limit is answered with 429, as for `/api/v1/entities/batch`

#### Scenario: Percent-encoded body above the batch size

- **WHEN** a batch write above the batch size limit is sent to `/api/v1/entities/b%61tch`
- **THEN** it is answered with 413, as for `/api/v1/entities/batch`

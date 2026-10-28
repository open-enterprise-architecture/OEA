# platform/authorization Specification

## Purpose

Defines how OEA enforces authentication and function-level authorization: deny by default, permissions declared per operation in the API contract, and the fixed permission catalog with the built-in roles.

## Requirements

### Requirement: Authentication required by default

Every operation SHALL require an authenticated principal except the public allowlist of the contract (health, version check, setup, login); there SHALL be no configuration that enables anonymous access.

Trace: REQ-163, ADR-032 §8 L1

#### Scenario: Request without session

- **WHEN** a non-public operation is called without a session
- **THEN** it is rejected with 401

### Requirement: Permissions declared by the contract

Each operation SHALL require the permissions declared in its `x-oea-authorization` before the controller runs; requests to operations without a declaration and to undeclared paths SHALL be rejected for every principal; rejections SHALL use a generic problem body.

Trace: REQ-158, ADR-032 §8 L2

#### Scenario: Principal without the permission

- **WHEN** a principal calls an implemented operation without holding one of its declared permissions
- **THEN** the response is 403 with a generic problem body that does not name the missing permission

#### Scenario: Every implemented operation is covered

- **WHEN** the generated authorization matrix test calls every implemented operation with a principal lacking its permissions
- **THEN** every call is rejected with 403 or, without a session, 401

### Requirement: Permission catalog and built-in roles

Every instance SHALL contain the fixed permission catalog and the built-in authorization roles of ADR-032 with their permission sets, readable by principals holding `role:assign`.

Trace: US-143 AC1, US-143 AC2, US-143 AC4, REQ-157

#### Scenario: Catalog and roles of a fresh instance

- **WHEN** the system admin reads the permissions and the roles of a freshly installed instance
- **THEN** the 20 permissions with their `adminClass` flag and the nine built-in roles with their permission sets are returned exactly as in the contract

#### Scenario: Reading without role:assign

- **WHEN** a principal without `role:assign` reads the permissions or the roles
- **THEN** the response is 403

### Requirement: Permission denials are audited

Every request refused with 403 by an authorization decision SHALL write an audit event `authz.denied` with the principal, the operation ID, the target ID if the operation has one, the source address and the request ID. Authorization decisions are the permissions declared in the contract, permission checks of contract conditions, the second-factor requirement of the setup phase and separation-of-duties rules.

Trace: REQ-175 AC1, security re-review WS-01, issue #75

#### Scenario: Viewer calls an administrative operation

- **WHEN** a viewer calls the operation that lists the IdP group mappings and is answered with 403
- **THEN** the audit trail contains an event `authz.denied` with the viewer as actor, the operation ID, the source address and the request ID

#### Scenario: Denial inside a contract condition

- **WHEN** a viewer (without `catalog:manage`) tries to create an instance catalog and is answered with 403
- **THEN** the audit trail contains an event `authz.denied` with the operation ID `createCatalog` and the reason `condition`

#### Scenario: Denial with a target

- **WHEN** a principal is refused with 403 by an operation whose path contains an ID
- **THEN** the event `authz.denied` carries that ID as target ID

#### Scenario: Every implemented operation audits its denial

- **WHEN** the generated authorization matrix test calls every implemented operation with a principal lacking its permissions
- **THEN** every call answered with 403 has written an event `authz.denied` with the operation ID of that call

### Requirement: Flood suppression of permission denials

Per principal, source address and operation, the first 20 denials within one minute SHALL be recorded individually; further denials within that minute SHALL be recorded as one summary event `authz.denied` carrying their count, written after the minute ends. The 403 answers themselves SHALL NOT change.

Trace: REQ-175 AC5, issue #75

#### Scenario: Fifty denials within one minute

- **WHEN** a principal triggers 50 requests rejected with 403 on the same operation within one minute
- **THEN** the audit trail contains 20 individual events `authz.denied` and one summary event `authz.denied` with the count 30 for that minute

#### Scenario: Different operations are counted separately

- **WHEN** a principal triggers 15 denials on one operation and 15 on another within one minute
- **THEN** the audit trail contains 30 individual events `authz.denied` and no summary event

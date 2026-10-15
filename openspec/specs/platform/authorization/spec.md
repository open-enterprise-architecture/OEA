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

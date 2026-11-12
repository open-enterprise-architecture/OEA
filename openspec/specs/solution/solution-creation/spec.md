# solution/solution-creation Specification

## Purpose

Defines how a solution architect creates a solution as the container of a change initiative: the permission and role for it, the name and architecture vision and their validation, the created solution in project mode, and its audit.

## Requirements

### Requirement: Create a solution in project mode

A person with `solution:write` SHALL create a solution with a name and an optional architecture vision (`description`). The solution SHALL be stored with status `draft`, the caller as owner and creator, the creation time, and `fromPlateauId` and `toPlateauId` null. The response SHALL be 201 with the solution. The name SHALL be stored trimmed. A creation SHALL be audited as `solution.created` with the name.

Trace: US-038 AC1, REQ-038 AC1, UC-05

#### Scenario: Solution created

- **GIVEN** Michael holds `solution-architect`
- **WHEN** he creates the solution "ERP Extension Logistics" with an architecture vision
- **THEN** the response is 201, the solution has status `draft`, Michael as owner, both plateau references null, it is listed in the solution overview, and `solution.created` is audited

#### Scenario: Name stored trimmed

- **WHEN** Michael creates the solution "  CRM Replacement  "
- **THEN** the solution is named "CRM Replacement"

### Requirement: Unique solution name

The name SHALL be unique within the instance, compared case-insensitively after trimming. A taken name SHALL be rejected with 422 naming `name` and the detail "Name '{name}' is already taken"; nothing SHALL be stored or audited.

Trace: US-038 AC2, REQ-038 AC4

#### Scenario: Name taken

- **GIVEN** the solution "ERP Extension Logistics" exists
- **WHEN** Michael creates a solution named "erp extension logistics "
- **THEN** the response is 422 naming `name` with the detail "Name 'erp extension logistics' is already taken", and no second solution exists

### Requirement: Valid name and architecture vision

The name SHALL have 1 to 255 characters and SHALL NOT be blank; the architecture vision SHALL have at most 2000 characters. A longer name or vision SHALL be rejected with 400, a blank name with 422 naming `name`; nothing SHALL be stored.

Trace: REQ-038 Inputs, business object solution

#### Scenario: Limits of the name

- **WHEN** Michael creates a solution with a name of 255 characters, and another with a name of 256 characters
- **THEN** the first is created and the second is rejected with 400

#### Scenario: Limits of the architecture vision

- **WHEN** Michael creates a solution with a vision of 2000 characters, and another with a vision of 2001 characters
- **THEN** the first is created and the second is rejected with 400

#### Scenario: Blank name

- **WHEN** Michael creates a solution named "   "
- **THEN** the response is 422 naming `name`

### Requirement: Creating solutions needs solution:write

Creating a solution SHALL require the permission `solution:write`. A caller without it, and an account that is not a person, SHALL get 403; nothing SHALL be stored.

Trace: US-038 AC5, REQ-038 AC5, REQ-157

#### Scenario: Without permission

- **GIVEN** Anna holds only `viewer`
- **WHEN** she creates a solution
- **THEN** the response is 403 and no solution is stored

#### Scenario: Local system administrator account

- **WHEN** the local system administrator account creates a solution
- **THEN** the response is 403

### Requirement: Permission and role for solution architects

The permission `solution:write` SHALL NOT be admin-class. The built-in role `enterprise-architect` SHALL hold it. The built-in role `solution-architect` SHALL hold `portal:read`, `entity:read`, `solution:write` and `solution-metamodel:write`, SHALL NOT be privileged and SHALL NOT be assignable to service accounts.

Trace: US-038 AC1, REQ-157, ADR-032 amendment 2026-11-12

#### Scenario: Built-in roles

- **WHEN** the authorization roles are listed
- **THEN** `solution-architect` lists `portal:read`, `entity:read`, `solution:write` and `solution-metamodel:write` and is not privileged, and `enterprise-architect` lists `solution:write`

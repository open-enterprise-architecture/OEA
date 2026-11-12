# Delta for solution/solution-deltas

## Purpose

Defines the entity deltas of a solution in project mode: the new entities an initiative introduces and the existing entities it retires. Covers how deltas are captured, read and removed, their checks, limits and audit, and how they appear on the page "Solution".

## ADDED Requirements

### Requirement: Capture a new entity

A principal with `solution:write` SHALL capture a delta `new` in a solution with an element metatype, a name, an optional description and property values. The delta SHALL NOT create an entity and SHALL NOT appear in the baseline.

Trace: US-040 AC1, REQ-040 AC2, owner decision 2026-11-12

#### Scenario: New entity

- **GIVEN** a solution in status `draft`
- **WHEN** a solution architect captures a delta `new` of the metatype Application with the name `Salesforce`
- **THEN** the response is 201 with the delta `new` named `Salesforce`
- **AND** the delta list of the solution shows it
- **AND** the baseline does not list `Salesforce`

#### Scenario: New entity with property values

- **GIVEN** the metatype Application has the mandatory property "Owner" (string) and the property "Users" (integer)
- **WHEN** a delta `new` with "Owner" `Sales` and "Users" `250` is captured
- **THEN** the delta holds both values

### Requirement: Checks of a new entity

A delta `new` SHALL be checked like the write of a new entity: name of 1–255 characters and not blank, description of at most 2000 characters, an existing element metatype, values matching the data type and constraints of their property, and a value for every mandatory property. A failed check SHALL answer 422 `validation-failed` naming the invalid fields and SHALL store nothing.

Trace: US-040 AC4, REQ-040

#### Scenario: Unknown metatype

- **WHEN** a delta `new` with a metatype ID that does not exist is captured
- **THEN** the response is 422 naming `metatypeId`
- **AND** no delta is stored

#### Scenario: Connection metatype

- **WHEN** a delta `new` with the connection metatype "Data flow" is captured
- **THEN** the response is 422 naming `metatypeId`

#### Scenario: Invalid property value

- **WHEN** a delta `new` with the value `many` for the integer property "Users" is captured
- **THEN** the response is 422 naming the value of that property
- **AND** no delta is stored

#### Scenario: Mandatory property missing

- **WHEN** a delta `new` of the metatype Application without a value for "Owner" is captured
- **THEN** the response is 422 naming `properties`

#### Scenario: Length of the name

- **WHEN** a delta `new` with a name of 255 characters is captured
- **THEN** the response is 201
- **WHEN** a delta `new` with a name of 256 characters or a blank name is captured
- **THEN** the response is 422 naming `name`

#### Scenario: Length of the description

- **WHEN** a delta `new` with a description of 2000 characters is captured
- **THEN** the response is 201
- **WHEN** a delta `new` with a description of 2001 characters is captured
- **THEN** the response is 422 naming `description`

### Requirement: Visibility of the values of a new entity

A value of a `role-restricted` property in a delta `new` SHALL need a write role of the property; without one, the request SHALL answer 403 and store nothing. A value of a `connection-scoped` property SHALL answer 403, because the entity of a delta `new` does not exist yet. When the deltas are read, a value the reader may not read SHALL be returned as redacted, without its value.

Trace: US-040 AC1, REQ-040, REQ-124, REQ-160, owner decision 2026-11-12

#### Scenario: Restricted property with a write role

- **GIVEN** the property "Cost" is `role-restricted` and writable by the role `finance`
- **WHEN** a solution architect with the role `finance` captures a delta `new` with a value of "Cost"
- **THEN** the response is 201

#### Scenario: Restricted property without a write role

- **GIVEN** the property "Cost" is `role-restricted` and writable by the role `finance`
- **WHEN** a solution architect without that role captures a delta `new` with a value of "Cost"
- **THEN** the response is 403
- **AND** no delta is stored

#### Scenario: Connection-scoped property

- **GIVEN** the property "Salary band" is `connection-scoped`
- **WHEN** a delta `new` with a value of "Salary band" is captured
- **THEN** the response is 403

#### Scenario: Restricted value redacted on read

- **GIVEN** a delta `new` holds a value of the `role-restricted` property "Cost"
- **WHEN** a principal without a read role of "Cost" reads the deltas
- **THEN** "Cost" is marked as redacted and carries no value
- **AND** a principal with a read role of "Cost" reads the value

### Requirement: Retire an entity of the baseline

A principal with `solution:write` SHALL capture a delta `retiring` for an entity of the baseline, given by its ID. An ID that is not an active entity of an element metatype SHALL answer 422 "Entity not found in the current landscape", naming `entityId`, and SHALL store nothing.

Trace: US-042 AC1, AC2, REQ-040 AC1

#### Scenario: Retire an entity

- **GIVEN** `CRM-Legacy` is in the baseline
- **WHEN** a solution architect captures a delta `retiring` for `CRM-Legacy`
- **THEN** the response is 201
- **AND** the delta list shows `CRM-Legacy` as `retiring`

#### Scenario: Entity not in the baseline

- **WHEN** a delta `retiring` is captured for an entity ID that does not exist, for a deleted entity, or for a data flow
- **THEN** the response is 422 "Entity not found in the current landscape" naming `entityId`
- **AND** no delta is stored

### Requirement: One delta per entity

A solution SHALL hold at most one delta per entity. A second delta `retiring` for the same entity, and a second delta `new` with the same metatype and the same name (case-insensitive), SHALL answer 422 "A delta already exists for this entity in this solution" and SHALL store nothing. Another solution SHALL NOT be affected.

Trace: US-040 AC3, US-042 AC3, REQ-040 AC5

#### Scenario: Second new entity with the same name

- **GIVEN** the solution holds the delta `new` Application `Salesforce`
- **WHEN** a delta `new` Application `salesforce` is captured in the same solution
- **THEN** the response is 422 "A delta already exists for this entity in this solution"

#### Scenario: Same name, other metatype

- **GIVEN** the solution holds the delta `new` Application `Salesforce`
- **WHEN** a delta `new` Component `Salesforce` is captured
- **THEN** the response is 201

#### Scenario: Entity retired twice

- **GIVEN** the solution holds a delta `retiring` for `CRM-Legacy`
- **WHEN** another delta `retiring` for `CRM-Legacy` is captured in the same solution
- **THEN** the response is 422 "A delta already exists for this entity in this solution"

#### Scenario: Other solution

- **GIVEN** the solution "CRM Replacement" holds a delta `retiring` for `CRM-Legacy`
- **WHEN** a delta `retiring` for `CRM-Legacy` is captured in the solution "Sales Portal"
- **THEN** the response is 201

### Requirement: Limit of deltas per solution

A solution SHALL hold at most 1000 deltas. A delta beyond that limit SHALL answer 422 naming `deltas` and SHALL store nothing.

Trace: REQ-040, design D7

#### Scenario: Limit of deltas

- **GIVEN** a solution holds 999 deltas
- **WHEN** a delta is captured
- **THEN** the response is 201
- **WHEN** a further delta is captured
- **THEN** the response is 422 naming `deltas`

### Requirement: Completed solution

A solution in status `implemented` or `archived` SHALL NOT accept a delta to be captured or removed. The request SHALL answer 409 "This solution is completed and can no longer be changed" and change nothing. Every other status SHALL accept it.

Trace: US-040 AC2, US-042 AC4, REQ-040 AC4, solution:BR-03, owner decision 2026-11-12

#### Scenario: Implemented solution

- **GIVEN** a solution in status `implemented`
- **WHEN** a delta is captured or removed in it
- **THEN** the response is 409 "This solution is completed and can no longer be changed"
- **AND** its deltas are unchanged

#### Scenario: Archived solution

- **GIVEN** a solution in status `archived`
- **WHEN** a delta is captured in it
- **THEN** the response is 409

#### Scenario: Solution in progress

- **GIVEN** a solution in status `in-progress`
- **WHEN** a delta is captured in it
- **THEN** the response is 201

### Requirement: Read the deltas

A principal with `entity:read` SHALL read the deltas of a solution, `new` before `retiring` and each group by name (case-insensitive). Each delta SHALL carry its ID, type, name, metatype and time of capture; a delta `retiring` additionally the entity ID; a delta `new` additionally its description and property values. An unknown solution SHALL answer 404.

Trace: US-040 AC1, US-042 AC1, REQ-040

#### Scenario: Delta list

- **GIVEN** a solution holds the deltas `new` `Salesforce` and `retiring` `CRM-Legacy`
- **WHEN** a principal with `entity:read` reads the deltas
- **THEN** `Salesforce` is listed before `CRM-Legacy`, each with its type, name and metatype
- **AND** `CRM-Legacy` carries its entity ID

#### Scenario: No deltas

- **WHEN** the deltas of a new solution are read
- **THEN** the list is empty

#### Scenario: Unknown solution

- **WHEN** the deltas of an unknown solution ID are read, or a delta is captured in it
- **THEN** the response is 404

### Requirement: Remove a delta

A principal with `solution:write` SHALL remove a delta of a solution together with its property values. A delta ID that does not belong to the solution SHALL answer 404.

Trace: REQ-040, owner decision 2026-11-12

#### Scenario: Remove a delta

- **GIVEN** a solution holds the delta `retiring` `CRM-Legacy`
- **WHEN** a solution architect removes it
- **THEN** the response is 204
- **AND** the delta list no longer shows `CRM-Legacy`
- **AND** a delta `retiring` for `CRM-Legacy` can be captured again

#### Scenario: Delta of another solution

- **WHEN** a delta of the solution "Sales Portal" is removed through the solution "CRM Replacement"
- **THEN** the response is 404
- **AND** the delta stays

### Requirement: Permissions for deltas

Capturing and removing a delta SHALL need `solution:write`, in any solution and not only as its owner. Reading the deltas SHALL need `entity:read`. A missing permission SHALL answer 403 and change nothing.

Trace: REQ-040, ADR-032, owner decision 2026-11-12

#### Scenario: Other solution architect

- **GIVEN** a solution owned by Michael
- **WHEN** another person with `solution:write` captures a delta in it
- **THEN** the response is 201

#### Scenario: Without solution:write

- **WHEN** a principal with `entity:read` but without `solution:write` captures or removes a delta
- **THEN** the response is 403

#### Scenario: Without entity:read

- **WHEN** a principal without `entity:read` reads the deltas
- **THEN** the response is 403

### Requirement: Audit of deltas

Capturing a delta SHALL write the audit event `solution.delta-added`, and removing one SHALL write `solution.delta-removed`. Each event SHALL name the solution, the delta, its type, and the entity ID or the metatype and name, but no property values. A rejected request SHALL NOT be audited.

Trace: REQ-040, ADR-033

#### Scenario: Delta captured

- **WHEN** a delta `new` `Salesforce` is captured
- **THEN** the audit log holds `solution.delta-added` with the solution, the delta, the type `new`, the metatype and the name `Salesforce`

#### Scenario: Delta removed

- **WHEN** a delta `retiring` `CRM-Legacy` is removed
- **THEN** the audit log holds `solution.delta-removed` with the solution, the delta, the type `retiring` and the entity ID

#### Scenario: Rejected capture

- **WHEN** a capture is rejected with 422
- **THEN** no `solution.delta-added` is written

### Requirement: Area "Changes" on the page "Solution"

The page "Solution" SHALL show an area "Changes" with the delta list; each row SHALL show the type, the name and the metatype. Without `solution:write`, or for a completed solution, the area SHALL show the list without write actions.

Trace: US-040 AC1, US-042 AC1, REQ-040, SCR-061

#### Scenario: Read only

- **WHEN** a principal without `solution:write` opens the page of a solution with deltas
- **THEN** the area "Changes" lists them without "New entity", "Remove" and "Retire"

### Requirement: Write actions on the page "Solution"

For a principal with `solution:write`, while the solution is not completed, the page SHALL offer the button "New entity" with a dialog for metatype, name, description and the properties of the chosen metatype, and the action "Remove" per delta after a confirmation. A rejected capture SHALL show the message of the response at the field it names.

Trace: US-040 AC1, REQ-040, SCR-061

#### Scenario: Capture a new entity in the dialog

- **WHEN** a solution architect opens "New entity", chooses Application, enters `Salesforce` and a value for each mandatory property, and saves
- **THEN** the dialog closes and the area "Changes" lists `Salesforce` as new

#### Scenario: Remove after confirmation

- **WHEN** a solution architect chooses "Remove" for a delta and confirms
- **THEN** the delta is no longer listed

#### Scenario: Rejected in the dialog

- **WHEN** the capture answers 422 naming `name`
- **THEN** the dialog stays open and shows the message at the name

### Requirement: Action "Retire" in the baseline

For a principal with `solution:write`, while the solution is not completed, each row of the baseline SHALL offer the action "Retire". A row that already has a delta SHALL show the marker "Retiring" instead.

Trace: US-042 AC1, SCR-061

#### Scenario: Retire from the baseline

- **WHEN** a solution architect chooses "Retire" in the baseline row `CRM-Legacy`
- **THEN** the area "Changes" lists `CRM-Legacy` as retiring
- **AND** the baseline row shows "Retiring" instead of the action

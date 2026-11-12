# Delta for solution/solution-deltas

## ADDED Requirements

### Requirement: Capture a modification

A principal with `solution:write` SHALL capture a delta `modified` for an entity of the baseline, with a new name, a new description and property values, each optional. An empty property value SHALL mean that the value is removed. The delta SHALL NOT change the entity in the catalog. An ID that is not an entity of the baseline SHALL answer 422 "Entity not found in the current landscape" naming `entityId`.

Trace: US-041 AC1, AC3, REQ-040 AC3, AC6, owner decision 2026-11-12

#### Scenario: Modify a property

- **GIVEN** `ERP-Core` is in the baseline with the property "Release" `v4`
- **WHEN** a solution architect captures a delta `modified` for `ERP-Core` with "Release" `v5`
- **THEN** the response is 201 with the change "Release" from `v4` to `v5`
- **AND** the delta list shows `ERP-Core` as `modified`
- **AND** the catalog still holds "Release" `v4`

#### Scenario: Rename and describe

- **WHEN** a delta `modified` for `ERP-Core` with the name `ERP-Core 2` and a new description is captured
- **THEN** the delta holds the changes of the name and the description with their before values

#### Scenario: Remove a value

- **GIVEN** `ERP-Core` has the optional property "Users" `250`
- **WHEN** a delta `modified` with an empty value for "Users" is captured
- **THEN** the delta holds the change "Users" from `250` to no value

#### Scenario: Entity not in the baseline

- **WHEN** a delta `modified` is captured for an entity ID that does not exist, for a deleted entity or for a data flow
- **THEN** the response is 422 "Entity not found in the current landscape" naming `entityId`
- **AND** no delta is stored

### Requirement: Before values recorded by the server

Each change of a delta `modified` SHALL record the value of its field in the catalog at the moment of capture as its before value. A request SHALL NOT set a before value. A field whose new value equals its current value SHALL NOT be a change; a delta without any change SHALL answer 422 naming `properties` and store nothing.

Trace: US-041 AC2, owner decision 2026-11-12

#### Scenario: Before value from the catalog

- **GIVEN** `ERP-Core` has "Release" `v4`
- **WHEN** a delta `modified` with "Release" `v5` is captured
- **THEN** its before value is `v4`

#### Scenario: Before value stays when the catalog changes

- **GIVEN** a delta `modified` recorded "Release" from `v4` to `v5`
- **WHEN** the catalog value of "Release" changes to `v4.1`
- **THEN** the delta still shows the before value `v4`

#### Scenario: Nothing changes

- **WHEN** a delta `modified` for `ERP-Core` with "Release" `v4`, its current value, and nothing else is captured
- **THEN** the response is 422 naming `properties`

### Requirement: Checks of a modification

A delta `modified` SHALL be checked like an update of its entity: the name has 1–255 characters and is not blank, the description at most 2000 characters, each value matches the data type and constraints of its property, and a mandatory property keeps a value. A failed check SHALL answer 422 naming the invalid fields and store nothing.

Trace: US-041, REQ-040, owner decision 2026-11-12

#### Scenario: Invalid value

- **WHEN** a delta `modified` with the value `many` for the integer property "Users" is captured
- **THEN** the response is 422 naming the value of that property

#### Scenario: Mandatory value removed

- **GIVEN** "Owner" is mandatory for applications
- **WHEN** a delta `modified` with an empty value for "Owner" is captured
- **THEN** the response is 422 naming `properties`

#### Scenario: Length of the new name

- **WHEN** a delta `modified` with a name of 255 characters is captured
- **THEN** the response is 201
- **WHEN** a delta `modified` with a name of 256 characters or a blank name is captured
- **THEN** the response is 422 naming `name`

#### Scenario: Length of the new description

- **WHEN** a delta `modified` with a description of 2000 characters is captured
- **THEN** the response is 201
- **WHEN** a delta `modified` with a description of 2001 characters is captured
- **THEN** the response is 422 naming `description`

### Requirement: Visibility of a modification

A new value of a `role-restricted` property SHALL need a write role of the property, and a new value of a `connection-scoped` property SHALL need the entity in the writer's scope; otherwise the request SHALL answer 403 and store nothing. When the deltas are read, the before and after values of a property the reader may not read SHALL be redacted.

Trace: US-041, REQ-124, REQ-127, REQ-160

#### Scenario: Restricted property without a write role

- **GIVEN** the property "Cost" is `role-restricted` and writable by the role `finance`
- **WHEN** a solution architect without that role captures a delta `modified` with a value of "Cost"
- **THEN** the response is 403

#### Scenario: Connection-scoped property outside the scope

- **GIVEN** the property "Salary Band" is `connection-scoped` and `ERP-Core` is outside the writer's scope
- **WHEN** a delta `modified` for `ERP-Core` with a value of "Salary Band" is captured
- **THEN** the response is 403

#### Scenario: Restricted values redacted on read

- **GIVEN** a delta `modified` changes the `role-restricted` property "Cost"
- **WHEN** a principal without a read role of "Cost" reads the deltas
- **THEN** the change of "Cost" is redacted, without its before and after values

### Requirement: Edit a modification

A principal with `solution:write` SHALL replace the changes of a delta `modified` with the checks of a capture. A field that was already part of the delta SHALL keep its recorded before value; a field added by the edit SHALL record its current value. Editing a delta of another type SHALL answer 422 naming `deltaType`; a delta of another solution SHALL answer 404.

Trace: US-041, REQ-040, owner decision 2026-11-12

#### Scenario: Edit the after value

- **GIVEN** a delta `modified` records "Release" from `v4` to `v5`, and the catalog now holds `v4.1`
- **WHEN** the delta is edited to "Release" `v6` and "Users" `300`
- **THEN** the delta holds "Release" from `v4` to `v6` and "Users" from its current value to `300`

#### Scenario: Edit a retiring delta

- **WHEN** a delta `retiring` is edited
- **THEN** the response is 422 naming `deltaType`

#### Scenario: Edit without solution:write

- **WHEN** a principal without `solution:write` edits a delta
- **THEN** the response is 403

### Requirement: Audit of modifications

Capturing a delta `modified` SHALL write `solution.delta-added`, and editing one SHALL write `solution.delta-updated`. Both SHALL name the solution, the delta, the entity and the changed fields, but no values. A rejected request SHALL NOT be audited.

Trace: REQ-040, ADR-033

#### Scenario: Modification audited

- **WHEN** a delta `modified` with "Release" `v5` is captured and then edited to `v6`
- **THEN** the audit log holds `solution.delta-added` and `solution.delta-updated`, each naming the field "Release" and neither the value `v5` nor `v6`

### Requirement: Actions "Modify" and "Edit" on the page "Solution"

For a principal with `solution:write`, while the solution is not completed, each row of the baseline without a delta SHALL offer "Modify", and each delta `modified` in the delta list SHALL offer "Edit". Both SHALL open the dialog "Modify entity", which shows per field the current value and an input for the new value, and sends only changed fields. A row of the baseline with a delta `modified` SHALL show the marker "Modified".

Trace: US-041 AC1, AC2, SCR-061

#### Scenario: Modify from the baseline

- **WHEN** a solution architect chooses "Modify" in the baseline row `ERP-Core`, enters "Release" `v5` and saves
- **THEN** the delta list shows `ERP-Core` as modified with one change
- **AND** the baseline row shows "Modified"

#### Scenario: Current values in the dialog

- **WHEN** the dialog "Modify entity" opens for `ERP-Core`
- **THEN** it shows "Release" `v4` as the current value, which cannot be edited

#### Scenario: Edit from the delta list

- **WHEN** a solution architect chooses "Edit" for the delta `modified` of `ERP-Core`, changes "Release" to `v6` and saves
- **THEN** the delta shows "Release" from `v4` to `v6`

## MODIFIED Requirements

### Requirement: One delta per entity

A solution SHALL hold at most one delta per entity. A second delta `retiring` or `modified` for the same entity, and a second delta `new` with the same metatype and the same name (case-insensitive), SHALL answer 422 "A delta already exists for this entity in this solution" and SHALL store nothing. Another solution SHALL NOT be affected.

Trace: US-040 AC3, US-041 AC5, US-042 AC3, REQ-040 AC5

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

#### Scenario: Retired entity modified

- **GIVEN** the solution holds a delta `retiring` for `ERP-Core`
- **WHEN** a delta `modified` for `ERP-Core` is captured in the same solution
- **THEN** the response is 422 "A delta already exists for this entity in this solution"

#### Scenario: Other solution

- **GIVEN** the solution "CRM Replacement" holds a delta `retiring` for `CRM-Legacy`
- **WHEN** a delta `retiring` for `CRM-Legacy` is captured in the solution "Sales Portal"
- **THEN** the response is 201

### Requirement: Completed solution

A solution in status `implemented` or `archived` SHALL NOT accept a delta to be captured, edited or removed. The request SHALL answer 409 "This solution is completed and can no longer be changed" and change nothing. Every other status SHALL accept it.

Trace: US-040 AC2, US-041 AC4, US-042 AC4, REQ-040 AC4, solution:BR-03, owner decision 2026-11-12

#### Scenario: Implemented solution

- **GIVEN** a solution in status `implemented`
- **WHEN** a delta is captured, edited or removed in it
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

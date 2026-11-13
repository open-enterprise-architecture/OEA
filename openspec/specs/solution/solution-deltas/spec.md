# solution/solution-deltas Specification

## Purpose

Defines the entity deltas of a solution in project mode: the new entities an initiative introduces and the existing entities it retires. Covers how deltas are captured, read and removed, their checks, limits and audit, and how they appear on the page "Solution".

## Requirements

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

### Requirement: Read the deltas

A principal with `entity:read` SHALL read the deltas of a solution, `new`, then `modified`, then `retiring`, each group by name (case-insensitive), then by delta ID. Each delta SHALL carry its ID, type, name, metatype and time of capture; a delta `modified` or `retiring` additionally the entity ID; a delta `new` additionally its description and property values; a delta `modified` its changes. An unknown solution SHALL answer 404.

Trace: US-040 AC1, US-041 AC1, US-042 AC1, REQ-040

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

A principal with `solution:write` SHALL replace the changes of a delta `modified` with the checks of a capture. A field that was already part of the delta SHALL keep its recorded before value; a field added by the edit SHALL record its current value. A change of a property the editor may not read SHALL stay as recorded. Editing a delta of another type SHALL answer 422 naming `deltaType`; a delta of another solution SHALL answer 404.

Trace: US-041, REQ-040, owner decision 2026-11-12

#### Scenario: Edit the after value

- **GIVEN** a delta `modified` records "Release" from `v4` to `v5`, and the catalog now holds `v4.1`
- **WHEN** the delta is edited to "Release" `v6` and "Users" `300`
- **THEN** the delta holds "Release" from `v4` to `v6` and "Users" from its current value to `300`

#### Scenario: Change the editor may not read

- **GIVEN** a delta `modified` changes "Release" and the `role-restricted` property "Cost"
- **WHEN** a solution architect without a read role of "Cost" edits "Release"
- **THEN** the change of "Cost" stays as recorded

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

### Requirement: Any number of deltas

A solution SHALL hold any number of deltas. Capturing a delta SHALL NOT be rejected because of the number of deltas the solution already holds.

Trace: REQ-040, owner decision 2026-11-13

#### Scenario: No limit of deltas

- **GIVEN** a solution holds 1000 deltas
- **WHEN** a further delta is captured
- **THEN** the response is 201

### Requirement: Paging of the deltas

The deltas SHALL be paged with `page` and `pageSize` from 1 to 200, default 50. The response SHALL carry the number of deltas per type over all pages and, while a following page exists, an opaque `nextCursor`; a query with `cursor` and `page` SHALL return the deltas after the cursor's page. A changed cursor, one of another solution or one without `page` SHALL answer 400 naming `cursor`.

Trace: owner decision 2026-11-13, REQ-071

#### Scenario: Pages by cursor

- **GIVEN** a solution with 60 deltas of all three types
- **WHEN** every page of size 20 is loaded with the cursor of the previous one
- **THEN** the pages hold every delta exactly once, in the order of the delta list, and the last page has no `nextCursor`
- **AND** each page carries the counts per type over all 60

#### Scenario: Limits of the page size

- **WHEN** the deltas are loaded with page size 1 or 200
- **THEN** the response is 200
- **WHEN** they are loaded with page size 0 or 201
- **THEN** the response is 400

#### Scenario: Cursor of another solution

- **WHEN** a cursor of another solution is sent, or a changed cursor
- **THEN** the response is 400 naming `cursor`

### Requirement: Load more deltas on the page "Solution"

The area "Changes" SHALL show the first page of the deltas and the total. While a following page exists, it SHALL offer "Load more", which appends the next page. After a delta is captured, edited or removed, the area SHALL load its first page again.

Trace: owner decision 2026-11-13, SCR-061

#### Scenario: Load more

- **GIVEN** a solution with 60 deltas
- **WHEN** a reader opens the page "Solution"
- **THEN** the area "Changes" shows 50 deltas, the total 60 and "Load more"
- **WHEN** the reader chooses "Load more"
- **THEN** it shows all 60 deltas and no longer offers "Load more"

#### Scenario: One page only

- **GIVEN** a solution with 50 deltas
- **WHEN** a reader opens the page "Solution"
- **THEN** the area "Changes" shows all 50 deltas without "Load more"

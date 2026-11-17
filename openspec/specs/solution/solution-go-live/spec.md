# solution/solution-go-live Specification

## Purpose

Defines the go-live of a solution in project mode: its preview, the field conflicts between a delta and later changes of its entity and their resolution, the transaction that turns the deltas into the current state with soft deletion and open issues, its audit, and the go-live action on the page "Solution" with the merge dialog SCR-063.

## Requirements

### Requirement: Preview of a go-live

A principal with `solution:write` SHALL read the preview of the go-live of a solution: the number of entities that become new, modified and retired, including the connections retired with an endpoint, the field conflicts and the metamodel violations. A value the caller may not read SHALL be redacted.

Trace: US-152 AC5

#### Scenario: Counts of a solution

- **WHEN** a solution has 1 delta `new`, 2 deltas `modified` and 1 delta `retiring` whose entity is the endpoint of 1 connection
- **THEN** the preview counts 1 new, 2 modified and 2 retired entities

### Requirement: Field conflicts

A field conflict SHALL exist when a field a delta `modified` changes has, in the entity, a value other than the before value the delta recorded. A change of a field the delta does not change SHALL NOT be a conflict.

Trace: US-152 AC2, AC3, issue #69

#### Scenario: Field changed after the capture

- **WHEN** a delta changes the description of "ERP-Core" from "Old" to "New", and the catalog changed it to "Other" afterwards
- **THEN** the preview lists a conflict for the description with before "Old", current "Other" and delta "New"

#### Scenario: Other field changed after the capture

- **WHEN** the delta changes only the description and the catalog changed the owner afterwards
- **THEN** the preview lists no conflict

### Requirement: Resolution of field conflicts

A principal with `solution:write` SHALL resolve the conflicts of a delta per field by taking the current value, the delta value or an own value. The delta SHALL then record the current value as its before value and the chosen value as its after value; taking the current value SHALL remove the field from the delta. A field without a conflict SHALL be answered with 422 naming the field.

Trace: US-152 AC2

#### Scenario: Delta value taken

- **WHEN** the conflict of the description is resolved with the delta value
- **THEN** the delta records "Other" as before and "New" as after value, and the preview lists no conflict

#### Scenario: Current value taken

- **WHEN** the conflict is resolved with the current value
- **THEN** the description is no longer part of the delta

#### Scenario: Own value

- **WHEN** the conflict is resolved with the own value "Merged"
- **THEN** the delta records "Other" as before and "Merged" as after value

#### Scenario: Field without a conflict

- **WHEN** a resolution names the owner, which has no conflict
- **THEN** the request is answered with 422 naming the owner, and the delta is unchanged

### Requirement: Go-live of a solution

A principal with `solution:write` SHALL take a solution live. With an unresolved field conflict, the request SHALL be answered with 409 `merge-required` listing the conflicts, and nothing SHALL change. A solution in status `implemented` or `archived` SHALL be answered with 409. Every other status SHALL be accepted.

Trace: US-152 AC1, AC2, owner decisions 2026-11-16

#### Scenario: Unresolved conflict

- **WHEN** a solution with an unresolved conflict is taken live
- **THEN** the response is 409 `merge-required` with the conflict, and no entity and no status changes

#### Scenario: Implemented solution

- **WHEN** a solution in status `implemented` is taken live again
- **THEN** the response is 409, and nothing changes

#### Scenario: Without permission

- **WHEN** a person without `solution:write` takes a solution live
- **THEN** the request is rejected with 403

### Requirement: Effects of a go-live

In one transaction, a go-live SHALL:

- create the entity of every delta `new` and record its ID in the delta;
- write the after values of every delta `modified`;
- soft-delete the entity of every delta `retiring` and the connections of that entity;
- set the solution to `implemented`.

On an error, nothing SHALL change.

Trace: US-152 AC1, ADR-019

#### Scenario: New, modified and retired

- **WHEN** a solution with a delta `new` "Salesforce", a delta `modified` of "ERP-Core" and a delta `retiring` of "CRM-Legacy" goes live
- **THEN** "Salesforce" exists, "ERP-Core" has the delta values, "CRM-Legacy" is deleted and no longer in catalog and baseline, and the solution is `implemented`

#### Scenario: Connection of a retired entity

- **WHEN** "CRM-Legacy" is the source of the connection "CRM→ERP" and is retired
- **THEN** "CRM→ERP" is deleted too

#### Scenario: Error during the go-live

- **WHEN** writing an entity fails during the go-live
- **THEN** no entity, delta or status has changed

### Requirement: Open issues of a go-live

A metamodel violation of a delta, such as an empty mandatory property or a value the current definition no longer accepts, SHALL NOT block the go-live. The value SHALL be taken as captured where its column can hold it, and the violation SHALL be stored as an open issue of the solution, naming the entity, the field and the kind of violation.

Trace: US-152 AC4, owner decision 2026-11-16

#### Scenario: Mandatory property missing

- **WHEN** a delta `new` lacks the property "Owner", which became mandatory after its capture, and the solution goes live
- **THEN** the entity is created without "Owner", and the solution lists an open issue "mandatory missing" for "Owner" of that entity

#### Scenario: Value no longer accepted

- **WHEN** a delta sets the enumeration value "retired", which was removed from its definition, and the solution goes live
- **THEN** the value is taken, and the solution lists an open issue "invalid value" for the field

### Requirement: Audit of a go-live

A go-live SHALL write the event `solution.went-live` with the solution and the counts of new, modified and retired entities and of open issues. Every created, changed and deleted entity SHALL write its own event.

Trace: US-152, ADR-033

#### Scenario: Events of a go-live

- **WHEN** a solution with one delta of each type goes live
- **THEN** one `solution.went-live`, one `entity.created`, one `entity.updated` and one `entity.deleted` are written

### Requirement: Go-live on the page "Solution"

The page "Solution" (SCR-061) SHALL offer "Go live" to a holder of `solution:write` while the solution is not `implemented` or `archived`. The action SHALL show a dialog with the counts, the notice that the go-live cannot be undone, and a confirmation. With conflicts, it SHALL first open the merge dialog SCR-063. After the go-live, the page SHALL show the status `implemented` and the open issues.

Trace: US-152 AC2, AC5

#### Scenario: Confirmation dialog

- **WHEN** Michael clicks "Go live" on a solution without conflicts
- **THEN** a dialog shows the counts and the notice, and the go-live runs after the confirmation

#### Scenario: Merge before the go-live

- **WHEN** Michael clicks "Go live" on a solution with a conflict
- **THEN** the merge dialog lists the conflict with its three choices, and the confirmation follows when every conflict is resolved

#### Scenario: Action hidden after the go-live

- **WHEN** a solution is `implemented`
- **THEN** the page offers no "Go live" and lists its open issues

### Requirement: Status of an open issue

Every open issue SHALL have the status `open` or `resolved`. A resolved issue SHALL name its resolution: `value` or `removed` with the person and time that resolved it, or `automatic` when its violation no longer holds against the current metamodel and values. The automatic resolution SHALL be evaluated on every read and every blocking check, without being stored. `openIssueCount` SHALL count only open issues.

Trace: US-153 AC2, AC4, owner decision 2026-11-17

#### Scenario: Resolved automatically by a metamodel change

- **WHEN** "Salesforce" has the open issue "mandatory missing" for "Contact", and the metamodel makes "Contact" optional
- **THEN** the issue is listed as `resolved` with the resolution `automatic`, and the solution counts no open issue

#### Scenario: Still open

- **WHEN** "Contact" stays mandatory and "Salesforce" still has no value for it
- **THEN** the issue is listed as `open`

### Requirement: Resolve open issues

`resolveSolutionOpenIssues` SHALL take per open issue a value, or the removal of the value for an `invalid-value` issue of an optional property. In one transaction, it SHALL write the values with a new version, mark the issues resolved with person and time, and audit `solution.open-issues-resolved`. A rejected value, the removal of a mandatory value, or an issue that is resolved or of another solution SHALL give 422 naming the issue, and nothing changes.

Trace: US-153 AC2, AC3

#### Scenario: Resolve with a valid value

- **WHEN** Michael resolves "mandatory missing" for "Contact" of "Salesforce" with `sales@example.com`
- **THEN** "Salesforce" has the value in a new version, the issue is `resolved` with the resolution `value`, Michael and the time, and `solution.open-issues-resolved` is written

#### Scenario: Value outside the definition

- **WHEN** Michael resolves "invalid value" for "Users" of "ERP-Core", which accepts at most 1000, with 5000
- **THEN** the answer is 422 naming the issue, and neither the entity nor the issue changes

#### Scenario: Last valid value

- **WHEN** Michael resolves the same issue with 1000
- **THEN** the issue is resolved and "ERP-Core" has 1000

#### Scenario: Remove an optional value

- **WHEN** Michael removes the value of "Users", an optional property, to resolve its issue
- **THEN** the value is removed and the issue is `resolved` with the resolution `removed`

#### Scenario: Remove a mandatory value

- **WHEN** Michael removes the value of a mandatory property to resolve its issue
- **THEN** the answer is 422 naming the issue

#### Scenario: Issue already resolved

- **WHEN** Michael resolves an issue that is already resolved
- **THEN** the answer is 422 naming the issue

### Requirement: Permission to resolve open issues

Resolving open issues SHALL need `solution:write` or `entity:write`. With `solution:write`, the values SHALL be taken without a check of write roles, as in the go-live. With only `entity:write`, the write roles and the scope of a catalog write SHALL apply, and a value the person may not write SHALL give 403. Without either permission the answer SHALL be 403.

Trace: US-153 Technical Notes, REQ-162, owner decision 2026-11-17

#### Scenario: Catalog writer resolves

- **WHEN** a person with `entity:write` and without `solution:write` resolves an issue of a property without write roles
- **THEN** the issue is resolved

#### Scenario: Catalog writer without the write role

- **WHEN** that person resolves an issue of a role-restricted property without one of its write roles
- **THEN** the answer is 403 and nothing changes

#### Scenario: Viewer

- **WHEN** a person with only `entity:read` resolves an issue
- **THEN** the answer is 403

### Requirement: Entities blocked by open issues

An entity with an open issue SHALL be blocked until all its issues are resolved: a write of it other than resolving its issues, and the go-live of another solution with a delta `modified` or `retiring` of it, SHALL be rejected with 409 of type `entity-blocked` naming the entity, its open issues and their solution, and nothing changes. Reads SHALL NOT be blocked.

Trace: US-153 AC1, owner decision 2026-11-17

#### Scenario: Go-live of another solution

- **WHEN** "Salesforce" has an open issue, and a solution with a delta `modified` of "Salesforce" goes live
- **THEN** the answer is 409 `entity-blocked` naming the delta and the issue, and nothing changes

#### Scenario: Unblocked after the resolution

- **WHEN** the last open issue of "Salesforce" is resolved, and that solution goes live
- **THEN** the go-live runs

### Requirement: Resolve open issues on the page "Solution"

The list "Open issues" of the page "Solution" (SCR-061) SHALL show the status of every issue and, to a holder of `solution:write` or `entity:write`, "Resolve" while an issue is open. It SHALL open the dialog SCR-064, grouped by entity, with per issue the field, the kind, the current value, an input and, where allowed, "Remove value". "Apply" SHALL be enabled once every issue has a value or a removal.

Trace: US-153 AC2, AC3

#### Scenario: Dialog of the open issues

- **WHEN** Michael clicks "Resolve" on a solution with two open issues
- **THEN** the dialog lists both, and after "Apply" the list shows them as resolved

#### Scenario: Rejected value in the dialog

- **WHEN** the server rejects a value with 422
- **THEN** the dialog stays open and names the field

#### Scenario: No action for a viewer

- **WHEN** a person with only `entity:read` opens the page
- **THEN** the list shows the issues without "Resolve"

### Requirement: Snapshot values at the go-live

A value of a delta whose property or enumeration value exists only in a snapshot of the solution SHALL be a conflict of the kind `not-in-metamodel` in the preview and in the merge dialog SCR-063, with the choices "Discard value" and "Resolve later". Such a conflict SHALL NOT stop the go-live. A value not discarded SHALL become an open issue `not-in-metamodel` keeping the value; the go-live SHALL drop the snapshots of the solution.

Trace: US-154 AC4, owner decision 2026-11-17

#### Scenario: Value resolved later

- **WHEN** "CRM Replacement" kept "Cost Center" as a snapshot, a delta `new` has the value "CC-100", and Michael takes it live without discarding the value
- **THEN** the entity is created without "Cost Center", the open issue `not-in-metamodel` keeps "CC-100" and blocks the entity, and the snapshot is dropped

#### Scenario: Value discarded

- **WHEN** Michael chooses "Discard value" in the merge dialog
- **THEN** the value is removed from the delta, and the go-live leaves no open issue for it

### Requirement: Resolve a value not in the metamodel

An open issue `not-in-metamodel` SHALL be resolved by discarding its value, with the resolution `removed`, or, once the property is mapped again or the enumeration value exists again, by writing the kept or another valid value. Writing a value while the property is still not in the metamodel SHALL give 422 naming the issue. It SHALL NOT be resolved automatically.

Trace: US-154 AC4

#### Scenario: Discard the kept value

- **WHEN** Michael discards the value of the issue
- **THEN** the issue is `resolved` with the resolution `removed`, and the entity is no longer blocked

#### Scenario: Write after the property is mapped again

- **WHEN** "Cost Center" is mapped to "Application Component" again, and Michael resolves the issue with the kept value "CC-100"
- **THEN** the entity has "CC-100", and the issue is resolved

#### Scenario: Write while still not mapped

- **WHEN** Michael resolves the issue with a value while "Cost Center" is not mapped
- **THEN** the answer is 422 naming the issue

### Requirement: A plateau solution goes live with its plateau

The go-live of a single solution that targets a plateau SHALL be refused with 409 `plateau-solution`, and nothing SHALL change. Its preview SHALL stay readable. The page "Solution" SHALL offer no "Go live" for it and SHALL link its plateau.

Trace: US-157 AC3

#### Scenario: Single go-live of a plateau solution

- **GIVEN** "CRM Replacement" targets "Target 2027"
- **WHEN** Michael takes "CRM Replacement" live
- **THEN** the response is 409 `plateau-solution`, and the solution keeps its status

#### Scenario: Project solution

- **GIVEN** "Website Relaunch" has no plateau
- **WHEN** Michael takes it live
- **THEN** it goes live as before

### Requirement: Current value of a plateau solution

For a delta of a solution that targets a plateau, the current value of a field conflict, and the value a resolution records as the before value, SHALL be the value at its turn in the go-live of the plateau.

Trace: US-157 AC2

#### Scenario: Resolved against the earlier solution

- **GIVEN** "CRM Replacement" goes first and changes the description of "ERP-Core" to "Core ERP"
- **WHEN** Michael resolves the conflict of "ERP Extension" on that description with the current value
- **THEN** the field leaves the delta of "ERP Extension", and "Core ERP" stays after the go-live

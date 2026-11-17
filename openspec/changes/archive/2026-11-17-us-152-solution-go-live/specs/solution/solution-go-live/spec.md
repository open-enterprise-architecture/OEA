## Purpose

Defines the go-live of a solution in project mode: its preview, the field conflicts between a delta and later changes of its entity and their resolution, the transaction that turns the deltas into the current state with soft deletion and open issues, its audit, and the go-live action on the page "Solution" with the merge dialog SCR-063.

## ADDED Requirements

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

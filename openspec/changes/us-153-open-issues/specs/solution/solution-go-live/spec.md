## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Fields not restored

A restore SHALL keep the current value of a property, and the preview and the result SHALL list it as not restored with its reason, when:

- the caller may not write it (`write-protected`);
- it is no longer mapped to the metatype (`not-mapped`);
- the current definition no longer accepts the target value (`invalid-value`);
- it was neither mapped nor set at the target version (`not-in-source`).

Trace: REQ-101 AC4, REQ-105 AC2, UC-15 E1

#### Scenario: Write-protected property in the preview

- **WHEN** a caller without the write role of the `role-restricted` property `investmentCost` previews a restore to a version with a different value of it
- **THEN** the preview lists `investmentCost` as not restored with the reason `write-protected`

#### Scenario: Property no longer mapped

- **WHEN** the target version has a value of the property `legacyCode`, whose mapping to the metatype was removed since
- **THEN** the preview lists `legacyCode` as not restored with the reason `not-mapped`

#### Scenario: Value no longer accepted

- **WHEN** the target version has the enumeration value `retired` of `lifecycleStatus`, which was removed from the definition since
- **THEN** the preview lists `lifecycleStatus` as not restored with the reason `invalid-value`

#### Scenario: Write-protected property is kept

- **WHEN** a caller without the write role of `investmentCost` restores to a version with a different value of it
- **THEN** `investmentCost` keeps its current value, the other fields are restored, and the response lists `investmentCost` as not restored with the reason `write-protected`

#### Scenario: Property added after the target version

- **WHEN** the property `costCenter` was mapped to the metatype after the target version and has a value now
- **THEN** the preview lists `costCenter` as not restored with the reason `not-in-source`, and a full restore keeps its value

## ADDED Requirements

### Requirement: Partial restore

A restore that names `fields` SHALL restore only those fields, by the keys of the preview (`name`, `description`, `isLogical` or the ID of a property definition). Every other field SHALL keep its value. A selected field that cannot be restored SHALL keep its value and be listed as not restored with its reason. A restore without `fields` SHALL restore in full.

Trace: US-105, REQ-105 AC4, REQ-106, UC-16

#### Scenario: Only the selected fields

- **WHEN** an entity at version 5 differs from version 3 in `description`, `owner` and `criticality`, and the architect restores it to version 3 with the fields `description` and `owner`
- **THEN** `description` and `owner` have the values of version 3, and `criticality` keeps its current value

#### Scenario: Selected write-protected property

- **WHEN** a partial restore selects `description` and the property `investmentCost`, which the caller may not write
- **THEN** `description` is restored, `investmentCost` keeps its value, and the response lists `investmentCost` as not restored with the reason `write-protected`

### Requirement: Rejections of a partial restore

- **Empty selection:** `fields` without an entry SHALL be answered with 422 `validation-failed` naming `fields`.
- **Unknown field:** a key that is not a field whose current value differs from the target version SHALL be answered with 422 `validation-failed` naming `fields`.
- **Nothing restorable:** selected fields of which none can be restored SHALL be answered with 422 `nothing-to-restore`.

A rejected restore SHALL change nothing and write no version.

Trace: US-106 AC2, REQ-106 AC2, UC-16 A4

#### Scenario: Empty selection

- **WHEN** a restore is sent with `fields` empty
- **THEN** the request is answered with 422 naming `fields`, and the entity is unchanged

#### Scenario: Identical field selected

- **WHEN** a partial restore selects `isLogical`, which has the same value in the current state and the target version
- **THEN** the request is answered with 422 naming `fields`, and the entity is unchanged

#### Scenario: One selected field of several

- **WHEN** an entity differs from the target version in 3 fields and a partial restore selects 1 of them
- **THEN** the restore is executed and restores exactly that field

### Requirement: Restored fields of a version

A version written by a partial restore SHALL record the keys of the fields it restored as `restoredFields`, besides `restoredFromVersion` and the reason. A full restore SHALL record no `restoredFields`. The audit event `entity.restored` SHALL name `restoredFields` of a partial restore.

Trace: US-106 AC1, REQ-106 AC1

#### Scenario: Recorded fields

- **WHEN** a partial restore to version 4 restores `description` and `owner`
- **THEN** the new version records `restoredFromVersion` 4 and `restoredFields` `description` and the ID of `owner`, and the event `entity.restored` names both

#### Scenario: Full restore records no fields

- **WHEN** a restore without `fields` is executed
- **THEN** the new version records no `restoredFields`

### Requirement: Connections in the preview

The preview SHALL list the connections in which the entity is source or target and that changed or were created after the time of the target version. Each SHALL name its ID, its name, the other endpoint, the direction, and whether it was `changed` or `added`. A connection without a change after that time SHALL not be listed.

Trace: US-104, REQ-104, UC-16

#### Scenario: Changed and added connections

- **WHEN** the connection "ERP→CRM" got version 3 after the target version, and the connection "ERP→Cloud-DB" was created after it
- **THEN** the preview lists "ERP→CRM" as `changed` and "ERP→Cloud-DB" as `added`

#### Scenario: Unchanged connection

- **WHEN** the connection "ERP→Mail" has no version after the target version
- **THEN** the preview does not list it

### Requirement: Partial restore wizard

On SCR-026, every version except the current one SHALL offer "Partially restore" to a reader with `entity:write` and `entity:restore`. The action SHALL open the wizard SCR-029 with this version as source. The wizard SHALL offer the steps "General", "Properties", "Connections" and "Summary", and a selection of the source version, whose change reloads the preview and clears the selection.

Trace: US-104 AC1, REQ-104, UC-16 A3

#### Scenario: Source version prefilled

- **WHEN** the architect clicks "Partially restore" on version 3
- **THEN** the wizard opens with version 3 as source

#### Scenario: Change of the source version

- **WHEN** the architect has selected `description` and chooses version 2 as source
- **THEN** the wizard loads the preview for version 2 and nothing is selected

### Requirement: Steps of the wizard

- **Skipping:** a step without differences SHALL be skipped with a note; every step SHALL offer "Skip".
- **Connections:** the step SHALL list the changed and added connections with a link to their history, and restore none.
- **Summary:** the step SHALL list the selected fields, the warnings and the optional reason.
- **Confirm:** "Partially restore" SHALL be disabled without a selected field.

Trace: US-104 AC2, AC3, REQ-104 AC2, AC3, UC-16 A1, A2, A4

#### Scenario: Automatic skipping

- **WHEN** the general fields of the source version and the current state are identical
- **THEN** the wizard skips "General" with the note and shows "Properties"

#### Scenario: Confirm without selection

- **WHEN** no field is selected in any step
- **THEN** "Partially restore" is disabled

#### Scenario: Link to the history of a connection

- **WHEN** the step "Connections" lists the changed connection "ERP→CRM"
- **THEN** its link opens the history of "ERP→CRM"

### Requirement: Field selection in the wizard

Only fields whose current value differs from the source version SHALL be selectable.

- **Identical:** a field with the same value SHALL be grayed out and not selectable.
- **Not restorable:** a field that cannot be restored SHALL show its reason and not be selectable.
- **Redacted:** a redacted field SHALL show "Restricted" without values.
- **Preview:** a selected field SHALL show the source value it will get.

Trace: US-105 AC1–AC3, REQ-105 AC1–AC5

#### Scenario: Identical field grayed out

- **WHEN** `isLogical` is identical in the source version and the current state
- **THEN** its checkbox is grayed out and cannot be clicked

#### Scenario: Property not in the source version

- **WHEN** the property `costCenter` was not present in the source version
- **THEN** the wizard shows "(not present in source version)" and its checkbox cannot be selected

#### Scenario: Preview on selection

- **WHEN** the architect selects `owner`
- **THEN** the wizard shows the source value of `owner` as the value after the restore

# repository/entity-restore Specification

## Purpose

Defines how an entity is restored in full to an earlier version: the preview of the restore with the diff of the current state against the target version, the fields a restore never changes, the handling of properties the caller may not write or read and of values the current metamodel no longer accepts, the protection against lost updates, the boundaries, the atomic write with its audit event, and the restore action on SCR-026 with the modal SCR-027.

## Requirements

### Requirement: Preview of a restore

A principal with `entity:write` and `entity:restore` SHALL read the preview of a restore of an entity to a target version. The preview SHALL name the current version and list every field whose current value differs from the value of the target version, with both values, and whether a restore would restore it.

Trace: US-101 AC2, REQ-101 AC2

#### Scenario: Differing fields

- **WHEN** an enterprise architect previews the restore of an entity at version 5 to version 3, where the description and the property `owner` differ
- **THEN** the preview names version 5 as current and lists `description` and `owner` with their current and target values, both to be restored, and no other field

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

### Requirement: Warnings and redaction in the preview

The preview SHALL list a warning for every mandatory or recommended property that would be empty after the restore. A value the caller may not read SHALL be returned as redacted, without its current or its target value.

Trace: REQ-101 AC5, UC-15 E1

#### Scenario: Empty mandatory property

- **WHEN** the property `owner` is mandatory today and was empty in the target version
- **THEN** the preview lists `owner` as restored and lists a warning for `owner`

#### Scenario: Redacted value in the preview

- **WHEN** a caller without the read role of `investmentCost` previews a restore to a version with a different value of it
- **THEN** the preview lists `investmentCost` as redacted, without its current or its target value

### Requirement: Full restore of an entity

A principal with `entity:write` and `entity:restore` SHALL restore an entity to a target version.

- **Changed:** the name, description, logical flag and every restorable property SHALL get the values of the target version. A property empty in the target version SHALL be removed.
- **Unchanged:** the ID, the metatype, the source and target of a connection, and the creation time and person SHALL stay as they are.

Trace: US-101 AC1, REQ-101 AC1

#### Scenario: Restore to an earlier version

- **WHEN** an enterprise architect restores an entity at version 5 to version 3
- **THEN** name, description, logical flag and properties have the values of version 3, and the ID and metatype are unchanged

#### Scenario: Value empty in the target version

- **WHEN** the property `website` has a value now and had none in the target version
- **THEN** after the restore `website` has no value

#### Scenario: Endpoints of a connection

- **WHEN** a connection is restored to an earlier version
- **THEN** its source and target are unchanged

### Requirement: Version and result of a restore

A restore SHALL write one new version that records the target version as `restoredFromVersion` and the optional reason. The version before the restore SHALL stay unchanged in the history as the state before the restore.

The response SHALL list the restored fields, the fields not restored with their reason, and the warnings. An empty mandatory property SHALL afterwards be marked as missing, as after a metatype change.

Trace: US-102 AC1, AC3, REQ-102 AC1, AC3, UC-15 E1

#### Scenario: Restore version with reason

- **WHEN** an entity at version 5 is restored to version 3 with the reason "Wrong owner"
- **THEN** version 6 records `restoredFromVersion` 3 and the reason, and the state of version 5 is unchanged in the history

#### Scenario: Missing mandatory value after the restore

- **WHEN** an entity is restored to a version in which the now mandatory property `owner` was empty
- **THEN** the restore succeeds, the response lists a warning for `owner`, and the entity is marked as missing `owner`

### Requirement: Protection against lost updates

A restore SHALL require the header `If-Match` with the current version of the entity.

- **Missing:** a restore without `If-Match` SHALL be answered with 428.
- **Stale:** a restore whose `If-Match` names a different version SHALL be answered with 412 `stale-version` and the current version.

Neither SHALL change the entity or write a version.

Trace: REQ-101, UC-15

#### Scenario: Current version

- **WHEN** a restore of an entity at version 5 sends `If-Match: "5"`
- **THEN** the restore is executed

#### Scenario: Changed meanwhile

- **WHEN** a restore of an entity at version 6 sends `If-Match: "5"`
- **THEN** the request is answered with 412 `stale-version` naming version 6, and the entity is unchanged

#### Scenario: Without If-Match

- **WHEN** a restore is sent without `If-Match`
- **THEN** the request is answered with 428, and the entity is unchanged

### Requirement: Target version of a restore

For preview and restore, the target version SHALL be at least 1 and before the current version. Otherwise the request SHALL be answered with 400 naming `targetVersion`. A version after the current one and an unknown entity SHALL be answered with 404. A rejected restore SHALL change nothing.

Trace: REQ-101, US-101

#### Scenario: Target version boundaries

- **WHEN** an entity is at version 5
- **THEN** target versions 1 and 4 are accepted, 0 and 5 are answered with 400 naming `targetVersion`, and 6 is answered with 404

### Requirement: Reason, permission and empty restores

- **Reason:** at most 1000 characters; a longer one SHALL be answered with 400 naming `reason`.
- **Permission:** a principal without `entity:write` or without `entity:restore` SHALL be rejected with 403.
- **Nothing to restore:** a restore in which every restorable field already has its target value SHALL be answered with 422 `nothing-to-restore`.

A rejected restore SHALL change nothing and write no version.

Trace: REQ-101, US-101

#### Scenario: Reason length boundary

- **WHEN** a restore is sent with a reason of 1000 characters, and another with 1001 characters
- **THEN** the first is executed, and the second is answered with 400 naming `reason` and changes nothing

#### Scenario: Only write-protected differences

- **WHEN** the only field that differs from the target version is a property the caller may not write
- **THEN** the restore is answered with 422 `nothing-to-restore` and writes no version

#### Scenario: Without restore permission

- **WHEN** a person with `entity:write` but without `entity:restore` restores an entity
- **THEN** the request is rejected with 403, and the entity is unchanged

### Requirement: Atomic restore with audit

A restore SHALL write the entity, its new version and the audit event `entity.restored` in one transaction. The event SHALL name the entity, the new version, the target version, the reason and the changed fields with old and new value. On an error, the transaction SHALL be rolled back: the entity SHALL be unchanged, and neither a new version nor the event SHALL exist.

Trace: US-103 AC1, AC2, REQ-103 AC1, AC2

#### Scenario: Successful restore

- **WHEN** a restore succeeds
- **THEN** the entity, version N+1 with `restoredFromVersion` and the event `entity.restored` with the changed fields all exist and agree

#### Scenario: Error after the entity was written

- **WHEN** writing the audit event fails during a restore
- **THEN** the entity is unchanged, and no new version and no event exist

### Requirement: Restore action on the history

On the page SCR-026, every version except the current one SHALL offer "Restore this version" to a reader with `entity:write` and `entity:restore`, and to no other reader. The action SHALL open the modal SCR-027. After a restore, the timeline SHALL show the new version.

Trace: US-101 AC2, REQ-101 AC2

#### Scenario: Action hidden without permission

- **WHEN** a reader with `entity:read` only opens the history
- **THEN** no version offers "Restore this version"

#### Scenario: Action on earlier versions

- **WHEN** an enterprise architect opens the history of an entity at version 5
- **THEN** versions 1 to 4 offer "Restore this version", and version 5 does not

### Requirement: Restore modal

The modal SCR-027 SHALL show the diff "current → target", the fields not restored with their reason, the warnings, the notices that the current state stays in the history and that ID, type and connection endpoints are not changed, and an optional reason. "Restore" SHALL be disabled when nothing can be restored. Cancel SHALL change nothing. A 412 SHALL show that the entity was changed meanwhile and reload the preview.

Trace: US-101 AC2, AC3, REQ-101 AC2, AC3

#### Scenario: Confirmation dialog with diff

- **WHEN** an enterprise architect clicks "Restore this version" on version 3
- **THEN** the modal lists the fields that differ between the current state and version 3

#### Scenario: Cancel without change

- **WHEN** the architect closes the modal without confirming
- **THEN** no restore is sent and the entity is unchanged

#### Scenario: Nothing to restore

- **WHEN** the preview lists only fields that will not be restored
- **THEN** "Restore" is disabled

#### Scenario: Changed meanwhile in the dialog

- **WHEN** the restore is answered with 412
- **THEN** the modal shows that the entity was changed meanwhile and shows the reloaded preview

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

### Requirement: Restore of a blocked entity

A full or partial restore of an entity with an open issue of a solution SHALL be rejected with 409 of type `entity-blocked` naming the open issues and their solution, and nothing changes. The preview SHALL stay readable.

Trace: US-153 AC1

#### Scenario: Restore a blocked entity

- **WHEN** an architect restores "Salesforce", which has an open issue, to version 1
- **THEN** the answer is 409 `entity-blocked`, and "Salesforce" keeps its version

#### Scenario: Restore after the resolution

- **WHEN** the issue is resolved, and the architect restores "Salesforce"
- **THEN** the restore runs

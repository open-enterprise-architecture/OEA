# Spec Delta

## Purpose

Defines reading the versions of an entity with their values: the complete state of a version, the field-by-field diff of a version and the net comparison of two versions, the visibility rules for values the reader may not read, the immutability of versions, and how the page "Change history" (SCR-026) shows them.

## ADDED Requirements

### Requirement: Complete state of a version

A principal with `entity:read` SHALL read the complete state of any version of an entity: its number, time in UTC and person, the system fields with their values, and the properties with label, data type and value. The values SHALL be reconstructed from the history of the entity; nothing is stored per version. An unknown entity or version SHALL be answered with 404.

Trace: US-100 AC1, REQ-100

#### Scenario: Complete state with empty properties

- **WHEN** an enterprise architect reads the state of version 4 of an entity whose metatype mapped `lifecycleStatus` and `owner` at that time, and only `lifecycleStatus` had a value
- **THEN** the state lists the system fields, `lifecycleStatus` with its value at version 4, and `owner` without value

#### Scenario: Value of an older version

- **WHEN** `lifecycleStatus` was `planned` up to version 3 and `active` from version 4
- **THEN** the state of version 3 lists `planned` and the state of version 4 lists `active`

#### Scenario: Version boundaries

- **WHEN** the state of an entity with 5 versions is read for version 1, 5, 6 and 0
- **THEN** versions 1 and 5 are returned, version 6 is answered with 404 and version 0 with 400

### Requirement: Properties of a state

The state of a version SHALL list the properties mapped to the entity's metatype at the time of the version (effective date on or before it), including empty ones, plus every property that had a value at that version. A property without value SHALL be listed without value.

Trace: US-100 AC1, REQ-100, owner decision 2026-11-14

#### Scenario: Mapping effective after the version

- **WHEN** `owner` was mapped to the metatype with an effective date one day after version 4, and had no value at version 4
- **THEN** the state of version 4 does not list `owner`

### Requirement: Diff of a version

The diff of version N SHALL compare the state of version N−1 with the state of version N. It SHALL list only the fields whose value differs, each with its label, whether it is a system field, its data type, the old and the new value. Each property SHALL be a field of its own. Version 1 SHALL have no diff.

Trace: US-099 AC1, AC2, REQ-099

#### Scenario: Only changed fields

- **WHEN** version 5 changed the property `owner` from `Kurt` to `Michael` and left all other fields unchanged
- **THEN** the diff of version 5 lists only `owner` with the old value `Kurt` and the new value `Michael`

#### Scenario: Added and removed values

- **WHEN** version 3 set a value of `description` that was empty and removed the value of `lifecycleStatus`
- **THEN** the diff lists `description` without old value and `lifecycleStatus` without new value

### Requirement: Comparison of two versions

Two versions `from` and `to` of an entity SHALL be comparable. The comparison SHALL be the net diff of the state of `from` with the state of `to`. A field that changed between them and has the same value in both SHALL NOT be listed.

- `from` SHALL be at least 1 and before `to`. Otherwise the request SHALL be answered with 400 naming `from`.
- A version that does not exist SHALL be answered with 404.

Trace: US-099 AC3, REQ-099, owner decision 2026-11-14

#### Scenario: Net diff across versions

- **WHEN** versions 2 and 7 are compared, `owner` changed from `Kurt` to `Michael` in version 3, and `lifecycleStatus` changed from `planned` to `active` in version 4 and back to `planned` in version 6
- **THEN** the comparison lists `owner` from `Kurt` to `Michael` and does not list `lifecycleStatus`

#### Scenario: Version range boundaries

- **WHEN** an entity with 7 versions is compared from 1 to 7, from 6 to 7, from 7 to 7, from 0 to 7 and from 1 to 8
- **THEN** 1 to 7 and 6 to 7 are answered, 7 to 7 and 0 to 7 are rejected with 400 naming `from`, and 1 to 8 is answered with 404

### Requirement: Values the reader may not read

A value SHALL NOT be returned in a state, a diff or a comparison when the reader may not read the field, by the rules of the change history (`repository/entity-history`, "Restricted fields in the history"). Such a field SHALL be listed as restricted, without values, or SHALL be left out when the history visibility of the reader's roles for the entity's metatype is "hide".

Trace: REQ-099, REQ-100, REQ-160, owner decision 2026-11-14

#### Scenario: Restricted value in the diff

- **WHEN** a viewer without a read role of the `role-restricted` property `investmentCost` reads the diff of a version that changed it, and no history visibility is configured
- **THEN** the diff lists `investmentCost` as restricted, without old and new value

#### Scenario: Hidden in the complete state

- **WHEN** the history visibility of the only role of the reader is "hide" for the metatype, and the reader reads a state with `investmentCost`
- **THEN** the state does not list `investmentCost`

### Requirement: Override reads of version values

A state, diff or comparison that returns a value the reader can read only through an override role SHALL be audited with one `property-override-access` event per read, with the channel `history`, naming each such property and the entity. A read without such values SHALL write no event.

Trace: REQ-128, REQ-160

#### Scenario: Read through the override role

- **WHEN** a holder of an override role without a read role of `investmentCost` reads the diff of a version that changed it
- **THEN** the diff lists both values, and one `property-override-access` event with the channel `history` names `investmentCost` and the entity

#### Scenario: Read through a read role

- **WHEN** a reader with a read role of `investmentCost` reads the same diff
- **THEN** no `property-override-access` event is written

### Requirement: Immutable versions

The versions of an entity SHALL NOT be changeable or deletable. `PUT` and `DELETE` on a version SHALL be answered with 405 and `Allow: GET`, and SHALL change nothing. A principal without `entity:read` SHALL be rejected with 403 when reading a state or a diff.

Trace: US-100 AC2, AC3, REQ-100

#### Scenario: Delete rejected

- **WHEN** a `DELETE` request is sent to `/entities/{id}/versions/4`
- **THEN** the request is answered with 405, and the state of version 4 is unchanged

#### Scenario: Edit rejected

- **WHEN** a `PUT` request is sent to `/entities/{id}/versions/4`
- **THEN** the request is answered with 405, and the state of version 4 is unchanged

#### Scenario: Without permission

- **WHEN** a person without `entity:read` reads the state of a version
- **THEN** the request is rejected with 403

### Requirement: Diff and state on the page "Change history"

On the page SCR-026, clicking a version SHALL expand its diff. A removed or old value SHALL be shown in red and struck through, a new value in green, and a restricted field with the badge "Restricted" and no values. The expanded version SHALL offer "Show complete state vN", which lists all fields of the state; an empty property SHALL be shown as "—".

Trace: US-099 AC1, AC2, US-100 AC1, REQ-099, REQ-100

#### Scenario: Expand a version

- **WHEN** the reader clicks version 5, which changed `Owner` from `Kurt` to `Michael`
- **THEN** the entry shows "Owner", `Kurt` in red and struck through, and `Michael` in green

#### Scenario: Show the complete state

- **WHEN** the reader chooses "Show complete state v4"
- **THEN** all fields of version 4 are listed, and `Owner` without value is shown as "—"

### Requirement: Compare mode on the page "Change history"

The page SCR-026 SHALL offer "Compare". In this mode the reader SHALL select two versions; the page SHALL then show the comparison of the earlier with the later one in a panel, and selecting a third version SHALL replace the version selected first. Leaving the mode SHALL close the panel.

Trace: US-099 AC3, REQ-099, owner decision 2026-11-14

#### Scenario: Compare two versions on the page

- **WHEN** the reader chooses "Compare" and selects versions 7 and 2
- **THEN** the panel shows the comparison from version 2 to version 7

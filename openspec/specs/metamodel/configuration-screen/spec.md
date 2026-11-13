# metamodel/configuration-screen Specification

## Purpose

Defines the metamodel configuration SCR-030 in the web portal: who sees it and who may change it, how metatypes, property definitions, categories, property mappings, translated labels and re-import settings are shown and edited, and how rejections and collisions are reported.

## Requirements

### Requirement: Access to the metamodel configuration

The web portal SHALL offer the metamodel configuration to persons with `entity:read` or `metamodel:write`. Only `metamodel:write` SHALL show actions to create, change or delete; without it, the configuration SHALL be read-only.

Trace: US-032, REQ-032, ADR-032, owner decision 2026-11-04

#### Scenario: Reader sees the metamodel

- **WHEN** a person with `entity:read` but without `metamodel:write` opens the metamodel configuration
- **THEN** all metatypes and properties are shown and no action to create, change or delete is offered

#### Scenario: Person without access

- **WHEN** a person with neither permission is logged in
- **THEN** the header shows no entry for the metamodel, and opening `/metamodel` shows that access is missing

### Requirement: Metatype list and detail

The configuration SHALL list all metatypes with abbreviation, name and scope; imported ones SHALL name their package. A selected metatype SHALL show its base type and its own and inherited mappings with necessity, valid-from date and state. Built-in and imported content SHALL NOT offer changes.

Trace: US-032, REQ-032 AC5, UC-04 basic flow steps 1–2, US-137

#### Scenario: Built-in metatype is read-only

- **WHEN** an administrator selects the built-in metatype `Application Component`
- **THEN** it is shown with the scope "built-in" and without actions to edit or delete it

#### Scenario: Inherited and future entries

- **WHEN** an administrator selects a metatype with a base type and a mapping entry dated in the future
- **THEN** inherited mappings name the base type, and the future entry is shown with its date as "future"

### Requirement: Editing metatypes

An administrator SHALL create, change and delete organization metatypes in dialogs, with name, abbreviation, sort order, base type and, for a new metatype, whether it is a connection with its allowed sources and targets. A new metatype SHALL appear in the list at once; a rejection SHALL be shown in the dialog with the reason, and nothing SHALL change.

Trace: US-032 AC1–AC2, REQ-032 AC1–AC4, UC-04 A4, US-036 AC4

#### Scenario: Creating SecurityZone

- **WHEN** an administrator creates `SecurityZone` and maps the new enum property `trustLevel` as mandatory
- **THEN** `SecurityZone` appears in the list with the property and can be chosen as primary metatype of a new catalog

#### Scenario: Creating a connection metatype

- **WHEN** an administrator creates `Contains` as connection from `SecurityZone` to `Technology Component`
- **THEN** the list marks `Contains` as connection, and its detail names the allowed source and target

#### Scenario: Name already taken

- **WHEN** an administrator creates a metatype named `application component`
- **THEN** the dialog shows that the name is already taken and no metatype is created

#### Scenario: Deleting a metatype in use

- **WHEN** an administrator deletes an organization metatype with 5 entities
- **THEN** the message names the 5 entities and the metatype still exists

### Requirement: Editing properties and categories

An administrator SHALL create, change and delete organization property definitions with data type, constraints, category and enum values, and property categories, in dialogs. Built-in and imported definitions and categories SHALL be shown read-only.

Trace: US-032, REQ-032 (inputs), UC-04 basic flow step 6

#### Scenario: Enum property with values

- **WHEN** an administrator creates the property `trustLevel` of type enum with the values `low`, `medium` and `high`
- **THEN** the property list shows `trustLevel` with these three values

#### Scenario: Property in use

- **WHEN** an administrator deletes a property definition that is mapped to a metatype
- **THEN** the message says that the property is in use and the property still exists

### Requirement: Editing property mappings

An administrator SHALL map an organization property to any metatype with necessity, valid-from date and default value, add a dated entry to an existing mapping, also a package one, and remove organization entries. Before a removal, the screen SHALL warn that values are deleted; after a new mandatory entry, it SHALL name the marked entities.

Trace: US-032, US-137, REQ-032 AC4, ADR-049

#### Scenario: New mandatory property on a metatype with entities

- **WHEN** an administrator maps a property as mandatory to a metatype with 3 entities without a value
- **THEN** the mapping is shown and the confirmation names 3 marked entities

#### Scenario: Overriding a package mapping

- **WHEN** an administrator adds an entry with necessity `mandatory` and a future date to an imported mapping
- **THEN** the mapping shows the package entry as active and the new entry as future

### Requirement: Translated labels

An administrator SHALL choose a language and see every label key with its name in the corporate language and its current translation, change translations and remove them by clearing the text. The web portal SHALL show the new labels without a page reload.

Trace: US-140, REQ-152, owner decision 2026-11-04

#### Scenario: Translating a metatype name

- **WHEN** an administrator sets the German name "Sicherheitszone" for `SecurityZone` and saves
- **THEN** with German as language, the metatype list shows "Sicherheitszone"

### Requirement: Re-import settings

An administrator SHALL set the global re-import setting for organization mappings and, per metatype, `inherit`, `keep` or `remove`. The metatype SHALL show the setting that applies.

Trace: US-032, ADR-049

#### Scenario: Setting a metatype to remove

- **WHEN** an administrator sets an imported metatype to `remove` while the global setting is `keep`
- **THEN** the metatype shows `remove` as the setting that applies

### Requirement: Warning on a collision

When a write is rejected because someone else changed the element meanwhile, the screen SHALL warn, show the current state and let the administrator overwrite it with the input or discard the input.

Trace: UC-04 A5, REQ-032 (trade-offs), owner decision 2026-11-04

#### Scenario: Overwriting after a warning

- **GIVEN** two administrators have opened the same organization metatype
- **WHEN** the first saves a new abbreviation and the second then saves another one
- **THEN** the second sees a warning with the first abbreviation, and choosing to overwrite saves the second abbreviation

### Requirement: Lock mode in the configuration

Under settings, an administrator SHALL see and switch whether the instance metamodel is locked (`import-only`). While it is locked, the configuration SHALL show a notice that points to the package import and SHALL offer no action that changes metamodel content.

Trace: US-035 AC1, AC3, REQ-035, owner decision 2026-11-04

#### Scenario: Locked metamodel

- **GIVEN** the instance metamodel is locked
- **WHEN** an administrator opens the metamodel configuration
- **THEN** all metatypes are shown with a notice about the lock mode and the package import, and no action to create, change or delete is offered

#### Scenario: Unlocking

- **WHEN** an administrator switches the lock mode off under settings
- **THEN** the actions to create, change and delete are offered again

### Requirement: Changes in the configuration

A metamodel administrator SHALL see a section "Changes" listing the metamodel changes with time, person, kind and element, filterable by kind, person and period. An import SHALL show its batch, and selecting it SHALL list the whole batch.

Trace: US-034 AC1, AC2, REQ-034, owner decision 2026-11-05

#### Scenario: Who changed what

- **GIVEN** Kurt created `SecurityZone` and Lukas deleted another metatype
- **WHEN** Kurt opens the section "Changes"
- **THEN** both changes are listed with name, time and element

#### Scenario: Readers see no changes

- **WHEN** a person holding only `entity:read` opens the metamodel configuration
- **THEN** the section "Changes" is not offered

### Requirement: Visibility shown read-only

The metamodel configuration SHALL mark every property whose visibility mode is not `public` with the badge "Restricted" in the property list. The detail of a property SHALL show its visibility mode, roles and scoping type read-only and refer to the page "Property visibility" for changes.

Trace: US-125 AC1, REQ-125, owner decision 2026-11-07

#### Scenario: Restricted property in the list

- **WHEN** a metamodel administrator opens the property list and `investmentCost` is `role-restricted`
- **THEN** its row shows the badge "Restricted" and the row of a `public` property does not

#### Scenario: Visibility in the detail

- **WHEN** the metamodel administrator opens `investmentCost`
- **THEN** the detail shows "Role-based" with its read and write roles, without a control to change them

### Requirement: Parallel-change setting on the metatype detail

The detail of a metatype SHALL show the switch "Show parallel changes in the baseline" with a short explanation. A principal with `metamodel:write` SHALL change it for every metatype, also built-in and imported ones and while the metamodel is locked; others SHALL see the value read-only.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Switching on a built-in metatype

- **WHEN** an administrator turns on the switch of the built-in metatype `Application Component`
- **THEN** the switch stays on after reloading the configuration

#### Scenario: Read-only without metamodel administration

- **WHEN** a person with `solution-metamodel:write` only opens the instance metatype `Application Component`
- **THEN** the setting is shown as text, without a switch

# Spec Delta

## MODIFIED Requirements

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

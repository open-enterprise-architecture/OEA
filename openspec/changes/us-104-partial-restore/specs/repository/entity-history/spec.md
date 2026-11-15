## ADDED Requirements

### Requirement: Partial restore marker in the history

A version written by a partial restore SHALL carry its restored fields, each with its key and label, besides the version it was restored from. A property the reader may not read SHALL be listed by its label as restricted; a hidden one SHALL be left out. On SCR-026, the version SHALL show "Partially restored from vN" with the labels of the restored fields.

Trace: US-106 AC3, REQ-106 AC3

#### Scenario: Partially restored version in the timeline

- **WHEN** version 6 of an entity was written by a partial restore from version 4 of `description` and `owner`
- **THEN** version 6 in the history carries `restoredFromVersion` 4 and the restored fields `description` and `owner` with their labels

#### Scenario: Marker on the page

- **WHEN** the reader opens the history of that entity
- **THEN** the entry of version 6 shows "Partially restored from v4 (Description, Owner)"

## ADDED Requirements

### Requirement: Restore marker in the history

A version written by a restore SHALL carry the version it was restored from and the reason given, or no reason. Other versions SHALL carry neither. The history SHALL name the current version of the entity, independent of filters and paging.

On the page SCR-026, a restored version SHALL show the badge "Restored from vN" and its reason.

Trace: US-102 AC1, AC2, REQ-102 AC1, AC2

#### Scenario: Restored version in the timeline

- **WHEN** version 6 of an entity was written by a restore to version 3 with the reason "Wrong owner"
- **THEN** version 6 in the history carries `restoredFromVersion` 3 and the reason "Wrong owner", and versions 1 to 5 carry neither

#### Scenario: Current version with a filter

- **WHEN** the history of an entity at version 6 is filtered to a period that contains only version 2
- **THEN** the response lists version 2 and names 6 as the current version

#### Scenario: Badge on the page

- **WHEN** the reader opens the history of an entity whose version 6 was restored from version 3
- **THEN** the entry of version 6 shows "Restored from v3" and the reason

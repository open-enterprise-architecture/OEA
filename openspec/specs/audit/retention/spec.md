# audit/retention Specification

## Purpose

Defines the retention settings of the audit trail: a default retention of seven years on every instance and a floor of one year that no setting can undercut.

## Requirements

### Requirement: Default retention on every instance

Every instance SHALL start with an audit retention of 2555 days (7 years), recorded as the first link of the audit hash chain.

Trace: REQ-083, REQ-174, ADR-033 §4

#### Scenario: Freshly migrated instance

- **WHEN** the retention settings of a freshly migrated instance are read
- **THEN** a retention of 2555 days is in force, effective from the migration, and it is the first record of the audit hash chain

### Requirement: Retention floor

The audit retention SHALL never be stored below 365 days, regardless of the path through which it is changed.

Trace: REQ-083 AC5, REQ-174

#### Scenario: Retention below the floor

- **WHEN** a retention of less than 365 days is stored
- **THEN** the database rejects it and the retention in force is unchanged

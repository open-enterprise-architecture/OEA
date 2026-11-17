## ADDED Requirements

### Requirement: Entities with open issues in the catalog

Every row of a catalog query SHALL carry `openIssues`, true while its entity has an open issue of a solution. The catalog browser (SCR-020) SHALL mark such a row next to its action "History", which leads to the open issues.

Trace: US-153 AC5

#### Scenario: Marked row

- **WHEN** a person queries a catalog containing "Salesforce", which has an open issue
- **THEN** the row of "Salesforce" has `openIssues: true` and shows the marker

#### Scenario: Row without issues

- **WHEN** the issue is resolved
- **THEN** the row has `openIssues: false` and no marker

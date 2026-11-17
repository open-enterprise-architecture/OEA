## ADDED Requirements

### Requirement: Open issues on the history of an entity

The history of an entity SHALL name the open issues of the entity with the field, the kind and the ID and name of their solution. The page "Change history" (SCR-026) SHALL show a notice with the issues and a link to the solution.

Trace: US-153 AC5

#### Scenario: Notice on the history

- **WHEN** a person opens the history of "Salesforce", which has an open issue of the solution "CRM Replacement"
- **THEN** the page shows the notice with the issue and a link to "CRM Replacement"

#### Scenario: No notice

- **WHEN** "Salesforce" has no open issue
- **THEN** the page shows no notice

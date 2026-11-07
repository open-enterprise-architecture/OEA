# Delta for platform/request-limits

## ADDED Requirements

### Requirement: Statement time limit

Every statement the application sends to the database SHALL be cancelled after the configured limit, 60 seconds by default, unless a tighter limit applies, such as the catalog query time limit. The limit SHALL be configurable and SHALL NOT apply to the one-shot migration step.

Trace: REQ-186, SQL review 2026-11-06 finding L1, owner decision 2026-11-07

#### Scenario: Default limit on every connection

- **WHEN** the application opens a database connection
- **THEN** its `statement_timeout` is 60 seconds

#### Scenario: Configured limit

- **WHEN** the application starts with `oea.database.statement-timeout=30s`
- **THEN** every connection of the application has a `statement_timeout` of 30 seconds

#### Scenario: Migration step unaffected

- **WHEN** the migration step applies migrations
- **THEN** no statement limit of the application applies to it

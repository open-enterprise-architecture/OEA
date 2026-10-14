# Spec Delta

## Purpose

Defines the database role model of OEA: roles are provisioned once, schema changes run in a separate migration step, and the running application cannot alter or delete audit data.

## ADDED Requirements

### Requirement: Roles created by provisioning, not by migrations

The database roles of OEA SHALL be created once by a provisioning step with superuser rights. No OEA role SHALL be superuser or able to create roles or databases, and no OEA role SHALL be a member of another OEA role.

Trace: REQ-151, REQ-174, ADR-037

#### Scenario: Role attributes after provisioning

- **WHEN** the roles of a freshly provisioned database are inspected
- **THEN** the roles `oea_owner`, `oea_reporting_owner`, `oea_app`, `oea_audit_cleanup`, `oea_auditor` and `oea_analytics` exist, none has `SUPERUSER`, `CREATEROLE`, `CREATEDB` or `BYPASSRLS`, and none is a member of another of these roles

### Requirement: Separate migration step

Schema migrations SHALL run in a one-shot migration step before the application starts, using the owner roles, and SHALL NOT run inside the application process.

Trace: REQ-156, REQ-151, ADR-033, ADR-037

#### Scenario: Migration step applies all locations

- **WHEN** the migration step runs against a provisioned database
- **THEN** it applies the migrations of `db/migration` and `db/migration-audit` as the owner role and of `db/migration-reporting` as the reporting owner role, and exits successfully

#### Scenario: Application does not migrate

- **WHEN** the application starts against a database whose schema is not up to date
- **THEN** it does not change the schema and fails with a message that the migration step must run first

### Requirement: Runtime role cannot alter audit data

The runtime role of the application SHALL hold only INSERT and SELECT on the audit tables and SHALL NOT own any object in the audit schema.

Trace: REQ-151 AC1, REQ-151 AC4, REQ-174 AC1

#### Scenario: Update rejected

- **WHEN** the runtime role executes `UPDATE audit.events SET event_type = 'manipulated'`
- **THEN** the database rejects the statement with "permission denied" and no record changes

#### Scenario: Destructive statements rejected

- **WHEN** the runtime role executes `DELETE`, `TRUNCATE`, `ALTER TABLE` or `DROP TABLE` on `audit.events`
- **THEN** every statement is rejected, and the owner of the audit tables is a role different from the runtime role

### Requirement: Fail closed on wrong audit privileges

The application SHALL refuse to start outside the development profile if its runtime role holds more than INSERT and SELECT on an audit table or owns an object in the audit schema.

Trace: REQ-151 AC5, ADR-033

#### Scenario: Runtime role with UPDATE privilege

- **WHEN** the application starts outside the development profile and its runtime role holds UPDATE on `audit.events`
- **THEN** startup fails with a message naming the offending privilege

#### Scenario: Runtime role owns an audit object

- **WHEN** the application starts outside the development profile and its runtime role owns an object in schema `audit`
- **THEN** startup fails with a message naming the offending ownership

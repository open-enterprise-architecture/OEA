# Spec: openspec platform/database-roles – Runtime role cannot alter audit data
# Source material: requirements/tests/REQ-151-audit-schema-separation.feature (AC1)

@REQ-151 @REQ-174 @ADR-037
Feature: Runtime role cannot alter audit data

  The runtime role of the application holds only INSERT and SELECT on the audit
  tables and owns no object in the audit schema.

  Background:
    Given the application is connected to the database with its runtime role
    And an audit event exists

  @AC1
  Scenario: Update rejected
    When the runtime role executes "UPDATE audit.events SET event_type = 'manipulated'"
    Then the database rejects the statement with "permission denied"
    And no audit record has changed

  Scenario Outline: Destructive statements rejected
    When the runtime role executes "<statement>"
    Then the database rejects the statement with "<error>"
    And no audit record has changed
    And the owner of "audit.events" is a role different from the runtime role

    Examples:
      | statement                                       | error                       |
      | DELETE FROM audit.events                        | permission denied           |
      | TRUNCATE audit.events                           | permission denied           |
      | ALTER TABLE audit.events ADD COLUMN forged TEXT | must be owner of table      |
      | DROP TABLE audit.events                         | must be owner of table      |

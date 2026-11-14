# Spec: openspec metamodel/history-visibility
# Source material: requirements/req/REQ-098-version-history-timeline.md, REQ-161; owner decision 2026-11-14

@US-098 @UC-14 @metamodel
Feature: History visibility per role and metatype

  Security administration decides per role and metatype whether a change to a
  field the reader may not read appears as "Restricted" or is left out of the
  history. No setting ever shows the value.

  Background:
    Given a running repository instance with a metamodel administrator
    And the history persons "Michael" and "Kurt"

  # Requirement: History visibility per role and metatype

  @REQ-098
  Scenario: Nothing configured
    When the security administrator reads the history visibility
    Then the history visibility has no entries

  @REQ-098
  Scenario: More open setting wins
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed "investmentCost"
    And the history visibility of "viewer" for "Application Component" is "hide"
    When the person holding "viewer" and "auditor" reads the history of "CRM System"
    Then version 2 lists "investmentCost (restricted)"

  @REQ-098
  Scenario: All roles hide
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed "investmentCost"
    And the history visibility of "viewer" for "Application Component" is "hide"
    And the history visibility of "auditor" for "Application Component" is "hide"
    When the person holding "viewer" and "auditor" reads the history of "CRM System"
    Then version 2 lists ""

  # Requirement: Scope of a history visibility setting

  @REQ-098
  Scenario: No inheritance
    Given the history entity "Billing" of the subtype "History Business Application" of "Application Component" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "Billing" changed "investmentCost"
    And the history visibility of "viewer" for "Application Component" is "hide"
    When the viewer reads the history of "Billing"
    Then version 2 lists "investmentCost (restricted)"

  @REQ-098 @ADR-049
  Scenario: Re-import keeps the settings
    Given the history visibility of "viewer" for "Application Component" is "hide"
    When the metamodel administrator imports the starter package again
    Then the history visibility lists "viewer" for "Application Component" as "hide"

  # Requirement: Reading and changing the history visibility

  @REQ-161
  Scenario: Hide for a role
    When the security administrator sets "viewer" to "hide" for "Application Component"
    Then the history visibility lists "viewer" for "Application Component" as "hide"

  @REQ-161
  Scenario: Back to the default
    Given the history visibility of "viewer" for "Application Component" is "hide"
    When the security administrator sets "viewer" to "show-restricted" for "Application Component"
    Then the history visibility has no entries

  @REQ-161
  Scenario: Unknown role among valid entries
    When the security administrator sets "auditor" to "hide" and "no-such-role" to "hide" for "Application Component"
    Then the history visibility change is rejected with 422 naming "entries[1].roleId"
    And the history visibility has no entries

  @REQ-161
  Scenario Outline: Entry limits
    When the security administrator sends a history visibility change with <count> entries
    Then the history visibility change is answered with <status>

    Examples:
      | count | status |
      | 500   | 200    |
      | 501   | 400    |
      | 0     | 400    |

  @REQ-161 @ADR-032
  Scenario: Metamodel administrator
    When the metamodel administrator sets "viewer" to "hide" for "Application Component"
    Then the history visibility change is answered with 403
    And the history visibility has no entries

  # Requirement: Audit of history visibility changes

  @REQ-161 @ADR-033
  Scenario: Change audited
    When the security administrator sets "viewer" to "hide" for "Application Component"
    Then a "visibility.history-changed" event names "viewer", "Application Component", "show-restricted" and "hide"

  @REQ-161 @ADR-033
  Scenario: Unchanged entry
    Given the history visibility of "viewer" for "Application Component" is "hide"
    When the security administrator sets "viewer" to "hide" for "Application Component"
    Then no further "visibility.history-changed" event is written

  @REQ-161 @ADR-032
  Scenario: Self-benefiting change
    Given the history visibility of "security-admin" for "Application Component" is "hide"
    When the security administrator sets "security-admin" to "show-restricted" for "Application Component"
    Then the last "visibility.history-changed" event is flagged as self-benefiting

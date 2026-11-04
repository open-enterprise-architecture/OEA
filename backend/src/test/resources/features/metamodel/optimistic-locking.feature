# Spec: openspec metamodel/optimistic-locking
# Source material: requirements/use-cases/UC-04-configure-metamodel.md (A5), REQ-032

@US-032 @UC-04 @metamodel
Feature: Optimistic locking of metamodel elements

  Metatypes, property definitions and property categories carry a version that
  grows with every change. A write names the version it is based on with
  If-Match; a write on an older version is rejected with 412, so two
  administrators do not overwrite each other unnoticed.

  Background:
    Given a running repository instance for optimistic locking

  Scenario: Version after a change
    Given an organization metatype in version 1
    When the administrator changes its abbreviation with If-Match "1"
    Then the response contains version 2 and the ETag "2"
    And the metatype is read in version 2 with the ETag "2"

  Scenario: A mapping changes the metatype version
    Given an organization metatype in version 1
    When the administrator maps a property to the metatype with If-Match "1"
    Then the metatype is read in version 2 with the ETag "2"

  Scenario: An enum value changes the definition version
    Given an organization enum property in version 1
    When the administrator adds an enum value with If-Match "1"
    Then the property definition has version 2

  Scenario: Two administrators change the same metatype
    Given an organization metatype in version 1
    And two administrators have read the metatype
    When the first saves a new name with If-Match "1"
    And the second saves another name with If-Match "1"
    Then the first write succeeded with version 2
    And the second write is rejected with 412 "stale-version"
    And the metatype keeps the first name

  Scenario: Deleting with a stale version
    Given an organization enum property in version 1
    And the administrator has added an enum value
    When the administrator deletes the property definition with If-Match "1"
    Then the write is rejected with 412 "stale-version"
    And the property definition still exists

  Scenario: Write without If-Match
    Given an organization property category in version 1
    When an API client renames the category without If-Match
    Then the change succeeds with version 2

# Derived from: requirements/req/req-097-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-13
Feature: REQ-097 – Navigation tree multiple placement of objects

  The same repository object (entity, diagram or catalog) MUST be allowed
  to be referenced simultaneously as an item in several folders.
  Each folder entry is an independent TreeNodeItem with its own
  `referenceId`; no data duplication of the object.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Multiple placement – the entity "SAP ERP" is attached as an item to folders "Finance" and "Cloud-Migration"...
    Given the precondition according to the requirement is met
    When  the entity "SAP ERP" is attached as an item to folders "Finance" and "Cloud-Migration"
    Then  two independent TreeNodeItems with the same `referenceId` exist; the entity itself is unchanged

  @AC2
  Scenario: AC2 – Independent removal – the item "SAP ERP" is removed from folder "Finance"
    Given the precondition according to the requirement is met
    When  the item "SAP ERP" is removed from folder "Finance"
    Then  the entry in "Finance" is deleted; the entry in "Cloud-Migration" remains valid

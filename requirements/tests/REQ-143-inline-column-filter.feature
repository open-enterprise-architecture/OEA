# Derived from: requirements/req/req-143-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-143 – Inline column filter in catalog table (AND-combined)

  The system MUST offer a filter input row directly below the
  column headers in the catalog table view. Each column MUST have
  its own input field that enables a quick filter on the
  respective attribute.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Inline filter sets result – catalog "Application Inventory" with 47 ApplicationComponent entries
    Given catalog "Application Inventory" with 47 ApplicationComponent entries
    When  user types "Catalog" into the inline filter field of the "Name" column
    Then  table shows only entities whose name contains "Catalog" (contains); status bar updates count

  @AC2
  Scenario: AC2 – AND combination of multiple inline filters – user sets inline filter Name="Service" AND Layer="Application"
    Given the precondition according to the requirement is met
    When  user sets inline filter Name="Service" AND Layer="Application"
    Then  only entities that satisfy BOTH conditions appear in the table

  @AC3
  Scenario: AC3 – Combination inline filter + SavedFilter – SavedFilter "Active systems only" is active
    Given SavedFilter "Active systems only" is active
    When  inline filter Layer="Application" is additionally set
    Then  result = active AND layer=Application (all filters AND-combined)

  @AC4
  Scenario: AC4 – Inline filter not persistent – user sets inline filter, leaves the catalog and opens it again
    Given the precondition according to the requirement is met
    When  user sets inline filter, leaves the catalog and opens it again
    Then  inline filters are cleared; SavedFilters remain active (since persisted)

  @AC5
  Scenario: AC5 – No API call to save – inline filter is set
    Given the precondition according to the requirement is met
    When  inline filter is set
    Then  no `POST /api/v1/catalogs/{id}/filters` — only GET query with inline parameter

  @AC6
  Scenario: AC6 – OR hint – user wants to filter Name="Gateway" OR Name="Service"
    Given user wants to filter Name="Gateway" OR Name="Service"
    When the described action is performed
    Then  inline filter does not support OR; user is pointed to the "+ Filter" dialog via tooltip/hint

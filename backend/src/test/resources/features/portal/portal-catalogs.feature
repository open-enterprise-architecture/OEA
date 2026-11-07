# Spec: openspec portal/portal-catalogs
# Source material: issue #67; REQ-160, REQ-124; UC-06; ADR-032

@UC-06 @portal
Feature: Shared catalogs in the portal

  Readers of the web portal see the shared catalogs read-only. A reader
  with only the role "portal-reader" lists them and queries one with the
  catalog's configured columns and default sort; personal catalogs never
  appear in the portal.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the portal reader and a person without role are logged in through the IdP

  Scenario: Shared catalogs only
    Given the enterprise architect has created the catalog "Portal Inventory" with scope "instance"
    And the enterprise architect has created the catalog "Portal Candidates" with scope "personal"
    When the portal reader lists the portal catalogs
    Then the portal list contains "Portal Inventory" and not "Portal Candidates"

  Scenario: Own personal catalog not in the portal
    Given the enterprise architect has created the catalog "Portal Own Candidates" with scope "personal"
    When the enterprise architect lists the portal catalogs
    Then the portal list does not contain "Portal Own Candidates"

  @REQ-160
  Scenario: Rows of a shared catalog
    Given the applications "Portal CRM", "Portal ERP" and "Portal Intranet" exist with the status "active"
    And the enterprise architect has created the catalog "Portal Applications" with scope "instance"
    When the portal reader queries the portal catalog "Portal Applications"
    Then the portal result has the columns "Name" and "Status"
    And the portal result equals the catalog query of the enterprise architect

  Scenario: Sorting and paging
    Given 15 applications with the prefix "Portal Paged" exist
    And the enterprise architect has created the catalog "Portal Paged Inventory" with scope "instance"
    When the portal reader queries the portal catalog "Portal Paged Inventory" with page size 10 and sort "name:desc"
    Then the portal result has 10 rows sorted by name descending

  Scenario: Personal catalog
    Given the enterprise architect has created the catalog "Portal Private" with scope "personal"
    When the portal reader queries the portal catalog "Portal Private"
    Then the portal query is answered with 404
    And the portal query of the enterprise architect for "Portal Private" is answered with 404

  Scenario: Unknown sort column
    Given the enterprise architect has created the catalog "Portal Sorted" with scope "instance"
    When the portal reader queries the portal catalog "Portal Sorted" with page size 10 and sort "unknown:asc"
    Then the portal query is rejected with 400 naming "sort"

  Scenario: Without portal permission
    When the person without role lists the portal catalogs
    Then the portal query is answered with 403

# Derived from: requirements/req/req-064-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-08
Feature: REQ-064 – GDPR catalog filter on data flows (personalDataCategories)

  The system MUST make it possible to restrict, in a catalog (UC-06),
  all ArchitectureEntities of type `data-flow` with a filter on
  `personalDataCategories ≠ empty` and to export the result as CSV;
  this filter MUST also be combinable with the catalog filter
  operators from REQ-047.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Filter personalDataCategories ≠ empty – DataFlow id=5 with `personalD...
    Given DataFlow id=5 with `personalDataCategories="Name,Address,Email"` and DataFlow id=7 without personalDataCategories
    When  Lukas opens catalog with filter `entityTypeId=data-flow` + `personalDataCategories ≠ empty`
    Then  only id=5 appears; id=7 does not

  @AC2
  Scenario: AC2 – Combined filter – Lukas additionally adds filter `protocol=JDBC`
    Given the precondition according to the requirement is met
    When  Lukas additionally adds filter `protocol=JDBC`
    Then  only DataFlows with JDBC protocol AND non-empty personalDataCategories appear

  @AC3
  Scenario: AC3 – CSV export – catalog shows 3 DataFlows after filter
    Given catalog shows 3 DataFlows after filter
    When  Lukas clicks "Export as CSV"
    Then  CSV download with 3 rows + header; fields as described above; encoding UTF-8

  @AC4
  Scenario: AC4 – Empty result – no DataFlows have personalDataCategories filled
    Given no DataFlows have personalDataCategories filled
    When  filter applied
    Then  empty table; no error; export returns only header row

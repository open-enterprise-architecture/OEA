# Spec: openspec catalog/catalog-definition
# Source material: requirements/user-stories/US-046-create-catalog.md, REQ-043; ADR-032

@US-046 @UC-06 @catalog
Feature: Catalog definition

  Catalogs are named table views of the repository. Enterprise architects
  create shared catalogs for everybody; every reader can create personal
  catalogs that only they see.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP

  @US-046 @AC1 @REQ-043
  Scenario: Catalog created
    When the enterprise architect creates the catalog "Application Inventory" for "Application Component" with scope "instance" and join mode "aggregate"
    Then the catalog is created with empty columns, joins, saved filters and saved views
    And the catalog overview of the enterprise architect lists "Application Inventory"

  @REQ-043
  Scenario: Default join mode omitted
    When the enterprise architect creates the catalog "Without Join Mode" for "Application Component" with scope "instance"
    Then the catalog is created with the default join mode "aggregate"

  @US-046 @AC2 @REQ-043
  Scenario: Same name again
    Given the enterprise architect has created the catalog "Duplicate Inventory" with scope "instance"
    When the enterprise architect creates the catalog " duplicate inventory " for "Application Component" with scope "personal"
    Then the creation is rejected with 422 and the message "A catalog with this name already exists"
    And exactly one catalog is named "Duplicate Inventory" ignoring case

  @REQ-043 @AC3
  Scenario: Unknown primary metatype
    When the enterprise architect creates the catalog "Unknown Type" for an unknown metatype
    Then the creation is rejected with 422 naming the field "primaryMetatypeId"

  @REQ-043
  Scenario: Name too long
    When the enterprise architect creates a catalog with a name of 256 characters
    Then the creation is rejected with 422 naming the field "name"

  @US-046 @AC4 @REQ-043 @AC5 @ADR-032
  Scenario: Viewer creates a shared catalog
    When the viewer creates the catalog "Shared by Viewer" for "Application Component" with scope "instance"
    Then the creation is rejected with 403
    And no catalog is named "Shared by Viewer"

  @US-046 @AC4 @ADR-032
  Scenario: Viewer creates a personal catalog
    When the viewer creates the catalog "Personal of Viewer" for "Application Component" with scope "personal"
    Then the catalog is created

  @US-046 @AC3 @REQ-043 @AC4
  Scenario: Personal catalog of another person
    Given the enterprise architect has created the catalog "Architect's Processes" with scope "personal"
    Then the catalog overview of the viewer does not list "Architect's Processes"
    And the viewer receives 404 for the catalog "Architect's Processes"

  @US-046 @AC3
  Scenario: Own personal catalog
    Given the enterprise architect has created the catalog "Order B Personal" with scope "personal"
    And the enterprise architect has created the catalog "Order C Shared" with scope "instance"
    And the enterprise architect has created the catalog "order a shared" with scope "instance"
    Then the catalog overview of the enterprise architect lists "order a shared", "Order B Personal" and "Order C Shared" in this order

  @ADR-033
  Scenario: Created catalog audited
    When the enterprise architect creates the catalog "Audited Inventory" for "Application Component" with scope "instance"
    Then one "catalog.created" audit event names "Audited Inventory"
    And the audit chain is intact

  @ADR-033
  Scenario: Rejected creation not audited
    Given the enterprise architect has created the catalog "Audit Collision" with scope "instance"
    When the enterprise architect creates the catalog "Audit Collision" for "Application Component" with scope "instance"
    Then the creation is rejected with 422 and the message "A catalog with this name already exists"
    And one "catalog.created" audit event names "Audit Collision"

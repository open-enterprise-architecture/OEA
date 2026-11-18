# Spec: openspec dashboard/widgets
# Source material: requirements/user-stories/US-058-configure-table-widget.md, US-059-add-text-widget.md, US-060-adjust-widget-layout.md; REQ-052, REQ-054, REQ-055, REQ-057; ADR-032

@US-058 @US-059 @US-060 @UC-07 @dashboard
Feature: Dashboard widgets

  Text and table widgets sit in a 12-column grid. Their data is computed
  with the permissions of the reader.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP

  @REQ-052
  Scenario: Type cannot change
    Given the enterprise architect has created the catalog "Dash Type Catalog" with scope "instance"
    And the enterprise architect has created the dashboard "Dash Types" with scope "instance"
    And the enterprise architect adds the text widget "Type Notes" with the content "x" to the dashboard "Dash Types"
    When the enterprise architect changes the widget "Type Notes" of the dashboard "Dash Types" into a table widget for the catalog "Dash Type Catalog"
    Then the dashboard request is rejected with 422 naming "type"

  @REQ-052
  Scenario: Widgets at the limit
    Given the enterprise architect has created the dashboard "Dash Fifty" with scope "instance"
    And the dashboard "Dash Fifty" has 49 widgets
    When the enterprise architect adds the text widget "Fiftieth" with the content "x" to the dashboard "Dash Fifty"
    Then the dashboard request is accepted
    When the enterprise architect adds the text widget "Fifty-first" with the content "x" to the dashboard "Dash Fifty"
    Then the dashboard request is rejected with 422

  @REQ-052
  Scenario Outline: Types that are not supported yet
    Given the enterprise architect has created the dashboard "Dash Later <type>" with scope "instance"
    When the enterprise architect adds a widget of type "<type>" to the dashboard "Dash Later <type>"
    Then the dashboard request is rejected with 422 naming "type"

    Examples:
      | type  |
      | kpi   |
      | chart |

  @US-059 @AC1 @AC3
  Scenario: Methodological notes
    Given the enterprise architect has created the dashboard "Dash Notes" with scope "instance"
    When the enterprise architect adds the text widget "Methodological Notes" with the content "**Method:** see [the guide](https://example.org/guide)" to the dashboard "Dash Notes"
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Notes"
    Then the widget "Methodological Notes" has the status "static"

  @US-059 @AC4
  Scenario Outline: Content at the limits
    Given the enterprise architect has created the dashboard "Dash Content <length>" with scope "instance"
    When the enterprise architect adds the text widget "Content" with a content of <length> characters to the dashboard "Dash Content <length>"
    Then <outcome>

    Examples:
      | length | outcome                                             |
      | 0      | the dashboard request is accepted                             |
      | 10000  | the dashboard request is accepted                             |
      | 10001  | the dashboard request is rejected with 422 naming "content"   |

  @US-058 @AC1 @REQ-054 @dashboard-entities
  Scenario: Catalog with a saved view
    Given the repository contains no applications
    And the shared catalog "Dash Application Inventory" has 47 entities and the saved view "Compact view"
    And the enterprise architect has created the dashboard "Dash Views" with scope "instance"
    When the enterprise architect adds the table widget "Application Overview" for the catalog "Dash Application Inventory" with the saved view "Compact view" and 10 rows per page to the dashboard "Dash Views"
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Views"
    Then the widget "Application Overview" has the status "ok"
    And the widget "Application Overview" shows 10 rows of 47 entities in 5 pages
    And the widget "Application Overview" shows only the column "name"

  @US-058 @AC1 @AC2 @REQ-055 @dashboard-entities
  Scenario: Second page of a table
    Given the repository contains no applications
    And the shared catalog "Dash Paged Inventory" has 47 entities and the saved view "Compact view"
    And the enterprise architect has created the dashboard "Dash Pages" with scope "instance"
    And the enterprise architect adds the table widget "Paged Overview" for the catalog "Dash Paged Inventory" with 10 rows per page to the dashboard "Dash Pages"
    When the viewer reads page 2 of the widget "Paged Overview" of the dashboard "Dash Pages"
    Then the widget shows the entities "Dash App 11" to "Dash App 20", page 2 of 5

  @US-058 @AC2
  Scenario: Without a saved view
    Given the enterprise architect has created the catalog "Dash Plain Catalog" with scope "instance"
    And the enterprise architect has created the dashboard "Dash Plain" with scope "instance"
    When the enterprise architect adds the table widget "Plain Overview" for the catalog "Dash Plain Catalog" to the dashboard "Dash Plain"
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Plain"
    Then the widget "Plain Overview" has the status "ok"

  @US-058 @AC3
  Scenario: Another person's personal catalog
    Given the viewer has created the personal catalog "Dash Viewer Private"
    And the enterprise architect has created the dashboard "Dash Foreign" with scope "instance"
    When the enterprise architect adds the table widget "Foreign" for the catalog "Dash Viewer Private" to the dashboard "Dash Foreign"
    Then the dashboard request is rejected with 403

  @US-058 @AC3
  Scenario: Own personal catalog on a shared dashboard
    Given the enterprise architect has created the personal catalog "Dash Architect Private"
    And the enterprise architect has created the dashboard "Dash Shared Own" with scope "instance"
    When the enterprise architect adds the table widget "Own" for the catalog "Dash Architect Private" to the dashboard "Dash Shared Own"
    Then the dashboard request is rejected with 422 naming "catalogId"

  @US-058 @AC2
  Scenario Outline: Page size at the limits
    Given the enterprise architect has created the catalog "Dash Size Catalog <size>" with scope "instance"
    And the enterprise architect has created the dashboard "Dash Size <size>" with scope "instance"
    When the enterprise architect adds the table widget "Sized" for the catalog "Dash Size Catalog <size>" with <size> rows per page to the dashboard "Dash Size <size>"
    Then <outcome>

    Examples:
      | size | outcome                                              |
      | 5    | the dashboard request is accepted                              |
      | 100  | the dashboard request is accepted                              |
      | 4    | the dashboard request is rejected with 422 naming "pageSize"   |
      | 101  | the dashboard request is rejected with 422 naming "pageSize"   |

  @US-058 @AC5
  Scenario: Wrong data source
    Given the enterprise architect has created the catalog "Dash Source Catalog" with scope "instance"
    And the enterprise architect has created the dashboard "Dash Source" with scope "instance"
    When the enterprise architect adds the table widget "Aggregation" for the catalog "Dash Source Catalog" with a property aggregation as data source to the dashboard "Dash Source"
    Then the dashboard request is rejected with 422 and the message "Table widget requires CatalogQuery DataSource"

  @US-058 @AC4 @REQ-055
  Scenario: Deleted catalog
    Given the enterprise architect has created the catalog "Dash Doomed Inventory" with scope "instance"
    And the enterprise architect has created the dashboard "Dash Deleted" with scope "instance"
    And the enterprise architect adds the text widget "Still Fine" with the content "x" to the dashboard "Dash Deleted"
    And the enterprise architect adds the table widget "Doomed" for the catalog "Dash Doomed Inventory" to the dashboard "Dash Deleted"
    And the catalog "Dash Doomed Inventory" is deleted
    When the enterprise architect reads the data of the dashboard "Dash Deleted"
    Then the widget "Doomed" shows the error "catalog-not-found"
    And the widget "Still Fine" has the status "static"

  @REQ-056 @REQ-055
  Scenario: Restricted values
    Given the catalog "Dash Restricted Inventory" with a column on "Dash Restricted Cost", readable only by "enterprise-architect", and an application "Dash Costly Application" with the value "100"
    And the enterprise architect has created the dashboard "Dash Restricted" with scope "instance"
    And the enterprise architect adds the table widget "Costs" for the catalog "Dash Restricted Inventory" to the dashboard "Dash Restricted"
    When the viewer reads the data of the dashboard "Dash Restricted"
    Then the widget "Costs" shows a column that is not readable

  @US-060 @AC1 @REQ-057
  Scenario: Position two widgets
    Given the enterprise architect has created the dashboard "Dash Layout" with scope "instance"
    And the enterprise architect adds the text widget "Left" with the content "x" to the dashboard "Dash Layout"
    And the enterprise architect adds the text widget "Right" with the content "y" to the dashboard "Dash Layout"
    When the enterprise architect places widgets of the dashboard "Dash Layout" in one request:
      | title | col | row | width | height |
      | Left  | 1   | 1   | 3     | 1      |
      | Right | 4   | 1   | 9     | 3      |
    Then the dashboard request is accepted
    And the widget "Left" of the dashboard "Dash Layout" is at column 1, row 1, width 3 and height 1
    And the widget "Right" of the dashboard "Dash Layout" is at column 4, row 1, width 9 and height 3

  @US-060 @AC2 @REQ-057
  Scenario Outline: Width at the grid border
    Given the enterprise architect has created the dashboard "Dash Border <width>" with scope "instance"
    And the enterprise architect adds the text widget "Border" with the content "x" to the dashboard "Dash Border <width>"
    When the enterprise architect places the widget "Border" of the dashboard "Dash Border <width>" at column 5, row 1, width <width> and height 1
    Then <outcome>

    Examples:
      | width | outcome                                                                      |
      | 8     | the dashboard request is accepted                                                      |
      | 9     | the dashboard request is rejected with 422 and the message "Widget exceeds the grid width" |

  @US-060 @AC3 @REQ-057
  Scenario Outline: Height at the limits
    Given the enterprise architect has created the dashboard "Dash Height <height>" with scope "instance"
    And the enterprise architect adds the text widget "Height" with the content "x" to the dashboard "Dash Height <height>"
    When the enterprise architect places the widget "Height" of the dashboard "Dash Height <height>" at column 1, row 1, width 3 and height <height>
    Then <outcome>

    Examples:
      | height | outcome                                              |
      | 6      | the dashboard request is accepted                              |
      | 7      | the dashboard request is rejected with 422 naming "height"     |

  @US-060 @AC4 @REQ-057
  Scenario: Overlap
    Given the enterprise architect has created the dashboard "Dash Overlap" with scope "instance"
    And the enterprise architect adds the text widget "Over A" with the content "x" to the dashboard "Dash Overlap"
    And the enterprise architect adds the text widget "Over B" with the content "y" to the dashboard "Dash Overlap"
    When the enterprise architect places widgets of the dashboard "Dash Overlap" in one request:
      | title  | col | row | width | height |
      | Over A | 1   | 1   | 6     | 2      |
      | Over B | 3   | 1   | 6     | 2      |
    Then the dashboard request is accepted

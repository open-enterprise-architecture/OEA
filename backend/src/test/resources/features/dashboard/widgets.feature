# Spec: openspec dashboard/widgets
# Source material: requirements/user-stories/US-058-configure-table-widget.md, US-059-add-text-widget.md, US-060-adjust-widget-layout.md; REQ-052, REQ-054, REQ-055, REQ-057; ADR-032

@US-056 @US-057 @US-058 @US-059 @US-060 @UC-07 @dashboard
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

  @REQ-052 @US-056 @US-057 @dashboard-aggregation
  Scenario: KPI and chart accepted
    Given the element type "Dash Accepted Element"
    And the enterprise architect has created the dashboard "Dash Accepted" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Accepted":
      | type | title    | metatype              | function |
      | kpi  | Accepted | Dash Accepted Element | count    |
    Then the dashboard request is accepted
    When the enterprise architect adds this widget to the dashboard "Dash Accepted":
      | type  | title         | metatype              | function | groupBy  | chartType |
      | chart | Accepted Plot | Dash Accepted Element | count    | metatype | bar       |
    Then the dashboard request is accepted

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

  # ---- property aggregation, KPI and chart widgets (us-056-kpi-and-chart-widgets)

  @US-056 @AC1 @AC2 @REQ-053 @dashboard-aggregation
  Scenario: Sum of a property
    Given the element type "Dash Sum Application"
    And the property "Dash Sum Investment" of type "integer" is mapped to the element type "Dash Sum Application"
    And the entity "Dash Sum 1" of the element type "Dash Sum Application" with the "Dash Sum Investment" "5000000"
    And the entity "Dash Sum 2" of the element type "Dash Sum Application" with the "Dash Sum Investment" "12000000"
    And the entity "Dash Sum 3" of the element type "Dash Sum Application" with the "Dash Sum Investment" "25000000"
    And the entity "Dash Sum 4" of the element type "Dash Sum Application" with the "Dash Sum Investment" "100000000"
    And the entity "Dash Sum 4" is soft-deleted
    And the enterprise architect has created the dashboard "Dash Sum" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Sum":
      | type | title            | metatype             | property            | function | unit |
      | kpi  | Total Investment | Dash Sum Application | Dash Sum Investment | sum      | CHF  |
    Then the dashboard request is accepted
    And the widget "Total Investment" of the dashboard "Dash Sum" has the unit "CHF"
    When the viewer reads the data of the dashboard "Dash Sum"
    Then the widget "Total Investment" has the value 42000000

  @US-056 @AC3 @REQ-053 @dashboard-aggregation
  Scenario: Count
    Given the element type "Dash Count Application"
    And the element type "Dash Count Cloud Application" with the base type "Dash Count Application"
    And 14 entities of the element type "Dash Count Application" named "Dash Count App "
    And the entity "Dash Count Cloud" of the element type "Dash Count Cloud Application"
    And the enterprise architect has created the dashboard "Dash Count" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Count":
      | type | title    | metatype               | function |
      | kpi  | Programs | Dash Count Application | count    |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Count"
    Then the widget "Programs" has the value 15

  @US-056 @REQ-053 @dashboard-aggregation
  Scenario: Sum of a text property
    Given the element type "Dash Text Application"
    And the property "Dash Text Name" of type "string" is mapped to the element type "Dash Text Application"
    And the enterprise architect has created the dashboard "Dash Text" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Text":
      | type | title | metatype              | property       | function |
      | kpi  | Names | Dash Text Application | Dash Text Name | sum      |
    Then the dashboard request is rejected with 422 and the message "sum requires an integer property"

  @US-056 @REQ-053 @dashboard-aggregation
  Scenario: Unknown or unmapped property
    Given the element type "Dash Unmapped Application"
    And the element type "Dash Unmapped Other"
    And the property "Dash Unmapped Investment" of type "integer" is mapped to the element type "Dash Unmapped Other"
    And the enterprise architect has created the dashboard "Dash Unmapped" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Unmapped":
      | type | title      | metatype                  | property                 | function |
      | kpi  | Investment | Dash Unmapped Application | Dash Unmapped Investment | sum      |
    Then the dashboard request is rejected with 422 naming "propertyDefId"

  @US-056 @AC5 @dashboard-aggregation
  Scenario: Target value missing
    Given the element type "Dash Target Application"
    And the enterprise architect has created the dashboard "Dash Target Missing" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Target Missing":
      | type | title  | metatype                | function | comparison |
      | kpi  | Target | Dash Target Application | count    | vs-target  |
    Then the dashboard request is rejected with 422 naming "targetValue"

  @US-056 @AC5 @dashboard-aggregation
  Scenario: Below the target
    Given the element type "Dash Below Application"
    And the property "Dash Below Investment" of type "integer" is mapped to the element type "Dash Below Application"
    And the entity "Dash Below 1" of the element type "Dash Below Application" with the "Dash Below Investment" "42"
    And the enterprise architect has created the dashboard "Dash Below" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Below":
      | type | title      | metatype              | property              | function | comparison | target |
      | kpi  | Investment | Dash Below Application | Dash Below Investment | sum      | vs-target  | 50     |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Below"
    Then the widget "Investment" has the value 42
    And the widget "Investment" has the target 50

  @US-056 @AC4 @dashboard-aggregation
  Scenario: No data
    Given the element type "Dash Empty Application"
    And the property "Dash Empty Investment" of type "integer" is mapped to the element type "Dash Empty Application"
    And the entity "Dash Empty 1" of the element type "Dash Empty Application"
    And the enterprise architect has created the dashboard "Dash Empty" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Empty":
      | type | title      | metatype               | property               | function |
      | kpi  | Investment | Dash Empty Application | Dash Empty Investment  | sum      |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Empty"
    Then the widget "Investment" has no value
    And the widget "Investment" has no error

  @US-056 @AC1 @dashboard-aggregation
  Scenario Outline: Unit at the limit
    Given the element type "Dash Unit Application <length>"
    And the enterprise architect has created the dashboard "Dash Unit <length>" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Unit <length>":
      | type | title | metatype                       | function | unit   |
      | kpi  | Unit  | Dash Unit Application <length> | count    | <unit> |
    Then <outcome>

    Examples:
      | length | unit                  | outcome                                              |
      | 20     | 12345678901234567890  | the dashboard request is accepted                    |
      | 21     | 123456789012345678901 | the dashboard request is rejected with 422 naming "unit" |

  @US-057 @AC1 @AC2 @dashboard-aggregation
  Scenario: Investment per plateau as bars
    Given the element type "Dash Plateau Application"
    And the property "Dash Plateau Investment" of type "integer" is mapped to the element type "Dash Plateau Application"
    And the property "Dash Plateau" of type "enum" is mapped to the element type "Dash Plateau Application"
    And the property "Dash Plateau" has the enumeration values "Dash P1", "Dash P2" and "Dash P3"
    And the entity "Dash Plateau 1" of the element type "Dash Plateau Application" with the "Dash Plateau Investment" "5000000"
    And the entity "Dash Plateau 1" has the "Dash Plateau" "Dash P1"
    And the entity "Dash Plateau 2" of the element type "Dash Plateau Application" with the "Dash Plateau Investment" "12000000"
    And the entity "Dash Plateau 2" has the "Dash Plateau" "Dash P2"
    And the entity "Dash Plateau 3" of the element type "Dash Plateau Application" with the "Dash Plateau Investment" "25000000"
    And the entity "Dash Plateau 3" has the "Dash Plateau" "Dash P3"
    And the enterprise architect has created the dashboard "Dash Plateaus" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Plateaus":
      | type  | title             | metatype                 | property                | function | groupBy               | chartType |
      | chart | Per Plateau       | Dash Plateau Application | Dash Plateau Investment | sum      | property:Dash Plateau | bar       |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Plateaus"
    Then the widget "Per Plateau" shows the groups:
      | label   | value    |
      | Dash P3 | 25000000 |
      | Dash P2 | 12000000 |
      | Dash P1 | 5000000  |

  @US-057 @AC2 @AC4 @dashboard-aggregation
  Scenario: Entities per layer as pie
    Given the element type "Dash Layer Root"
    And the element type "Dash Layer Process" with the base type "Dash Layer Root" in the layer "Dash Business"
    And the element type "Dash Layer Component" with the base type "Dash Layer Root" in the layer "Dash Application"
    And the element type "Dash Layer Node" with the base type "Dash Layer Root" in the layer "Dash Technology"
    And 20 entities of the element type "Dash Layer Process" named "Dash Layer Process "
    And 20 entities of the element type "Dash Layer Component" named "Dash Layer Component "
    And 10 entities of the element type "Dash Layer Node" named "Dash Layer Node "
    And the enterprise architect has created the dashboard "Dash Layers" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Layers":
      | type  | title     | metatype        | function | groupBy | chartType |
      | chart | Per Layer | Dash Layer Root | count    | layer   | pie       |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Layers"
    Then the widget "Per Layer" shows the groups:
      | label            | value |
      | Dash Application | 20    |
      | Dash Business    | 20    |
      | Dash Technology  | 10    |

  @US-057 @dashboard-aggregation
  Scenario: Grouping by an unknown property
    Given the element type "Dash Group Application"
    And the element type "Dash Group Other"
    And the property "Dash Group Plateau" of type "enum" is mapped to the element type "Dash Group Other"
    And the enterprise architect has created the dashboard "Dash Group" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Group":
      | type  | title   | metatype               | function | groupBy                    | chartType |
      | chart | Plateau | Dash Group Application | count    | property:Dash Group Plateau | bar       |
    Then the dashboard request is rejected with 422 naming "groupBy"

  @US-057 @AC4 @REQ-053 @dashboard-aggregation
  Scenario: Many groups
    Given the element type "Dash Many Application"
    And the property "Dash Many Vendor" of type "string" is mapped to the element type "Dash Many Application"
    And 60 entities of the element type "Dash Many Application" with distinct values of the property "Dash Many Vendor"
    And the enterprise architect has created the dashboard "Dash Many" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Many":
      | type  | title   | metatype              | function | groupBy                  | chartType |
      | chart | Vendors | Dash Many Application | count    | property:Dash Many Vendor | treemap   |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Many"
    Then the widget "Vendors" shows 51 groups, the last one named "Other" with the value 10

  @US-057 @dashboard-aggregation
  Scenario: Chart without grouping
    Given the element type "Dash Ungrouped Application"
    And the enterprise architect has created the dashboard "Dash Ungrouped" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Ungrouped":
      | type  | title | metatype                   | function | chartType |
      | chart | Plain | Dash Ungrouped Application | count    | bar       |
    Then the dashboard request is rejected with 422 naming "groupBy"

  @US-056 @US-057 @REQ-056 @dashboard-aggregation
  Scenario: Restricted property
    Given the element type "Dash Restricted Application"
    And the property "Dash Restricted Investment" of type "integer" is mapped to the element type "Dash Restricted Application"
    And the aggregated property "Dash Restricted Investment" is readable only by the role "enterprise-architect"
    And the entity "Dash Restricted 1" of the element type "Dash Restricted Application" with the "Dash Restricted Investment" "100"
    And the enterprise architect has created the dashboard "Dash Restricted Sum" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Restricted Sum":
      | type | title      | metatype                    | property                   | function |
      | kpi  | Investment | Dash Restricted Application | Dash Restricted Investment | sum      |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Restricted Sum"
    Then the widget "Investment" shows the error "restricted"
    When the enterprise architect reads the data of the dashboard "Dash Restricted Sum"
    Then the widget "Investment" has the value 100

  @US-057 @REQ-056 @dashboard-aggregation
  Scenario: Connection-scoped grouping
    Given the element type "Dash Scoped Application"
    And the property "Dash Scoped Owner Unit" of type "string" is mapped to the element type "Dash Scoped Application"
    And the entity "Dash Scoped Own" of the element type "Dash Scoped Application" with the "Dash Scoped Owner Unit" "Finance"
    And the property "Dash Scoped Owner Unit" is connection-scoped and in scope of the enterprise architect for the entity "Dash Scoped Own"
    And the enterprise architect has created the dashboard "Dash Scoped" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Scoped":
      | type  | title | metatype                | function | groupBy                       | chartType |
      | chart | Units | Dash Scoped Application | count    | property:Dash Scoped Owner Unit | bar       |
    Then the dashboard request is accepted
    When the enterprise architect reads the data of the dashboard "Dash Scoped"
    Then the widget "Units" shows the error "restricted"

  @REQ-053 @dashboard-aggregation
  Scenario: Property unmapped later
    Given the element type "Dash Later Application"
    And the property "Dash Later Investment" of type "integer" is mapped to the element type "Dash Later Application"
    And the entity "Dash Later 1" of the element type "Dash Later Application" with the "Dash Later Investment" "7"
    And the enterprise architect has created the dashboard "Dash Later" with scope "instance"
    And the enterprise architect adds the text widget "Still Fine" with the content "x" to the dashboard "Dash Later"
    When the enterprise architect adds this widget to the dashboard "Dash Later":
      | type | title      | metatype               | property              | function |
      | kpi  | Investment | Dash Later Application | Dash Later Investment | sum      |
    Then the dashboard request is accepted
    Given the property "Dash Later Investment" is no longer mapped to the element type "Dash Later Application"
    When the viewer reads the data of the dashboard "Dash Later"
    Then the widget "Investment" shows the error "invalid-configuration"
    And the widget "Still Fine" has the status "static"

  # ---- comparison with the value 30 days ago (us-056-kpi-previous-value)
  # The scenario "Boundary of the period" needs exact instants, which a running instance cannot give;
  # it is covered by EntityAggregationStoreTests and DashboardAggregationTests with a fixed clock.

  @US-056 @AC1 @REQ-053 @dashboard-aggregation
  Scenario: Count grew
    Given the element type "Dash Grown Application"
    And 11 entities of the element type "Dash Grown Application" named "Dash Grown Old "
    And the entities named "Dash Grown Old " were created 40 days ago
    And the entity "Dash Grown Removed" of the element type "Dash Grown Application"
    And the entity "Dash Grown Removed" was created 40 days ago
    And the entity "Dash Grown Removed" was soft-deleted 5 days ago
    And 3 entities of the element type "Dash Grown Application" named "Dash Grown New "
    And the enterprise architect has created the dashboard "Dash Grown" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Grown":
      | type | title    | metatype               | function | comparison        |
      | kpi  | Programs | Dash Grown Application | count    | vs-previous-value |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Grown"
    Then the widget "Programs" has the value 14
    And the widget "Programs" has the previous value 12
    And the widget "Programs" compares with the point in time 30 days ago

  @US-056 @AC1 @REQ-053 @dashboard-aggregation
  Scenario: Value changed since
    Given the element type "Dash Changed Application"
    And the property "Dash Changed Investment" of type "integer" is mapped to the element type "Dash Changed Application"
    And the entity "Dash Changed 1" of the element type "Dash Changed Application" with the "Dash Changed Investment" "7000000"
    And the entity "Dash Changed 2" of the element type "Dash Changed Application" with the "Dash Changed Investment" "12000000"
    And the entity "Dash Changed 3" of the element type "Dash Changed Application" with the "Dash Changed Investment" "25000000"
    And the entities named "Dash Changed " were created 40 days ago
    And the "Dash Changed Investment" of the entity "Dash Changed 1" was changed 10 days ago from "5000000"
    And the enterprise architect has created the dashboard "Dash Changed" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Changed":
      | type | title      | metatype                 | property                | function | comparison        |
      | kpi  | Investment | Dash Changed Application | Dash Changed Investment | sum      | vs-previous-value |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Changed"
    Then the widget "Investment" has the value 44000000
    And the widget "Investment" has the previous value 42000000

  @US-056 @AC4 @REQ-053 @dashboard-aggregation
  Scenario: No data 30 days ago
    Given the element type "Dash Recent Application"
    And the property "Dash Recent Investment" of type "integer" is mapped to the element type "Dash Recent Application"
    And the entity "Dash Recent 1" of the element type "Dash Recent Application" with the "Dash Recent Investment" "9000000"
    And the enterprise architect has created the dashboard "Dash Recent" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Recent":
      | type | title      | metatype                | property               | function | comparison        |
      | kpi  | Investment | Dash Recent Application | Dash Recent Investment | sum      | vs-previous-value |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Recent"
    Then the widget "Investment" has the value 9000000
    And the widget "Investment" has no previous value

  @US-056 @REQ-056 @dashboard-aggregation
  Scenario: Restricted property with a comparison
    Given the element type "Dash Cmp Restricted Application"
    And the property "Dash Cmp Restricted Investment" of type "integer" is mapped to the element type "Dash Cmp Restricted Application"
    And the aggregated property "Dash Cmp Restricted Investment" is readable only by the role "enterprise-architect"
    And the entity "Dash Cmp Restricted 1" of the element type "Dash Cmp Restricted Application" with the "Dash Cmp Restricted Investment" "100"
    And the entity "Dash Cmp Restricted 1" was created 40 days ago
    And the enterprise architect has created the dashboard "Dash Cmp Restricted Sum" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Dash Cmp Restricted Sum":
      | type | title      | metatype                        | property                        | function | comparison        |
      | kpi  | Investment | Dash Cmp Restricted Application | Dash Cmp Restricted Investment | sum      | vs-previous-value |
    Then the dashboard request is accepted
    When the viewer reads the data of the dashboard "Dash Cmp Restricted Sum"
    Then the widget "Investment" shows the error "restricted"
    And the widget "Investment" has no previous value
    When the enterprise architect reads the data of the dashboard "Dash Cmp Restricted Sum"
    Then the widget "Investment" has the previous value 100

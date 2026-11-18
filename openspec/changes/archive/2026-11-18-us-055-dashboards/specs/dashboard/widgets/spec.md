## ADDED Requirements

### Requirement: Widgets of a dashboard

Those who may change a dashboard SHALL add, configure and remove widgets with a title of 1 to 150 characters and a grid position, up to 50 per dashboard. The type SHALL NOT change after creation. The types `text` and `table` SHALL be accepted; `kpi` and `chart` SHALL give 422 until they are supported.

Trace: REQ-052, US-058, US-059

#### Scenario: Type cannot change

- **WHEN** Kurt changes a text widget into a table widget
- **THEN** the request is rejected with 422 naming `type`

#### Scenario: Widgets at the limit

- **GIVEN** a dashboard with 49 widgets
- **WHEN** Kurt adds a widget, and then another one
- **THEN** the 50th is added, and the 51st is rejected with 422

#### Scenario: KPI not supported yet

- **WHEN** Kurt adds a widget of type `kpi`
- **THEN** the request is rejected with 422 naming `type`

### Requirement: Text widget

A text widget SHALL hold Markdown of 0 to 10 000 characters and no data source. Its data SHALL have `status=static`. The web app SHALL render it as sanitized HTML: no scripts, event handlers or unknown tags, and links only with `http`, `https` or `mailto`.

Trace: US-059 AC1–AC4, REQ-052, REQ-055

#### Scenario: Methodological notes

- **WHEN** Kurt adds the text widget "Methodological Notes" with bold text, a list and a link
- **THEN** it is saved, its data has `status=static`, and the dashboard shows the formatted text, not the Markdown source

#### Scenario: Empty and maximal content

- **WHEN** Kurt adds text widgets with empty content, with 10 000 and with 10 001 characters
- **THEN** the first two are added, and the third is rejected with 422 naming `content`

#### Scenario: Script in the content

- **GIVEN** a text widget with the content `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>` and `[x](javascript:alert(1))`
- **WHEN** Franz opens the dashboard
- **THEN** no script runs, and the link is not clickable

### Requirement: Table widget

A table widget SHALL show a catalog visible to its writer (403 otherwise), on a shared dashboard a shared one (422 otherwise), optionally through a saved view of that catalog (422 otherwise), with 5 to 100 rows per page (default 10). A data source other than a catalog query SHALL give 422 "Table widget requires CatalogQuery DataSource".

Trace: US-058 AC1, AC2, AC3, AC5, REQ-054

#### Scenario: Catalog with a saved view

- **GIVEN** the shared catalog "Application Inventory" with the saved view "Compact view" and 47 entities
- **WHEN** Kurt adds the table widget "Application Overview" with that catalog, the view and 10 rows per page
- **THEN** its data shows the first 10 rows with the columns of "Compact view" and the total 47

#### Scenario: Without a saved view

- **WHEN** Kurt adds a table widget for "Application Inventory" without a view
- **THEN** its data uses the default state of the catalog

#### Scenario: Another person's personal catalog

- **WHEN** Kurt adds a table widget for Franz's personal catalog
- **THEN** the request is rejected with 403, and no widget is saved

#### Scenario: Own personal catalog on a shared dashboard

- **WHEN** Kurt adds a table widget for his own personal catalog to a shared dashboard
- **THEN** the request is rejected with 422 naming `catalogId`

#### Scenario: Page size at the limits

- **WHEN** Kurt adds table widgets with 5, 100, 4 and 101 rows per page
- **THEN** 5 and 100 are accepted, and 4 and 101 are rejected with 422 naming `pageSize`

#### Scenario: Wrong data source

- **WHEN** Kurt adds a table widget with a property aggregation as data source
- **THEN** the request is rejected with 422 "Table widget requires CatalogQuery DataSource"

### Requirement: Dashboard data

The data of a dashboard SHALL be computed on each request from the current repository, with the reader's permissions and visibility. A widget whose catalog or view no longer exists or is not visible SHALL get an error status; the other widgets SHALL be computed normally. Further pages of a table widget SHALL be read per widget.

Trace: US-058 AC4, REQ-055, REQ-056

#### Scenario: Deleted catalog

- **GIVEN** a dashboard with a text widget and a table widget for the catalog "Inventory"
- **WHEN** "Inventory" is deleted and Kurt opens the dashboard
- **THEN** the table widget shows "Catalog not found", and the text widget is shown normally

#### Scenario: Second page of a table

- **GIVEN** the table widget "Application Overview" over 47 entities with 10 rows per page
- **WHEN** Franz chooses page 2
- **THEN** the widget shows entities 11 to 20 and "page 2 of 5"

#### Scenario: Restricted values

- **GIVEN** a column of "Application Inventory" that Franz may not read
- **WHEN** Franz opens the dashboard
- **THEN** the table widget shows that column redacted, as the catalog does

### Requirement: Grid layout

Each widget SHALL have a position in the 12-column grid: column 1 to 12, width 1 to 12 with column + width at most 13, row 1 to 500, height 1 to 6. Several positions SHALL be changeable in one request. Overlaps SHALL be accepted by the server and marked in the editor.

Trace: US-060 AC1–AC4, REQ-057

#### Scenario: Position two widgets

- **WHEN** Kurt places a widget at column 1, row 1, width 3, height 1 and another at column 4, row 1, width 9, height 3
- **THEN** both positions are saved and are the same after reloading

#### Scenario: Width at the grid border

- **WHEN** Kurt places a widget at column 5 with width 8, and then with width 9
- **THEN** width 8 is accepted, and width 9 is rejected with "Widget exceeds the grid width", naming `width`

#### Scenario: Height at the limits

- **WHEN** Kurt places a widget with height 6, and then with height 7
- **THEN** height 6 is accepted, and height 7 is rejected with 422 naming `height`

#### Scenario: Overlap

- **WHEN** Kurt places two widgets so that they overlap
- **THEN** both positions are saved, and the editor marks both widgets

#### Scenario: Many rows

- **WHEN** Kurt places widgets on rows 1 to 8
- **THEN** the dashboard scrolls vertically and shows all of them

### Requirement: Arrange widgets in the editor

In edit mode, the dashboard page SHALL let widgets be moved and resized by drag and drop, and by keyboard controls for move and resize. A change SHALL be saved as one layout request. The view mode SHALL show the widgets at their positions without drag and drop.

Trace: US-060 AC1, AC5, REQ-057

#### Scenario: Move a widget by keyboard

- **WHEN** Kurt selects a widget in edit mode and moves it one column to the right with the keyboard
- **THEN** its new position is saved

#### Scenario: Width beyond the grid in the editor

- **WHEN** Kurt drags a widget so that it would exceed column 12
- **THEN** the editor shows "Widget exceeds the grid width", and the position is not saved

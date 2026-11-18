# navigation/solution-browser Specification

## Purpose

Defines the browser tab of the sidebar: the computed "Current state" node, the plateaus and project solutions with their solutions, the categories with their counts and paged contents, who reads them, and the limits of the browser queries.

## Requirements

### Requirement: Browser tab in the sidebar

The sidebar SHALL have two tabs, "Browser" and "Navigation tree". "Browser" SHALL be active when the sidebar opens; the tab last chosen SHALL stay active while the person moves between pages. The tab "Navigation tree" SHALL show the navigation tree unchanged.

Trace: US-132 AC1, REQ-138, ADR-020

#### Scenario: Browser is the default tab

- **WHEN** Michael logs in and the sidebar opens
- **THEN** the tab "Browser" is active and shows "Current state" as its first node

#### Scenario: The navigation tree stays reachable

- **WHEN** Michael chooses the tab "Navigation tree"
- **THEN** the sidebar shows the navigation tree from "Repository"

### Requirement: Top level of the browser

The browser SHALL show "Current state" first, then one node per plateau with status `transition` or `target` in the order of the plateau overview, then the group "Projects". The baseline and realized plateaus SHALL have no node. A plateau or "Projects" without solutions SHALL stay visible with 0, greyed out.

Trace: US-132 AC1, AC2, REQ-138, REQ-140, owner decision 2026-11-18

#### Scenario: Plateaus after the current state

- **GIVEN** the baseline "AS-IS 2026", the transition plateau "2027" valid from 2027-01-01 and the target plateau "TO-BE 2030" valid from 2030-01-01
- **WHEN** Michael opens the browser
- **THEN** the top level is "Current state", "2027", "TO-BE 2030", "Projects", in this order

#### Scenario: No plateau mode

- **GIVEN** no plateau exists
- **WHEN** Michael opens the browser
- **THEN** the top level is "Current state" and "Projects"

#### Scenario: Empty plateau

- **GIVEN** the target plateau "TO-BE 2030" has no solution
- **WHEN** Michael opens the browser
- **THEN** "TO-BE 2030 (0)" is shown greyed out

### Requirement: Solutions in the browser

A solution SHALL appear when its status is neither `implemented` nor `archived`: a plateau solution under its target plateau, a project solution under "Projects", each sorted by name, case-insensitively. A solution SHALL appear only there, never as a top-level node.

Trace: US-132 AC2, AC3, REQ-138, REQ-140, owner decision 2026-11-18

#### Scenario: Plateau and project solutions

- **GIVEN** the plateau solution "CRM Migration" with target plateau "2027" and the project solution "ERP Upgrade", both `draft`
- **WHEN** Michael opens the browser
- **THEN** "CRM Migration" is listed under "2027" and "ERP Upgrade" under "Projects"

#### Scenario: Live solution leaves the browser

- **GIVEN** the project solution "ERP Upgrade" went live and has status `implemented`
- **WHEN** Michael opens the browser
- **THEN** "ERP Upgrade" is not listed, and its entities count under "Current state"

#### Scenario: Archived solution is hidden

- **GIVEN** the solution "Old Portal" has status `archived`
- **WHEN** Michael opens the browser
- **THEN** "Old Portal" is not listed

### Requirement: Categories and counts

"Current state" SHALL have the categories Components (element entities not deleted), Connections (connection entities not deleted) and Catalogs (the catalogs the reader sees). A solution SHALL have the category Components (its deltas). Each category SHALL show its count; an empty one SHALL be greyed out, not hidden.

Trace: US-133 AC1, AC2, REQ-139, owner decision 2026-11-18

#### Scenario: Categories of the current state

- **GIVEN** 4 element entities, 2 connections and no catalog, and one more element entity that is deleted
- **WHEN** Kurt expands "Current state"
- **THEN** it shows "Components (4)", "Connections (2)" and "Catalogs (0)", the last one greyed out

#### Scenario: Components of a solution

- **GIVEN** "CRM Migration" has a delta `new` "CRM Cloud", a delta `modified` for "SAP ERP" and a delta `retiring` for "Legacy CRM"
- **WHEN** Kurt expands "CRM Migration"
- **THEN** it shows exactly "Components (3)"

#### Scenario: Solution without deltas

- **GIVEN** "ERP Upgrade" has no delta
- **WHEN** Kurt expands "ERP Upgrade"
- **THEN** it shows "Components (0)" greyed out

### Requirement: Contents of a category

An expanded category SHALL list its objects by name, case-insensitively, then by ID, in pages of at most 200 (default 50), with a link to load more. A component SHALL show its metatype abbreviation; a component of a solution SHALL show its delta type. An entity SHALL open its change history, a catalog its page, a delta its solution's page.

Trace: US-133 AC1, AC2, REQ-139

#### Scenario: Delta types in a solution

- **GIVEN** the deltas of "CRM Migration" from above
- **WHEN** Kurt expands "Components (3)"
- **THEN** it lists "CRM Cloud" marked new, "Legacy CRM" marked retiring and "SAP ERP" marked modified, in this order

#### Scenario: Second page

- **GIVEN** 51 element entities
- **WHEN** Kurt expands "Components (51)" of "Current state" and chooses "Load more"
- **THEN** the first page lists 50 entities and the second page the 51st

#### Scenario: Open an entity

- **WHEN** Kurt chooses "SAP ERP" under "Current state"
- **THEN** the change history of "SAP ERP" opens

### Requirement: Who reads the browser

Reading the browser SHALL need `entity:read`; without it the sidebar SHALL not show the tab "Browser". Catalogs SHALL count and list only instance catalogs and the reader's own personal ones. Names SHALL be shown as text, never as markup.

Trace: US-132, US-133, ADR-032, owner decision 2026-11-18

#### Scenario: Reader without entity:read

- **WHEN** a person without `entity:read` reads the browser
- **THEN** the answer is 403

#### Scenario: Another person's personal catalog

- **GIVEN** Kurt has the personal catalog "Kurt's Candidates" and there is one instance catalog
- **WHEN** Franz expands "Current state"
- **THEN** it shows "Catalogs (1)", and Kurt sees "Catalogs (2)"

#### Scenario: Markup in a solution name

- **GIVEN** a solution named `<img src=x onerror=alert(1)>`
- **WHEN** Franz opens the browser
- **THEN** the name is shown literally, and no script runs

### Requirement: Limits of the browser queries

A page of a category SHALL hold 1 to 200 objects; 0 or 201 SHALL give 400, as SHALL a page below 0. A category that the scope does not have SHALL give 422 naming `category`; a `solutionId` missing for a solution or given for the current state SHALL give 422 naming `solutionId`. A solution that does not exist SHALL give 404; one that is `implemented` or `archived` SHALL still list its deltas.

Trace: US-133, ADR-032

#### Scenario: Page size at the limits

- **WHEN** Kurt reads a category with `pageSize` 200, then with 201, then with 1, then with 0
- **THEN** 200 and 1 are accepted, and 201 and 0 give 400

#### Scenario: Category a solution does not have

- **WHEN** Kurt reads the category `connections` of the solution "CRM Migration"
- **THEN** the answer is 422 naming `category`

#### Scenario: Unknown solution

- **WHEN** Kurt reads the components of a solution ID that does not exist
- **THEN** the answer is 404

## Purpose

Defines the plateaus of plateau mode: creating the baseline, target and transition plateaus, their predecessor and basis, the content of a plateau, editing and the read-only protection of realized plateaus, the permissions and the audit, and the screen "Plateaus".

## ADDED Requirements

### Requirement: Create the baseline plateau

A person with `plateau:write` SHALL create a plateau with the status `baseline`, a name, an optional description and an optional `validFrom`, without a predecessor and without a basis. The response SHALL be 201 with the plateau. Creating the first baseline SHALL turn plateau mode on; there SHALL be no other setting for it.

Trace: US-084 AC1, REQ-084 AC1, UC-11 A1, owner decision 2026-11-17

#### Scenario: Initial baseline created

- **GIVEN** no plateau exists and Kurt holds `enterprise-architect`
- **WHEN** he creates the plateau "Baseline 2026-Q2" with the status `baseline`
- **THEN** the response is 201 with `status=baseline`, no predecessor and no basis, and the plateau is listed

#### Scenario: Baseline with a predecessor

- **GIVEN** the target plateau "Target 2027" exists
- **WHEN** Kurt creates a plateau with the status `baseline` and the predecessor "Target 2027"
- **THEN** the response is 422 naming `succeeds`, and no plateau is created

### Requirement: Only one baseline plateau

An instance SHALL have at most one plateau with the status `baseline`. Creating a second one SHALL be rejected with 422 and the detail "A baseline plateau already exists"; nothing SHALL be stored or audited.

Trace: US-084 AC3, REQ-084 AC3, BR-01 of the plateau

#### Scenario: Second baseline

- **GIVEN** the baseline "Baseline 2026-Q2" exists
- **WHEN** Kurt creates another plateau with the status `baseline`
- **THEN** the response is 422 with the detail "A baseline plateau already exists", and only one baseline exists

### Requirement: Create a target or transition plateau

A person with `plateau:write` SHALL create a plateau with the status `target` or `transition`, a name, a predecessor, a basis (`catalog` or `predecessor`), an optional description and an optional `validFrom`. Without a baseline plateau, the creation SHALL be rejected with 422 and the detail "No baseline plateau exists. Create an initial baseline first."

Trace: US-084 AC2, REQ-084 AC2, US-155, UC-11 A2, A3, E1

#### Scenario: Target created

- **GIVEN** the baseline "Baseline 2026-Q2" exists
- **WHEN** Kurt creates the plateau "Target 2027" with the status `target`, the baseline as predecessor and the basis `catalog`
- **THEN** the response is 201 with `status=target`, the baseline as `succeeds` and the basis `catalog`

#### Scenario: Transition created

- **GIVEN** the target "Target 2027" exists
- **WHEN** Kurt creates the plateau "Phase 1" with the status `transition` and "Target 2027" as predecessor
- **THEN** the response is 201 with `status=transition`

#### Scenario: Target without a baseline

- **GIVEN** no plateau exists
- **WHEN** Kurt creates a plateau with the status `target`
- **THEN** the response is 422 with the detail "No baseline plateau exists. Create an initial baseline first.", and no plateau exists

#### Scenario: Target without a predecessor

- **GIVEN** the baseline exists
- **WHEN** Kurt creates a target plateau without a predecessor
- **THEN** the response is 422 naming `succeeds`, and no plateau is created

### Requirement: Valid plateau fields

The name SHALL have 1 to 255 characters, SHALL NOT be blank, SHALL be stored trimmed and SHALL be unique case-insensitively. The description SHALL have at most 2000 characters. A longer name or description SHALL answer 400, a blank or taken name 422 naming `name`; nothing SHALL be stored.

Trace: REQ-084, plateau BO

#### Scenario: Name of 255 characters

- **WHEN** Kurt creates a plateau with a name of exactly 255 characters
- **THEN** the response is 201

#### Scenario: Name of 256 characters

- **WHEN** Kurt creates a plateau with a name of 256 characters
- **THEN** the response is 400, and no plateau is created

#### Scenario: Description of 2000 characters

- **WHEN** Kurt creates a plateau with a description of exactly 2000 characters
- **THEN** the response is 201

#### Scenario: Description of 2001 characters

- **WHEN** Kurt creates a plateau with a description of 2001 characters
- **THEN** the response is 400, and no plateau is created

#### Scenario: Name taken

- **GIVEN** the plateau "Target 2027" exists
- **WHEN** Kurt creates a plateau named " target 2027 "
- **THEN** the response is 422 naming `name`

#### Scenario: Blank name

- **WHEN** Kurt creates a plateau named "   "
- **THEN** the response is 422 naming `name`

### Requirement: Predecessor of a plateau

The predecessor of a target or transition plateau SHALL be an existing plateau that is not realized, and the chain of predecessors SHALL have no cycle. A violation SHALL be rejected with 422 naming `succeeds`. While a solution targets a plateau, its predecessor SHALL NOT change (409 `plateau-in-use`).

Trace: REQ-084, UC-11 A2, plateau BO BR-04

#### Scenario: Realized predecessor

- **GIVEN** the plateau "Baseline 2025" is realized
- **WHEN** Kurt creates a target plateau with "Baseline 2025" as predecessor
- **THEN** the response is 422 naming `succeeds`

#### Scenario: Cycle

- **GIVEN** "Phase 1" succeeds "Target 2027"
- **WHEN** Kurt changes the predecessor of "Target 2027" to "Phase 1"
- **THEN** the response is 422 naming `succeeds`, and the predecessor of "Target 2027" is unchanged

#### Scenario: Predecessor of a plateau in use

- **GIVEN** the solution "CRM Replacement" targets "Target 2027"
- **WHEN** Kurt changes the predecessor of "Target 2027"
- **THEN** the response is 409 `plateau-in-use`, and nothing changes

#### Scenario: Predecessor of an unused plateau

- **GIVEN** no solution targets "Phase 1"
- **WHEN** Kurt changes the predecessor of "Phase 1" to the baseline
- **THEN** the response is 200 with the baseline as `succeeds`

### Requirement: Basis of a plateau

A target or transition plateau SHALL have the basis `catalog` or `predecessor`; the baseline SHALL have none. The basis `catalog` SHALL be the current catalog. The basis `predecessor` SHALL be the content of the predecessor plateau. The basis SHALL be changeable while the plateau is not realized.

Trace: US-155 AC1, AC3, AC4, owner decision 2026-11-17

#### Scenario: Basis catalog

- **GIVEN** "Target 2027" has the basis `catalog`
- **WHEN** its basis is read
- **THEN** it is the current catalog

#### Scenario: Basis changed

- **GIVEN** "Target 2028" has the basis `predecessor`
- **WHEN** Kurt changes it to `catalog`
- **THEN** the response is 200, and the basis of "Target 2028" is the current catalog from then on

#### Scenario: Basis missing

- **WHEN** Kurt creates a target plateau without a basis
- **THEN** the response is 422 naming `basis`, and no plateau is created

### Requirement: Content of a plateau

The content of a plateau SHALL be its basis with the deltas of the active solutions that target it: a delta `modified` SHALL apply its after values, a delta `retiring` SHALL mark its entity as retiring, and a delta `new` SHALL add its entity as proposed. When two deltas change the same field, the delta changed last SHALL win. The baseline's content SHALL be the catalog.

Trace: US-155 AC2, owner decision 2026-11-17

#### Scenario: Content of a predecessor

- **GIVEN** the active solution "CRM Replacement" targets "Target 2027", retires "Legacy CRM", renames "ERP-Core" to "ERP-Core 2" and proposes the new entity "Salesforce", and "Target 2028" succeeds "Target 2027" with the basis `predecessor`
- **WHEN** the basis of "Target 2028" is read
- **THEN** it lists "Legacy CRM" as retiring, "ERP-Core 2" as active and "Salesforce" as proposed, each naming "CRM Replacement"

#### Scenario: Chain of predecessors

- **GIVEN** "Phase 2" succeeds "Target 2028" with the basis `predecessor`, and "Target 2028" succeeds "Target 2027" with the basis `predecessor`
- **WHEN** the basis of "Phase 2" is read
- **THEN** it contains the changes of the active solutions of "Target 2027" and of "Target 2028"

#### Scenario: Completed solutions

- **GIVEN** the solution "CRM Replacement" of "Target 2027" is `implemented`
- **WHEN** the basis of "Target 2028" is read
- **THEN** its deltas are not applied again, and the catalog shows their effect

#### Scenario: Two solutions change one field

- **GIVEN** the active solutions "CRM Replacement" and "ERP Extension" of "Target 2027" both change the description of "ERP-Core", "ERP Extension" last
- **WHEN** the basis of "Target 2028" is read
- **THEN** "ERP-Core" has the description of "ERP Extension"

### Requirement: Read plateaus

`listPlateaus` SHALL return the baseline, then the transition and target plateaus by `validFrom` and name, then the realized plateaus newest first. `getPlateau` SHALL return one plateau or 404. A plateau SHALL carry its fields, status, predecessor, basis, `validTo`, the number of solutions that target it and its creator.

Trace: US-085, REQ-085

#### Scenario: Plateaus listed

- **GIVEN** the baseline, "Target 2027" and "Phase 1" exist
- **WHEN** Michael lists the plateaus
- **THEN** the baseline comes first, followed by "Phase 1" and "Target 2027" in the order of their `validFrom`

#### Scenario: Unknown plateau

- **WHEN** an unknown plateau ID is read
- **THEN** the response is 404

### Requirement: Edit a plateau

A person with `plateau:write` SHALL change the name, description, `validFrom`, predecessor and basis of a plateau that is not realized, with the checks of their creation. The status SHALL NOT be changeable by an edit; a different status SHALL be rejected with 422 naming `status`.

Trace: US-089, UC-11, owner decision 2026-11-16 (go-live in change 4)

#### Scenario: Plateau renamed

- **WHEN** Kurt renames "Target 2027" to "Target 2027 Cloud"
- **THEN** the response is 200 with the new name

#### Scenario: Status changed by an edit

- **WHEN** Kurt sends the status `baseline` for "Target 2027"
- **THEN** the response is 422 naming `status`, and "Target 2027" stays a target

### Requirement: Realized plateau is read-only

An edit of a realized plateau SHALL be rejected with 422 and the detail "Realized plateau is read-only"; nothing SHALL change.

Trace: US-089 AC1, REQ-089, BR-03 of the plateau

#### Scenario: Realized plateau edited

- **GIVEN** "Baseline 2025" is realized
- **WHEN** Kurt renames it
- **THEN** the response is 422 with the detail "Realized plateau is read-only", and the name is unchanged

#### Scenario: Plateau not realized

- **GIVEN** "Target 2027" is a target
- **WHEN** Kurt renames it
- **THEN** the response is 200

### Requirement: Permissions for plateaus

Creating and editing plateaus SHALL need `plateau:write`, which the built-in role `enterprise-architect` SHALL hold. Reading plateaus SHALL need `plateau:write`, `plateau:go-live` or `solution:write`. Without them, the response SHALL be 403 and nothing SHALL change.

Trace: UC-11 E4, owner decision 2026-11-17

#### Scenario: Creation without plateau:write

- **GIVEN** Michael holds only `solution-architect`
- **WHEN** he creates a plateau
- **THEN** the response is 403, and no plateau exists

#### Scenario: Reading with solution:write

- **GIVEN** Michael holds only `solution-architect`
- **WHEN** he lists the plateaus
- **THEN** the response is 200

#### Scenario: Reading without a permission

- **GIVEN** a person with only `entity:read`
- **WHEN** they list the plateaus
- **THEN** the response is 403

### Requirement: Audit of plateaus

Creating a plateau SHALL be audited as `plateau.created` with its name and status, and editing one as `plateau.updated` with the names of the changed fields. A rejected write SHALL NOT be audited.

Trace: UC-11, ADR-033

#### Scenario: Creation audited

- **WHEN** Kurt creates "Target 2027"
- **THEN** `plateau.created` is audited with the name and the status `target`

#### Scenario: Edit audited

- **WHEN** Kurt changes the basis of "Target 2028"
- **THEN** `plateau.updated` is audited naming `basis`

### Requirement: Screen "Plateaus"

The page "Plateaus" (SCR-065) SHALL show the baseline highlighted, the target and transition plateaus with `validFrom`, and the realized plateaus in a collapsible section. Without a target, it SHALL show "No target plateau planned yet"; without a baseline, a notice with "Create initial baseline". Only holders of `plateau:write` SHALL see "New plateau" and "Edit", never on a realized plateau.

Trace: US-085 AC1, AC2, US-089 AC2, UC-11 A1

#### Scenario: Grouped display

- **GIVEN** the baseline, "Target 2027" and the realized "Baseline 2025" exist
- **WHEN** Kurt opens the page "Plateaus"
- **THEN** the baseline is highlighted, "Target 2027" shows its `validFrom`, and "Baseline 2025" is in the collapsed section "Realized"

#### Scenario: No target plateau

- **GIVEN** only the baseline exists
- **WHEN** Kurt opens the page
- **THEN** the target section shows "No target plateau planned yet"

#### Scenario: No baseline

- **GIVEN** no plateau exists
- **WHEN** Kurt opens the page
- **THEN** it shows the notice and "Create initial baseline"

#### Scenario: Realized plateau

- **WHEN** Kurt views "Baseline 2025" in the section "Realized"
- **THEN** no "Edit" is visible

#### Scenario: Without plateau:write

- **GIVEN** Michael holds only `solution-architect`
- **WHEN** he opens the page
- **THEN** he sees the plateaus without "New plateau" and "Edit"

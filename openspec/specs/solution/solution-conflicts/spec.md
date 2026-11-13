# solution/solution-conflicts Specification

## Purpose

Defines the conflicts of a solution with other active solutions that change the same entity: what conflicts, how the conflicts are computed and returned with the deltas, the diff and the baseline, and how the screens "Solution" and "Solution comparison" warn about them.

## Requirements

### Requirement: Conflicts of a delta

A delta `modified` or `retiring` SHALL carry one conflict per other active solution that holds a delta `modified` or `retiring` for the same entity. A solution SHALL be active unless its status is `implemented` or `archived`. A delta `new` SHALL carry no conflicts. The conflicts SHALL be computed on each read and SHALL NOT be stored.

Trace: US-044 AC1, AC2, AC5, REQ-042 AC1–AC4, owner decision 2026-11-13

#### Scenario: Conflict detected

- **GIVEN** solution A (draft, owner Maria) holds a delta for `CRM-Legacy`
- **WHEN** Michael captures a delta for `CRM-Legacy` in solution B
- **THEN** the delta is saved with status 201
- **AND** it carries one conflict naming solution A, its status `draft` and the owner Maria

#### Scenario: No conflict with a completed solution

- **GIVEN** solution A holds a delta for `CRM-Legacy` and has status `implemented` or `archived`
- **WHEN** Michael captures a delta for `CRM-Legacy` in solution B
- **THEN** the delta carries no conflicts

#### Scenario: Every active status conflicts

- **GIVEN** solution A holds a delta for `CRM-Legacy` and has status `draft`, `proposed`, `approved` or `in-progress`
- **WHEN** the deltas of solution B with a delta for `CRM-Legacy` are read
- **THEN** that delta carries a conflict with solution A

#### Scenario: Multiple conflicts

- **GIVEN** solutions A and B each hold a delta for `CRM-Legacy`
- **WHEN** Michael captures a delta for `CRM-Legacy` in solution C
- **THEN** the delta carries two conflicts, A before B

#### Scenario: New deltas never conflict

- **GIVEN** solutions A and B each hold a delta `new` named `Warehouse Cloud` of the same metatype
- **WHEN** the deltas of solution B are read
- **THEN** that delta carries no conflicts

#### Scenario: Other entity

- **GIVEN** solution A holds a delta for `ERP-Core`
- **WHEN** Michael captures a delta for `CRM-Legacy` in solution B
- **THEN** the delta carries no conflicts

### Requirement: Content of a conflict

Each conflict SHALL name the other solution's ID, name and status, its owner's ID and name, and the type of its delta. The conflicts of a delta SHALL be ordered by solution name (case-insensitive).

Trace: US-044 AC1, REQ-042 Processing

#### Scenario: Owner and delta type

- **GIVEN** solution A (proposed, owner Maria) retires `CRM-Legacy`
- **WHEN** Michael modifies `CRM-Legacy` in solution B
- **THEN** the conflict names solution A, status `proposed`, owner Maria with her person ID, and delta type `retiring`

### Requirement: Warning never blocks

A conflict SHALL NOT reject or change a write. Capturing and editing a delta SHALL return the saved delta with its conflicts.

Trace: US-044 Technical Notes, REQ-042 AC1

#### Scenario: Editing a conflicting delta

- **GIVEN** a delta `modified` for `CRM-Legacy` in solution B conflicts with solution A
- **WHEN** Michael edits it
- **THEN** the response is 200 with the changed delta and the conflict with solution A

### Requirement: Conflicts in the delta list and the diff

The delta list SHALL return the conflicts of each delta and the number of deltas with at least one conflict over all pages (`conflicted`). Each entry of the diff SHALL carry the conflicts of its delta.

Trace: US-044 AC3, REQ-042 AC5, owner decision 2026-11-13

#### Scenario: Warning persists

- **GIVEN** a delta for `CRM-Legacy` in solution B conflicts with solution A
- **WHEN** the deltas of solution B are read again
- **THEN** the delta still carries the conflict with solution A

#### Scenario: Number of conflicted deltas over all pages

- **GIVEN** solution B holds 60 deltas, 3 of which conflict, one of them on the second page of size 50
- **WHEN** the first page is read
- **THEN** `conflicted` is 3

#### Scenario: No conflicted deltas

- **WHEN** the deltas of a solution without conflicts are read
- **THEN** `conflicted` is 0

#### Scenario: Conflicts in the diff

- **GIVEN** a delta for `CRM-Legacy` in solution B conflicts with solution A
- **WHEN** the diff of solution B is read
- **THEN** the entry of `CRM-Legacy` carries the conflict with solution A

### Requirement: Warning disappears

A conflict SHALL disappear from the next read as soon as the other solution is `implemented` or `archived` or no longer holds its delta.

Trace: US-044 AC4, REQ-042 AC6

#### Scenario: Other solution implemented

- **GIVEN** a delta for `CRM-Legacy` in solution B conflicts with solution A
- **WHEN** solution A is set to `implemented` and the deltas of solution B are read
- **THEN** the delta carries no conflicts and `conflicted` is 0

#### Scenario: Other delta removed

- **GIVEN** a delta for `CRM-Legacy` in solution B conflicts with solution A
- **WHEN** solution A removes its delta and the deltas of solution B are read
- **THEN** the delta carries no conflicts

### Requirement: Parallel changes in the baseline

An entry of the baseline SHALL carry the other active solutions that hold a delta `modified` or `retiring` for its entity (`parallelChanges`), in the form of a conflict, when the setting "Show parallel changes in the baseline" of its metatype is on. When the setting is off, the list SHALL be empty. The entry read SHALL carry the same list.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Setting on

- **GIVEN** the metatype `Application Component` shows parallel changes in the baseline and solution A holds a delta for `CRM-Legacy`
- **WHEN** the baseline of solution B is read
- **THEN** the entry of `CRM-Legacy` names solution A

#### Scenario: Setting off

- **GIVEN** the metatype `Application Component` does not show parallel changes in the baseline and solution A holds a delta for `CRM-Legacy`
- **WHEN** the baseline of solution B is read
- **THEN** the entry of `CRM-Legacy` names no solution

#### Scenario: Completed solution not shown

- **GIVEN** the setting is on and solution A holds a delta for `CRM-Legacy` and is `archived`
- **WHEN** the baseline of solution B is read
- **THEN** the entry of `CRM-Legacy` names no solution

#### Scenario: Own delta not shown

- **GIVEN** the setting is on and only solution B holds a delta for `CRM-Legacy`
- **WHEN** the baseline of solution B is read
- **THEN** the entry of `CRM-Legacy` names no solution

### Requirement: Warnings on the screen "Solution"

On SCR-061, an entry of the delta list with conflicts SHALL show a warning with an icon and, per conflict, "Also changed in '{solution}' (owner: {owner})", the solution name linking to that solution. The header SHALL show a notice with the number of conflicted deltas when it is at least 1.

Trace: US-044 AC1, AC3, AC5, REQ-042 Outputs, owner decision 2026-11-13

#### Scenario: Warning on an entry and in the header

- **GIVEN** a delta for `CRM-Legacy` in solution B conflicts with solution A (owner Maria)
- **WHEN** Michael opens solution B
- **THEN** the entry of `CRM-Legacy` reads "Also changed in 'Solution A' (owner: Maria)"
- **AND** the header shows "1 change also concerns other active solutions"

#### Scenario: No conflicts

- **WHEN** Michael opens a solution without conflicts
- **THEN** neither the header nor any entry shows a conflict warning

### Requirement: Warning after saving

Saving a delta with conflicts on SCR-061 SHALL show a warning naming the conflicting solutions; the warning on the entry SHALL stay after reloading.

Trace: US-044 AC1, AC3, REQ-042 Outputs

#### Scenario: Warning after saving

- **WHEN** Michael captures a delta for `CRM-Legacy` in solution B while solution A holds one
- **THEN** a warning names solution A, and the new entry shows the conflict

### Requirement: Marker in the baseline

A baseline entry on SCR-061 with parallel changes SHALL show the marker "Changed in {n} other solutions" with the names of the solutions.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Marker in the baseline

- **GIVEN** the setting is on for `Application Component` and solutions A and C hold a delta for `CRM-Legacy`
- **WHEN** Michael opens the baseline of solution B
- **THEN** the entry of `CRM-Legacy` reads "Changed in 2 other solutions" and names A and C

#### Scenario: One other solution

- **GIVEN** the setting is on and only solution A holds a delta for `CRM-Legacy`
- **WHEN** Michael opens the baseline of solution B
- **THEN** the entry of `CRM-Legacy` reads "Changed in 1 other solution"

### Requirement: Warnings on the screen "Solution comparison"

On SCR-062, the card of an entry with conflicts SHALL show the same warning as the delta list.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Warning on a card

- **GIVEN** a delta for `CRM-Legacy` in solution B conflicts with solution A
- **WHEN** Michael opens the comparison of solution B
- **THEN** the card of `CRM-Legacy` reads "Also changed in 'Solution A' (owner: Maria)"

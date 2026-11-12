# Proposal

## Why

A change initiative needs a container in OEA before its entity changes can be captured ([UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md)). Today OEA has no solutions at all: no table, no API, no page, and no permission that a solution architect could hold. [US-038](../../../requirements/user-stories/US-038-create-solution.md) adds creating a solution with a name and an architecture vision in project mode. It is S10 change 5 and the base for the baseline and the entity deltas in S11.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05: Describe the Architecture Vision of a Change Initiative](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) |
| User Stories | [US-038: Create a Solution with Architecture Vision](../../../requirements/user-stories/US-038-create-solution.md) (AC1, AC2, AC5; AC3 and AC4 follow in S14) |
| Requirements | [REQ-038: Create a solution and describe the architecture vision](../../../requirements/req/REQ-038-create-solution.md), [REQ-157: Permission model](../../../requirements/req/REQ-157-permission-model.md) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (new permission, amendment), [ADR-033: Audit trail hardening](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-015: Flyway](../../../adrs/ADR-015-db-migration.md) |
| Sprint / Milestone | S10 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-12:**

- **Permission:** a new permission `solution:write`, not admin-class. The built-in role `enterprise-architect` gets it, and a new built-in role `solution-architect` holds `portal:read`, `entity:read`, `solution:write` and `solution-metamodel:write`. Both changes go into ADR-032 as an amendment, like `solution-metamodel:write` on 2026-11-04.
- **Pages:** a solution overview with a dialog "New solution", and a simple detail page that opens after creating.
- **Audit:** creating a solution writes the audit event `solution.created`.

**Creating a solution (`createSolution`, new):**

- **Who:** `solution:write`. The account must be a person (owner and creator are persons); the local system administrator account gets 403.
- **Input:**
  - `name`: 1 to 255 characters after trimming, stored trimmed.
  - `description`: the architecture vision, optional, at most 2000 characters.
- **Name check:** unique within the instance, compared case-insensitively after trimming. A taken name answers 422 naming `name`, with the detail "Name '{name}' is already taken". Nothing is stored.
- **Result:** 201 with the solution:
  - status `draft`;
  - owner and creator are the caller;
  - `fromPlateauId` and `toPlateauId` are null (project mode).
- **Audit:** `solution.created` (category `change`, severity `info`, target `solution` with the ID, details `name`).

**Reading solutions (`listSolutions`, `getSolution`, new):**

- **Who:** `entity:read`.
- **List:** all solutions ordered by name (case-insensitive), with status, owner name and creation time.
- **Detail:** one solution with its architecture vision; an unknown ID answers 404.

**Database:**

- New table `solutions` (migration V25):
  - name, description, status, both plateau references, owner, creator and creation time;
  - BR-01 as a check: both plateau references are set or both are null;
  - a unique index on the trimmed, lower-case name.
- The plateau references get their foreign keys with the plateaus in S14.
- The permission `solution:write` and the role `solution-architect` are added to the catalog tables.

**Frontend:**

- **New page "Solutions" (SCR-060)** for `entity:read`: the list of solutions, and the button "New solution" with its dialog for `solution:write`.
- **New page "Solution" (SCR-061):** name, status, owner, creation time and the architecture vision. Creating a solution opens it.
- A link "Solutions" in the header for `entity:read`.

**Manual:** a new page on solutions, in English and German.

**Realigned sections:**

- **US-038 AC2 and REQ-038 AC4 "validation error":** 422 naming `name`.
- **US-038 AC5 "403; no access to the form":**
  - the API answers 403;
  - the page hides "New solution" without `solution:write`, but still lists the solutions to readers.
- **REQ-038 Processing "across all non-archived solutions":** solutions cannot be archived yet, so the name is unique across all solutions. The rule is revisited with the archive status in S12.
- **"Solution architect permission" (US-038, REQ-038):** this is `solution:write`.
- **`metamodelExtensionId` (business object, data model):** not stored on the solution. An extension already names its solution in `metamodel_configurations.solution_id` (US-037); the data model drops the duplicate column.

## Out of Scope

- **Plateau mode:** US-038 AC3 and AC4, REQ-038 AC2 and AC3, BR-02, the plateau dropdowns and the foreign keys to `plateaus` follow with the plateaus in S14.
- **Changing solutions:** editing and the status transitions (S12).
- **Archiving:** deleting and archiving solutions.
- **Extension foreign key:** `metamodel_configurations.solution_id` stays a reference without a foreign key. Existing extensions name solution IDs that do not exist, and US-037 tests create extensions for random IDs. A follow-up issue adds the foreign key and a 422 for an unknown solution.

## Capabilities

### New Capabilities

- `solution/solution-creation`: creating a solution in project mode: the permission `solution:write` and the role `solution-architect`, the validation of name and architecture vision, the result and its audit.
- `solution/solution-overview`: reading solutions through the API, the page "Solutions" with the dialog "New solution", and the page "Solution".

### Modified Capabilities

None.

## Impact

- **`api/openapi.yaml`:**
  - `listSolutions`, `createSolution` and `getSolution` (new, tag `Solutions`);
  - the schemas `Solution`, `SolutionSummary`, `SolutionCreate` and `SolutionStatus`;
  - `solution:write` in `x-oea-permissions`, `solution-architect` and the changed `enterprise-architect` in `x-oea-builtin-roles`.
- **`backend/`:**
  - migration `V25__solutions.sql`;
  - `app/solution/Solutions` (create, list, find, audit) and `api/solution/SolutionController`;
  - unit, integration and Cucumber tests.
- **`frontend/`:** the pages SCR-060 and SCR-061 with stories and images, the API client, the router, the header link, and i18n in English and German.
- **`manual/`**, in English and German.
- **Documentation:**
  - ADR-032 (amendment 2026-11-12);
  - US-038, REQ-038, REQ-157;
  - the audit event catalog (`solution.created`);
  - `docs/data-model.puml` and `business-objects/solution.md`;
  - `docs/screens/SCREENS.md` (SCR-060, SCR-061);
  - the sprint plan.

# Design

## Context

See proposal.md – Why. The current state:

- **No solutions:** there is no `solutions` table, no API operation and no page. `docs/data-model.puml` already describes the table, with a `metamodel_extension_id` column and foreign keys to `plateaus`, which do not exist yet (S14).
- **Extensions:** metamodel extensions name a solution in `metamodel_configurations.solution_id` without a foreign key (US-037, V20). The comment in V20 announces the foreign key "with solutions (S10)".
- **Permissions:** the permission catalog lives in `permissions` and `role_permissions` (V3, V20) and is mirrored in `x-oea-permissions` and `x-oea-builtin-roles` of the contract; a contract test compares both. No permission covers solutions.
- **Patterns to follow:**
  - `Catalogs.create`: a unique, trimmed, case-insensitive name answered with 422 and audited as `catalog.created`.
  - `CatalogsPage.vue` with the dialog SCR-028: a list with a create dialog.

## Goals / Non-Goals

**Goals:**

- A solution exists as its own table with the lifecycle columns that S11 to S14 build on.
- Solutions are read like other EA content (`entity:read`); writing them is a separate, unprivileged permission.

**Non-Goals:**

- No plateau mode, no editing, no status transitions, no archive.
- No change to metamodel extensions.

## Architecture Constraints

- **ADR-028:** `api/solution/SolutionController` implements the generated `SolutionsApi`; `app/solution/Solutions` holds the rules; plain JDBC like `Catalogs`.
- **ADR-032:** a new permission needs an amendment (D1); the controller declares `x-oea-authorization`, which the contract test checks.
- **ADR-033:** `solution.created` is written through `AuditWriter` in the creating transaction.
- **ADR-015:** the schema comes with the Flyway migration V25.
- **ADR-031:** both pages get screen stories with play functions; CI renders the images.
- **ADR-019 (soft delete):** not needed yet, since solutions cannot be deleted; archiving uses the status (S12).

## Decisions

### D1 – Permission `solution:write` and role `solution-architect`

- **`solution:write`:**
  - not admin-class, so holding it requires no second factor beyond the instance policy;
  - granted to `enterprise-architect`, which models today and keeps doing so (SH-03, SH-04).
- **New built-in role `solution-architect`:**
  - permissions: `portal:read`, `entity:read`, `solution:write` and `solution-metamodel:write`;
  - not assignable to service accounts;
  - for persons who run initiatives without changing the shared landscape directly. `entity:write` stays out: entity changes of a solution go through its deltas (S11).
- **ADR-032:** a new section "Amendment 2026-11-12: `solution:write`", the permission table and the matrix of §4 extended, and the header `Amended` line.
- **Migration V25:** inserts the permission (sort order after `solution-metamodel:write`), the role and its role permissions.
- **Contract:** `x-oea-permissions` and `x-oea-builtin-roles` are extended in the same order.
- **Alternative considered:** reusing `entity:write`. Rejected by the owner; a writer of the landscape is not automatically the owner of initiatives.

### D2 – Table `solutions` (V25)

```sql
CREATE TABLE solutions (
    id              UUID        PRIMARY KEY,
    name            TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
    description     TEXT        CHECK (length(description) <= 2000),
    status          TEXT        NOT NULL DEFAULT 'draft'
                                CHECK (status IN ('draft', 'proposed', 'approved', 'in-progress', 'implemented', 'archived')),
    from_plateau_id UUID,
    to_plateau_id   UUID,
    owner_id        BIGINT      NOT NULL REFERENCES persons (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT      NOT NULL REFERENCES persons (id),
    CONSTRAINT solutions_plateaus_both_or_none CHECK ((from_plateau_id IS NULL) = (to_plateau_id IS NULL))
);
CREATE UNIQUE INDEX solutions_name_unique ON solutions (lower(name));
```

- **Grants:** `SELECT` and `INSERT` on `solutions` for `oea_app`, as for the other tables.
- **Plateau columns:** created now, null in project mode, with BR-01 as a check. The foreign keys to `plateaus` follow in S14 with that table.
- **Name:** stored trimmed, so `lower(name)` is the case-insensitive comparison. The index is not partial: no solution can be archived yet (proposal, realigned sections).
- **`metamodel_extension_id`:** not created. An extension already points to its solution (`metamodel_configurations.solution_id`), so a second pointer could contradict it. The data model and the business object drop the attribute.
- **Alternative considered:** omitting the plateau columns until S14. Rejected: the response carries `fromPlateauId` and `toPlateauId` from now on (US-038 AC1), and adding nullable columns later would change the table twice.

### D3 – `Solutions` service

- **`create(principal, NewSolution)`:**
  1. Without a person ID (local system administrator account): `PermissionDeniedException` (403). The permission itself is checked by the authorization filter from `x-oea-authorization`.
  2. Trim the name; a blank name gives 422 naming `name`.
  3. Insert with a new UUID, status `draft`, owner and creator the person: `INSERT … ON CONFLICT DO NOTHING` against the unique index. No row means the name is taken: 422 naming `name` with the detail "Name '{name}' is already taken" (the trimmed input).
  4. Audit `solution.created`: category `change`, severity `info`, target `solution` with the ID, details `name`.
  5. Return the solution as `find` reads it.
- **`list()`:** all solutions with the owner's name (join on `persons`), ordered by `lower(name), id`.
- **`find(id)`:** one solution, or empty for 404.
- **Errors:** a new `InvalidSolutionException(field, detail)`, mapped by `ApiExceptionHandler` to 422 `urn:oea:problem:validation-failed` with `invalidFields`, like `InvalidAccessRequestException`.
- **Alternative considered:** a separate problem type `solution-name-taken`, like catalogs. Rejected: US-038 asks for a validation error naming the field, and the generic type with `invalidFields` is the newer convention (S10 changes 1 to 4).

### D4 – Frontend

- **SCR-060 "Solutions"** (`/solutions`, `SolutionsPage.vue`):
  - a `UTable` with name (link), status badge, owner and creation time; an empty state;
  - the button "New solution" only with `solution:write` (`me.permissions`);
  - a `UModal` with name (`UInput`) and architecture vision (`UTextarea`, counter up to 2000);
  - a rejection shows the problem's detail in the dialog, which stays open; a success routes to `/solutions/{id}`.
- **SCR-061 "Solution"** (`/solutions/:id`, `SolutionPage.vue`):
  - name as the heading, status badge, owner, creation time, and the architecture vision as plain text with kept line breaks;
  - "Not found" for a 404.
- **Header:** a link "Solutions" (`header-solutions`) for `entity:read`, next to "Catalogs".
- **API client:** `src/api/solutions.ts` on the generated client.
- **i18n:** keys `solutions.*` in `en.json` and `de.json`.
- **Screens:** stories `SCR-060-solutions.stories.ts` (list, empty, dialog, name taken, reader without "New solution") and `SCR-061-solution.stories.ts` (solution, not found) with play functions; test IDs where `UFormField` sets the labels. `SCREENS.md` gets a section "Client App – Solutions" with both screens as `implemented`.

## Data Model

- **Migration `V25__solutions.sql`:** the table of D2, the permission `solution:write`, the role `solution-architect` and the role permissions of D1.
- **`docs/data-model.puml`:**
  - `solutions` without `metamodel_extension_id`, with the unique index and the note that the plateau foreign keys follow in S14;
  - the relation from `metamodel_configurations` to `solutions` over `solution_id` replaces the one over `metamodel_extension_id`, still without a foreign key;
  - changelog entry.
- **`business-objects/solution.md`:** `metamodelExtensionId` replaced by the relation from the extension; notes on project mode as implemented; version 0.3.0.

## API

Delta to `api/openapi.yaml`:

- **Tag `Solutions`.**
- **`GET /solutions` (`listSolutions`):** `entity:read`; 200 with `SolutionSummary[]`.
- **`POST /solutions` (`createSolution`):** `solution:write`; body `SolutionCreate`; 201 with `Solution` and `Location`; 400 for the contract limits, 403, 413, 422 (`invalidFields` names `name`), 429.
- **`GET /solutions/{id}` (`getSolution`):** `entity:read`; 200 with `Solution`; 404.
- **Schemas:**
  - `SolutionCreate`: `name` (1–255), `description` (≤ 2000), no plateau fields yet;
  - `SolutionStatus`: the six statuses;
  - `SolutionSummary`: `id`, `name`, `status`, `ownerId`, `ownerName`, `createdAt`;
  - `Solution`: the summary plus `description`, `fromPlateauId`, `toPlateauId` (nullable, null in project mode), `createdBy`.
- **Authorization catalog:** `solution:write` in `x-oea-permissions`; `solution-architect` added and `enterprise-architect` extended in `x-oea-builtin-roles`.

## Risks / Trade-offs

- **[The name index is not partial; once archiving exists, an archived solution's name stays taken]** → S12 replaces the index with one over non-archived solutions, as REQ-038 asks.
- **[Owners are fixed to the creator; a solution of a person who leaves keeps that owner]** → acceptable until editing exists (S12); the owner is still listed by name.
- **[`metamodel_configurations.solution_id` can still name a missing solution]** → a follow-up issue adds the foreign key and adjusts the US-037 tests.
- **[A new built-in role changes `listAuthorizationRoles` and the role counts in existing tests]** → the tests that count built-in roles are updated in task group 1.

## Migration Plan

V25 creates the table and the catalog rows; it touches no existing data. A rollback leaves the table and the role in place; the previous version ignores them, except that it lists `solution-architect` and `solution:write` in the role catalog, which it reads from the tables.

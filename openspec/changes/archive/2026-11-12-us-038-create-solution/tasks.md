# Tasks

## 1. Contract, permission and migration

- [x] 1.1 `api/openapi.yaml`:
  - tag `Solutions` with `listSolutions`, `createSolution` and `getSolution`;
  - the schemas `SolutionCreate`, `SolutionStatus`, `SolutionSummary` and `Solution` (design, API);
  - `solution:write` in `x-oea-permissions` after `solution-metamodel:write`;
  - `solution-architect` added and `enterprise-architect` extended in `x-oea-builtin-roles`.

  Verify: Redocly lint passes, and the generated `SolutionsApi` compiles.
- [x] 1.2 Flyway migration `V25__solutions.sql`:
  - the table `solutions` with BR-01 as a check, the unique index on `lower(name)` and the grants (design D2);
  - the permission `solution:write`, the role `solution-architect` and the role permissions (design D1).

  Verify: the migration test applies V1 to V25 on an empty database. A row with only `from_plateau_id` set is rejected by the check, and a row with both or none set is accepted.
- [x] 1.3 ADR-032 amendment 2026-11-12 (design D1):
  - the `Amended` line;
  - the permission table and the matrix of §4;
  - the role intent of `solution-architect`;
  - REQ-157 lists the permission.

  Verify: the contract test comparing `x-oea-permissions` and `x-oea-builtin-roles` with the tables passes. Tests that count permissions or built-in roles are updated to 22 permissions and 10 roles.
- [x] 1.4 Cucumber scenario "Built-in roles" (solution-creation): `listAuthorizationRoles` lists `solution-architect` with exactly its four permissions, not privileged, not assignable to service accounts, and `enterprise-architect` with `solution:write`. Verify that it passes.

## 2. Backend: create and read solutions

- [x] 2.1 `app/solution/Solutions` with `create`, `list` and `find`, `InvalidSolutionException` mapped to 422 `validation-failed` with `invalidFields`, and `api/solution/SolutionController` (design D3).

  Verify with `SolutionTests` (integration):
  - creation answers 201 with status `draft`, owner and creator the caller, both plateau references null, and `Location`;
  - the name is stored trimmed;
  - a taken name, also differing in case and spaces, answers 422 naming `name` with the detail "Name '…' is already taken", and nothing is stored;
  - a blank name answers 422 naming `name`;
  - limits as a parameterized test: a name of 255 characters is accepted, 256 rejected with 400; a vision of 2000 characters is accepted, 2001 rejected with 400; an empty name (0 characters) is rejected with 400;
  - without `solution:write` (viewer) 403 and nothing stored; the local system administrator account 403;
  - `solution-architect` and `enterprise-architect` both may create.
- [x] 2.2 Audit `solution.created` (category `change`, severity `info`, target `solution`, details `name`).

  Verify: one event per creation with these fields, and no event for a rejected creation (taken name, blank name, 403).
- [x] 2.3 Reading.

  Verify with `SolutionTests`:
  - `listSolutions` orders case-insensitively by name and carries status and owner name;
  - `getSolution` returns the vision, the plateau references and the creator;
  - an unknown ID answers 404;
  - `portal-reader` without `entity:read` gets 403 on both operations.
- [x] 2.4 Cucumber feature `features/solution/solution-creation.feature` with the scenarios of the solution-creation spec (solution created, name stored trimmed, name taken, limits of name and vision, blank name, without permission, local system administrator account) and `features/solution/solution-overview.feature` with "Listed solutions", "Unknown solution" and "Without entity:read". Verify that they pass.

## 3. Frontend: pages "Solutions" and "Solution"

- [x] 3.1 API client `src/api/solutions.ts`, the routes `/solutions` and `/solutions/:id`, and the header link `header-solutions` for `entity:read`.

  Verify (`AppHeader.spec.ts`): the link is shown with `entity:read` and hidden without it.
- [x] 3.2 `SolutionsPage.vue` (SCR-060) with the dialog "New solution" (design D4).

  Verify (`SolutionsPage.spec.ts`):
  - the list shows name, status, owner and creation time and links to the solution;
  - the empty state;
  - "New solution" is shown with `solution:write` and hidden for a viewer;
  - a successful creation routes to `/solutions/{id}`;
  - a 422 keeps the dialog open with the detail;
  - the vision counter allows 2000 characters and blocks saving at 2001;
  - saving is disabled for a blank name.
- [x] 3.3 `SolutionPage.vue` (SCR-061).

  Verify (`SolutionPage.spec.ts`):
  - name, status, owner, creation time and the vision with its line breaks are shown;
  - a 404 shows "not found".
- [x] 3.4 i18n keys `solutions.*` in `en.json` and `de.json`. Verify: the i18n completeness test passes.
- [x] 3.5 Screen stories `SCR-060-solutions.stories.ts` (list, empty, dialog, name taken, reader) and `SCR-061-solution.stories.ts` (solution, not found) with play functions; images rendered; `docs/screens/SCREENS.md` gets the section "Client App – Solutions" with SCR-060 and SCR-061 as `implemented` and the priority overview.

  Verify: `npm run test:stories` passes and `python3 scripts/check_screens.py` passes.

## 4. Manual

- [x] 4.1 New page `usage/solutions.md` in `manual/en/` and `manual/de/`:
  - creating a solution;
  - the name rule;
  - project mode, and that plateau mode follows;
  - the roles `solution-architect` and `enterprise-architect`;
  - screenshots from the walkthrough (`solutions`, `solution`).

  `administration/roles-and-permissions.md` lists `solution:write` and `solution-architect`.

  Verify: the manual build and its link check pass, and the walkthrough spec shoots both screens.

## 5. Wrap-up

- [x] 5.1 US-038:
  - status `done`, with the implementation and the deferred AC3 and AC4 (S14);
  - REQ-038: version bump, realigned sections and implementation note;
  - REQ-157: implementation note.
- [x] 5.2 Synchronize:
  - `docs/data-model.puml` and `business-objects/solution.md` (design, Data Model);
  - the audit event catalog (`solution.created`);
  - `docs/screens/SCREENS.md`;
  - the sprint plan status of S10 change 5.

  Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 5.3 Open the follow-up issue for the foreign key of `metamodel_configurations.solution_id` (proposal, Out of Scope) and link it in the design notes of US-038.
- [x] 5.4 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --all --strict` and verify they pass.

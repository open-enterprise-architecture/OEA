# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design, section API):
  - implement `createAuthorizationRole`, `getPropertyVisibilityOverride` and `updatePropertyVisibilityOverride`;
  - describe the override in `queryCatalog` and `queryPortalCatalog`.

  Verify with Redocly lint, `python3 scripts/check_openapi_authorization.py` and `ContractViolationStatusTests`.

## 2. Backend: custom roles

- [x] 2.1 Add `AuthorizationRoles` and `createAuthorizationRole` (D4). No migration is needed. Verify in `AuthorizationRoleTests`:
  - "Role without permissions" (201, listed, not built-in, `role.created` with severity `info`);
  - "Role with permissions": the assigned person holds exactly `entity:read` and `portal:read`;
  - a privileged custom role is audited with severity `high`;
  - "Role as read role" is accepted by `updatePropertyVisibility`;
  - "Separation of duties": `metamodel:write` with `visibility:configure` and with `visibility-scope:assign` (422);
  - "ID taken": a built-in ID and a custom ID (409);
  - "Unknown permission" (422);
  - "Admin-class permission for service accounts" (422), while the same role not assignable to service accounts is accepted;
  - "Metamodel administrator" without `role:assign` (403, audited `authz.denied`);
  - nothing is stored on any rejection.

  Cover the limits with a parameterized test:
  - name of 100 characters accepted, 101 rejected (400); an empty name rejected;
  - ID of 63 characters and of 2 characters accepted, 64 and 1 rejected (400);
  - 50 permissions accepted in the contract check, 51 rejected (400);
  - a repeated permission counts once.
- [x] 2.2 Cucumber: `features/identity/authorization-roles.feature` with "Role without permissions" and "Separation of duties". Verify with the `CucumberIT` run.

## 3. Backend: override role list

- [x] 3.1 Add `VisibilityOverride` with `getPropertyVisibilityOverride` and `updatePropertyVisibilityOverride` (D1). Verify in `VisibilityOverrideTests`:
  - "Empty by default";
  - "Add a role" (`overridesPropertyVisibility: true` in `listAuthorizationRoles`, false after removal);
  - "Without visibility:configure": `metamodel-admin` and `system-admin` (403, list unchanged);
  - "Unknown role" (422, list unchanged);
  - "Change audited" with old and new list and `selfBenefiting: false`;
  - "Self-benefiting change" (`true`);
  - setting the same list again writes no event.

  Limits: 20 roles accepted, 21 rejected (400); a repeated role counts once; the empty list accepted.
- [x] 3.2 Add the override to `PropertyAccess` (D2). Verify in `PropertyAccessTests`:
  - with an override role, `readable` is true for `role-restricted` and `connection-scoped`;
  - `throughOverride` is false with a read role and for `public`;
  - `writable` is unchanged;
  - without an override role, nothing changes, including for `system-admin` and `security-admin` ("No implicit override");
  - `of(Set<String>)` has no override.
- [x] 3.3 Apply the override in `CatalogQueries` and write `property-override-access` (D3), with `ScopeStore.inScope` for a set of IDs. Verify in `OverrideReadTests`:
  - "Override effective" on a `role-restricted` and a `connection-scoped` column, sorted and filtered by them;
  - "One event per query", naming the property and the entity IDs of the page;
  - a join column through the override is named with the IDs of the join results;
  - "Value readable anyway" (no event);
  - "Scope grants part of the rows" (only entities outside the scope);
  - an empty page, and a page whose overridden column has only empty values, write no event;
  - a query rejected with 400 writes no event;
  - "Override role in the portal" (`channel: portal`);
  - "No object access" (403 without `entity:read`);
  - "No write through the override" (403 in `EntityBatch`).

  Boundary: a page of 200 rows (the maximum) names all 200 entities in one event.
- [x] 3.4 Cucumber: "Override role" in `features/catalog/catalog-query.feature` and `features/metamodel/visibility-override.feature` with "Add a role" and "Without visibility:configure". Verify with the `CucumberIT` run.

## 4. Frontend

- [x] 4.1 Add the page SCR-039 "Authorization roles" with `src/api/roles.ts`, the route, the header link for `role:assign` and the i18n keys in English and German (D5). Verify with `AuthorizationRolesPage.spec.ts`:
  - the roles with their markers are listed;
  - "List and create": the request is sent and the list is reloaded;
  - "Rejected definition": 422 and 409 keep the dialog open with the message;
  - the link is missing without `role:assign`.

  Add the story `SCR-039-authorization-roles.stories.ts` with its images and the `SCREENS.md` entry, and verify with `npm run test:stories` and `python3 scripts/check_screens.py`.
- [x] 4.2 Add the page SCR-040 "Override roles" with the override functions in `src/api/visibility.ts`, the route, the header link for `visibility:configure`, the link from SCR-032 and the i18n keys (D5). Verify with `OverrideRolesPage.spec.ts`:
  - "Change the list on the page" (the request is sent);
  - a rejected save keeps the previous list;
  - the empty-list hint is shown only while the list is empty;
  - the link is missing without `visibility:configure`.

  Add the story `SCR-040-override-roles.stories.ts` with its images and the `SCREENS.md` entry, and verify as in 4.1.

## 5. Manual

- [x] 5.1 Update in English and German:
  - a new page on authorization roles: the list, creating a role, the rules and the API;
  - the property visibility page: the override roles, their audit and the API;
  - the catalogs and portal pages: values through the override.

  Verify with `python3 scripts/check_manual.py` and the manual build.

## 6. Wrap-up

- [x] 6.1 Update the documentation:
  - US-128 and US-151 `done`, with the realignments of the proposal;
  - implementation notes in REQ-128, REQ-157, REQ-160 and REQ-161;
  - the A5 story link in UC-23;
  - `role.created`, `visibility.override-changed` and `property-override-access` in `docs/audit-event-catalog.md`;
  - the notes in `docs/data-model.puml` and `business-objects/authorization-role.md`;
  - SCR-039 and SCR-040 in `docs/screens/SCREENS.md`;
  - the status of S10 change (4) in the sprint plan.

  Leave the hash for the archive PR.
- [x] 6.2 Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 6.3 Run `./mvnw verify`, the frontend type-check, tests, build and story tests, markdownlint, `openspec validate --specs --strict` and all repository checks, including `python3 scripts/validate_links.py`.

# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D2):
  - `setEntityParent`, `createStructuringAid` and `deleteStructuringAid` with their schemas;
  - `isComposition` on the metatype schemas and the package metatype;
  - `parentEntityId` on `listBrowserItems`;
  - `childCount`, `structuringAid` and `version` on `BrowserItem`;
  - `parentEntityId` among the system fields of `FieldDiff` and `EntityVersionChange`.

  Security (D7): the three writes declare `entity:write`, `deleteStructuringAid` also `entity:delete`; `If-Match` is required on `setEntityParent` and `deleteStructuringAid`; `name` has `minLength: 1` and `maxLength: 255`, `description` `maxLength: 2000`, and `parentEntityId` `minimum: 1` (body and query); the error responses are listed as in D2.

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and `python3 scripts/test_check_openapi_authorization.py` pass.

## 2. Backend

- [x] 2.1 `V42__entity_hierarchy.sql` (D1), with the built-in metatype constant, and the history readers of the field "Parent" (`EntityVersionStore`, `EntityVersions`, `EntityHistory`; not restored by `EntityRestore`).

  Verify with a migration test:
  - the built-in metatype exists once in the instance configuration;
  - a second `is_composition` in one configuration and `is_composition` on an element metatype are rejected by the database;
  - existing entities keep `parent_entity_id` null;
  - the built-in role `solution-architect` has `entity:write` and not `entity:delete`;
  - `entity_system_field_history` accepts `parent_entity_id` and still rejects an unknown field name.
- [x] 2.2 `EntityHierarchy` with its store (D3) and the controller.

  Verify with `EntityHierarchyTests`:
  - **setting, clearing and the same parent again:**
    - set and clear the parent: a new version each, with `parentEntityId` ("Parent", by name) in `getEntityHistory`, `getEntityVersion` and the diff;
    - a restore of the version before leaves the parent unchanged;
    - the parent keeps its version;
    - the same parent again writes no version and no event;
  - **rejected parents** (parameterized), 422 naming `parentEntityId`:
    - the entity itself, a child, a grandchild, a connection, a deleted entity, an unknown ID;
    - a connection as child gives 422 naming `entityId`;
  - **depth:** placing under an entity at level 9 is accepted (level 10), under one at level 10 is rejected; moving a subtree of 3 levels (the child included) under level 7 is accepted, under level 8 is rejected;
  - **`parentEntityId`:** 1 is accepted as a value (422 when unknown), 0 gives 400;
  - **`If-Match`:** missing gives 428, stale gives 412, and nothing changes;
  - **authorization:** 403 without `entity:write`, for all three writes; 403 for deleting an aid with `entity:write` but without `entity:delete`;
  - **blocking:** 409 for a blocked child; deleting an aid with a blocked child gives 409, and nothing changes;
  - **composition:**
    - created with `autoCreated=true` when allowed;
    - not created under a structuring aid, with a structuring aid as child, without a marked metatype, or for a pair not allowed, each with its reason;
    - a subtype of an allowed metatype is accepted;
  - **removal:**
    - detaching removes the auto-created connection;
    - a move removes the old one and creates a new one;
    - a connection written through the batch stays;
    - an auto-created connection that was already deleted causes no error;
    - the removal writes `entity.deleted` with `cause` = `parent-changed`;
  - **composition with a mandatory property:** the connection is created and the property is marked as missing;
  - **concurrency:** two opposite parent changes run in two threads; exactly one succeeds, and no cycle exists afterwards;
  - **audit:** `entity.parent-changed` with IDs only; no event on a rejection.
- [x] 2.3 Structuring aids (D3).

  Verify with `EntityHierarchyTests`:
  - **creating:**
    - at the root level and under a parent;
    - the name at 255 is accepted, at 256 and blank rejected; the description at 2000 is accepted, at 2001 rejected;
  - **deleting:**
    - with 3 children, the children move to the root level with a new version each and are not deleted;
    - deleting a non-aid gives 422 naming `entityId`;
    - an aid already deleted gives 404;
  - **batch:** an item of the structuring-aid metatype is rejected with 422 naming `metatypeId`;
  - **solution:** capturing a delta `new` of the structuring-aid metatype is rejected with 422 naming `metatypeId`;
  - **connections:** a connection from or to a structuring aid is rejected through the batch and `createConnection`;
  - **metatype protection:** renaming or deleting the built-in metatype, or mapping a property to it, gives 409 `read-only-metatype`; using it as base type gives 422 naming `baseMetatypeId`.
- [x] 2.4 Composition mark and package (D4).

  Verify:
  - **mark:** marking an organization connection metatype is audited and raises its version; a second mark, a mark on an element metatype and a mark in a solution extension give 422 naming `isComposition`;
  - **package import:**
    - a package with one composition metatype imports with the mark;
    - two in one package, or one on an element metatype, give 422;
    - a package whose composition metatype collides with another marked metatype gives 422;
  - **starter package:** 1.3.0 imports "Composition" marked, with the German label "Komposition"; `StarterPackagesTests`, `RepositoryInstance` and the other importing tests are updated;
  - **package base type:** a package metatype with the structuring aid as base type is rejected with 422.
- [x] 2.5 Browser hierarchy (D5).

  Verify with `BrowserTests`:
  - the root level holds elements without a parent or with a deleted parent;
  - `parentEntityId` lists the children by name;
  - `childCount` counts children that are not deleted;
  - `structuringAid` and `version` are set;
  - `parentEntityId` with `scope=solution` or with `category=connections` gives 422 naming `parentEntityId`; an unknown or deleted parent gives 404; 0 gives 400;
  - the count of "Components" stays the number of all elements.
- [x] 2.6 Cucumber:
  - `features/repository/entity-hierarchy.feature` with the scenarios of `repository/entity-hierarchy`;
  - the composition mark in the connection-metatype feature;
  - the browser hierarchy in `features/navigation/solution-browser.feature`.

  Logins on shared instances reuse existing sessions (see `PortalSteps.personWithoutRole`), never logging the same IdP user in twice.

  Verify that the new and the affected features pass.

## 3. Frontend

- [x] 3.1 Stories first:
  - `SCR-055-navigation-sidebar.stories.ts` with nested components, a structuring aid, a refused drop target, the dialog "Move to…" and the warning before deleting an aid;
  - `SCR-030-metamodel-configuration.stories.ts` with the switch "Composition" in `MetatypeDialog.vue`.

  Verify that the stories render without a11y violations.
- [x] 3.2 The browser hierarchy, drag and drop, "Move to…", "New structuring aid" and "Delete" (D5); `api/navigation.ts` and the fakes. Texts go in `i18n`, in English and German.

  Verify with the specs:
  - children load on expansion;
  - a drop sends `setEntityParent` with `If-Match`;
  - a drop on the element itself or on a loaded descendant is refused and sends nothing;
  - a 422 shows a toast and leaves the tree as it was;
  - "Move to…" offers the root level;
  - the delete warning names the number of children;
  - without `entity:write` there is no drag, "Move to…", "New structuring aid" or "Delete"; without `entity:delete` there is no "Delete";
  - names with markup are shown literally.
- [x] 3.3 The switch "Composition" in `MetatypeDialog.vue`, only for organization connection metatypes of the instance; `api/metamodel.ts` and `src/test/metamodel.ts`.

  Verify with the specs: the switch is shown and sent for connection metatypes, and is not shown for element metatypes.
- [x] 3.4 `docs/screens/SCREENS.md`: SCR-055 and SCR-030 name the additions.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation (D6):
  - **Manual:** "Navigation", "Metamodel editing" and "Metamodel packages", in English and German;
  - **Business objects and data model:** `business-objects/entity.md`, the metatype business object and `docs/data-model.puml`;
  - **Audit catalog:** `docs/audit-event-catalog.md` (`entity.parent-changed`, the causes of `entity.deleted`);
  - **Requirements:** UC-13, REQ-141 and REQ-142 realigned, and the note in ADR-021.
- [x] 4.2 Set US-135 and US-136 to `done`, and regenerate `requirements/traceability.md`.
- [x] 4.3 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-135-component-hierarchy --strict`;
  - `python3 scripts/check_manual.py`.

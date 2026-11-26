# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1, D2): `listDeletedEntities` with `DeletedEntity` and `DeletedEntityPage`; `includeDeleted` on `queryCatalog` with the condition in `x-oea-authorization` and the description (connection catalogs, joins, cursor); the optional `deleted`, `deletedAt` and `version` on `CatalogRow`. No Redocly ignore entry.

  Verify: Redocly lint, `check_openapi_authorization.py` and `check_openapi_conventions.py` with their tests.

## 2. Backend

- [x] 2.1 The list (D1), and the index migration if needed.

  Verify with tests:
  - the order and the cascade origin;
  - connections with their endpoints;
  - the filters, with `%` and `_` matched literally;
  - page sizes 1 and 200 accepted, 0 and 201 rejected; `q` of 255 accepted, 256 rejected;
  - cursor paging, and a cursor of another query rejected;
  - 403 without `entity:restore`;
  - an empty list.
- [x] 2.2 `includeDeleted` (D2).

  Verify:
  - deleted rows marked, with the same columns, visibility, filters and sorting;
  - the total includes them;
  - 403 without `entity:restore`, also for an unknown catalog, with `@ConditionsEnforcedByHandler` (probe role `viewer`) so that `HandlerEnforcedConditionsTests` covers it;
  - the default unchanged: no `deleted`, `deletedAt` or `version` members, and the same SQL;
  - a cursor of the other mode rejected with 400 `cursor`, in both directions;
  - joins show only active joined entities, a deleted row keeps one row without results in mode `rows`, and a join filter excludes it;
  - a connection catalog shows deleted connections with their deleted endpoints;
  - paging with `includeDeleted`: page sizes 1 and 200 accepted, 201 rejected;
  - the portal and the dashboard table widgets unchanged;
  - the latency measurement of `catalog/catalog-query-latency` still passes.
- [x] 2.3 Cucumber: the scenarios of both delta specs (API level). Use own metatypes and names with a unique prefix, and never assert on global counts. The list scenarios filter by the scenario's own metatype; the empty state ("No deleted entities.") is checked in the frontend spec and, in the backend, with a metatype without deleted entities.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes.
- [x] 3.2 `DeletedEntitiesPage.vue` (SCR-068), the header entry, the catalog switch and the row action (D3), with `i18n` in en and de.

  Verify with specs:
  - filters, "Load more", restore with the notice, 409 and 412, the empty state, and no permission;
  - the switch only with `entity:restore`, deleted rows marked, restore from a row with the row's `version`, 409 and 412, and the switch not saved in a view;
  - the URL value of the switch kept on reload and dropped without `entity:restore`.
- [x] 3.3 Stories SCR-068 (list, empty, after restore) and SCR-020 (Show deleted).

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes, except for the images CI renders.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D4.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-deleted-entities --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.

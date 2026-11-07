# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design, section API):
  - add `cursor` to `queryCatalog` and `queryPortalCatalog`, and `nextCursor` to `CatalogQueryResult`;
  - add the response `QueryTimeout` (503, `urn:oea:problem:query-timeout`) to both operations;
  - update the descriptions.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Backend: time limits and metric

- [ ] 2.1 Add `oea.database.statement-timeout` and the Hikari `connectionInitSql` (D4). Verify with `StatementTimeoutTests`:
  - "Default limit on every connection" (`SHOW statement_timeout` = `1min`);
  - "Configured limit";
  - an invalid value refuses the start.

  Check that `Migrator` is unaffected ("Migration step unaffected").
- [ ] 2.2 Add `oea.catalog.query-timeout`, `SET LOCAL` in `CatalogQueries`, `CatalogQueryTimeoutException`, the 503 mapping and the audit event `catalog.query-timeout` (D4). Verify with `CatalogQueryTimeoutTests`, where a limit of 1 s meets a slow query (for example a filter calling `pg_sleep` through a test-only view or a locked row):
  - 503 `query-timeout` and the audit event;
  - a query within the limit unchanged.
- [ ] 2.3 Add the timer `oea.catalog.query` with `channel` and `outcome` (D5). Verify in `CatalogQueryTests` and `PortalCatalogsTests` that a query counts with `success`, a 400 with `rejected` and a portal query with channel `portal`.

## 3. Backend: keyset paging

- [ ] 3.1 Add sort keys as data and select the key values in `CatalogQueryStore.page` (D1). Verify that all existing order tests of `CatalogQueryTests`, `CatalogJoinsTests`, `CatalogFilterTests`, `CatalogViewTests` and `ConnectionCatalogTests` stay green.
- [ ] 3.2 Add the keyset predicate and the row comparison for the default order (D2), and `CatalogCursors` with HMAC, fingerprint and type checks (D3). Wire `cursor` and `nextCursor` through `CatalogQueries`, `CatalogController` and `PortalController`. Verify with `CatalogCursorTests`:
  - "Next page by cursor";
  - "Cursor with ties, empty values and rows mode": walk every page by cursor and compare with `page` for sorts by name, by an enum and by a number, both directions, rows mode with 2 joins and a filter;
  - "Last page";
  - "Cursor of another query" (sort, filter, catalog, page size, changed columns);
  - "Tampered cursor" (changed value, wrong HMAC, garbage, too many values, a value of the wrong type) and a cursor without `page`, all 400 `cursor`;
  - the portal query with the cursor.
- [ ] 3.3 Cucumber: scenarios "Next page by cursor" and "Cursor of another query" in `features/catalog/catalog-query.feature`, and "Next page through the cursor" in `features/portal/portal-catalogs.feature`. Verify with the `CucumberIT` run.

## 4. Backend: latency

- [ ] 4.1 Add migration `V22__catalog_query_indexes.sql` with `entities_active_name` (D7). Verify with a schema test that the index exists and that the plan of the default first page uses it (`EXPLAIN`).
- [ ] 4.2 Add `CatalogQueryLatencyIT` (D8) with the four scenarios of `catalog/catalog-query-latency`, run in `verify`.
- [ ] 4.3 Run the measurement. Where a target is missed, analyse the plan (`EXPLAIN ANALYZE`) and add an index or rewrite the statement, then record the decision in the design. Verify that all four scenarios pass locally and in CI, and record the measured 95th percentiles.

## 5. Frontend

- [ ] 5.1 Add `cursor` to `queryCatalog` and `queryPortalCatalog`, and use the cursor for the next page in `CatalogPage` and `PortalCatalogPage`, with the fallback after 400 `cursor` (D6). Verify with tests in `CatalogPage.spec.ts` and `PortalCatalogPage.spec.ts`:
  - "Paging forward in the table" (the request carries `cursor` and `page`; the URL holds only `page`);
  - a jump uses `page` only;
  - a rejected cursor falls back to `page`.

## 6. Manual

- [ ] 6.1 Add `oea.database.statement-timeout` and `oea.catalog.query-timeout` to the installation page, name the 503 "Query took too long" on the catalogs page, and add `cursor` and `nextCursor` under "Through the API" on the catalogs and portal pages, in English and German. Verify with `python3 scripts/check_manual.py` and the manual build.

## 7. Wrap-up

- [ ] 7.1 Update the documentation:
  - realign REQ-071 (operation path, cursor, CI verification, implementation section with the measured values, verification status `verified`);
  - add implementation notes to REQ-046 and US-049;
  - add `catalog.query-timeout` to `docs/audit-event-catalog.md`;
  - add the index to `docs/data-model.puml`;
  - add the status of change (7) to the sprint plan.

  Leave the hash for the archive PR.
- [ ] 7.2 Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`). No change to `docs/screens/SCREENS.md` is needed.
- [ ] 7.3 Run `./mvnw verify`, the frontend type-check, tests, build, story tests and `test:csp`, markdownlint, `openspec validate --specs --strict` and all repository checks, including `python3 scripts/validate_links.py`.
- [ ] 7.4 Close #66 through the implementation PR (`Closes #66`).

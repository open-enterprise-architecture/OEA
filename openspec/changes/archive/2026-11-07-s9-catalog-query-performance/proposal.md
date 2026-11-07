# Proposal

## Why

The catalog query pages with `OFFSET`, which gets slower on deep pages of large metatypes, and the latency targets of [REQ-071](../../../requirements/req/REQ-071-catalog-query-latency.md) have never been measured ([#66](https://github.com/open-enterprise-architecture/OEA/issues/66)). In addition, no query of the application has a time limit. A catalog query with several joins, filters and rows mode can run for a long time and tie up a database connection. The owner's SQL review of 2026-11-06 (finding L1) asked for a statement timeout.

This is S9 change 7 of the cut of 2026-11-05 (see `us-048-catalog-joins`): performance (#66), measured against the finished query, which now includes joins, filters, views and the portal.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) |
| Requirements | [REQ-071: Catalog query latency](../../../requirements/req/REQ-071-catalog-query-latency.md), [REQ-046: Catalog query](../../../requirements/req/REQ-046-catalog-query.md) (AC5 paging), [REQ-186: Rate limits and resource quotas](../../../requirements/req/REQ-186-rate-limits-resource-quotas.md) |
| User Stories | [US-049: Show catalog data](../../../requirements/user-stories/US-049-show-catalog-data.md) (AC5 paging) |
| Issues | [#66: keyset paging and latency target for the catalog query](https://github.com/open-enterprise-architecture/OEA/issues/66) |
| ADRs | [ADR-013: API Style](../../../adrs/ADR-013-api-style.md) (spec-first), [ADR-024: Audit Separation](../../../adrs/ADR-024-audit-separation.md) (audit) |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-07:

- **Keyset paging now, not only if the measurement requires it.**
  - The catalog query and the portal query accept an optional `cursor`, and their result carries `nextCursor`.
  - With a cursor, the next page starts after the last row of the previous one instead of skipping rows with `OFFSET`.
  - `page` stays: it still jumps to any page and labels the page a cursor leads to. The contract stays compatible.
- **Two statement time limits:**
  - a global emergency limit on every statement of the application (default 60 s);
  - a tighter limit for catalog and portal queries (default 10 s).
  - A catalog query above its limit is answered with 503 `urn:oea:problem:query-timeout` and audited.
- **The latency test is a CI gate.** A measurement in the backend `verify` checks the four targets of REQ-071 with 10,000 primary entities, like `LoginLatencyIT` does for logins.

Further scope:

- **Cursor:**
  - It is opaque (base64url) and holds the sort values of the last row, together with a fingerprint of the query: catalog, columns or view, sort, join modes, filters and page size.
  - A cursor of another query, a tampered cursor or a cursor without `page` is rejected with 400 naming `cursor`.
  - The cursor holds only values of columns the caller sees.
- **Metric:** every catalog query records its server-side processing time as `oea.catalog.query`, tagged with the channel (`catalog` or `portal`) and the outcome.
- **Indexes:** a migration adds the indexes that the plans of the measured queries need, at least `entities (metatype_id, lower(name), id) WHERE deleted_at IS NULL` for the default order. The connection indexes REQ-071 names already exist (`entity_connections` by source and by target).
- **Frontend:** the catalog page and the portal catalog page pass `nextCursor` when moving to the next page. Jumps, reloads and links use `page`, so the URL is unchanged.
- **Configuration:**
  - `oea.database.statement-timeout` (default `60s`) and `oea.catalog.query-timeout` (default `10s`);
  - the installation page of the manual names both.

Realigned sections, which were non-binding until this change:

- **REQ-071:**
  - it names the query `GET /catalogs/{id}/query` instead of `/data`;
  - "cursor-based pagination" becomes the optional `cursor`;
  - the verification is integrated in CI;
  - the implementation section gets the test and the measured values.
- **REQ-046 AC5 and US-049 AC5:** paging by `page` stays, with `cursor` as the fast path to the next page.

Assumptions, open for the owner's review:

- **The cursor only goes forward.** "Previous page" uses `page`; pages near the start are cheap with `OFFSET`.
- **The total is still counted on every query** (`totalElements`, `totalPages`). The measurement includes the count. If the count misses the target, it is optimized in this change rather than dropped from the contract.
- **The emergency limit also applies to package imports and batch writes.** At 60 s, none of the current tests comes close.
- **Measurement on the CI runner:** the targets apply to the server-side processing time (the timer). The test seeds 10,000 entities once, warms up and measures 30 queries per scenario.

## Out of Scope

- Caching of query results, which REQ-071 rules out.
- Backward cursors and cursor-only paging without a total.
- Latency targets for other read paths, such as the navigation tree or the metamodel.

## Capabilities

### New Capabilities

- `catalog/catalog-query-latency`: measuring the processing time of catalog queries and the latency they guarantee at 10,000 primary entities (REQ-071).

### Modified Capabilities

- `catalog/catalog-query`: new requirements "Next page through a cursor" and "Query time limit"; the requirement "Pagination" stays as it is.
- `portal/portal-catalogs`: the portal query accepts the cursor like the catalog query.
- `platform/request-limits`: a new requirement "Statement time limit" for every statement of the application.

## Impact

- `api/openapi.yaml`:
  - the parameter `cursor` on `queryCatalog` and `queryPortalCatalog`, and `nextCursor` in `CatalogQueryResult`;
  - 503 `query-timeout` on both operations, and `cursor` in the 400 description.
- `backend/`:
  - the keyset predicate in `CatalogQueryStore`, cursor encoding and checking in `io.oea.app.catalog`, the timer and the local timeout in `CatalogQueries`;
  - the global timeout of the connection pool and the exception mapping;
  - a migration with indexes, if the plans need them;
  - `CatalogQueryLatencyIT`.
- `frontend/`: `CatalogPage`, `PortalCatalogPage` and the API clients pass the cursor.
- `manual/`: the installation page names the timeouts, and the catalog page mentions `cursor` under "Through the API", in English and German.
- Documentation:
  - REQ-071 (realigned, implementation and measured values), REQ-046 and US-049 (implementation notes);
  - `docs/audit-event-catalog.md`;
  - the sprint plan; #66 is closed by the implementation PR.

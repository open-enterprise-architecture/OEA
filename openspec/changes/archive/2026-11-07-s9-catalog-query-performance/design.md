# Design

## Context

- **Query:**
  - `CatalogQueryStore.page` builds one SQL statement: the active entities of the metatype, value joins per column, `LEFT JOIN LATERAL` per rows join, the filter in `WHERE`, `ORDER BY` and `LIMIT ? OFFSET ?`.
  - `count` runs the same `FROM`/`WHERE` with `count(*)`.
  - Aggregate joins are loaded per page with `aggregate(join, entityIds)`.
  - `CatalogQueries.query` (`@Transactional(readOnly = true)`) resolves columns, view, sort, join modes and filters and calls the store.
  - The portal query calls the same method (s9-portal-catalogs D1).
- **Order:** the `ORDER BY` is a fixed list of keys:
  - the sort expression, which for a property column is `eo.sort_order`, then the typed values `value_int`, `value_date`, `value_bool`, `value_ref`, then `lower(coalesce(value_enum, value_text))`, each `NULLS LAST`;
  - then `lower(e.name)` and `e.id`;
  - per rows join, `lower(jN.name)` and `jN.id`.
  - Every key is either never null or `NULLS LAST`.
- **Indexes:**
  - `entities_active (metatype_id) WHERE deleted_at IS NULL`;
  - `entity_connections_source` and `entity_connections_target`;
  - the unique index of `entity_property_values (entity_id, property_def_id)` from V6.
- **Time limits:**
  - No statement of the application has a time limit.
  - The connection pool is Spring Boot's Hikari default.
  - The migration step (`oea migrate`) opens its own Flyway connection (`Migrator`).
- **Measurement:** `LoginLatencyIT` (us-008) seeds data, measures the timer `oea.login.processing` before and after each request, and fails if the 95th percentile misses the target. It runs in the Maven `verify` phase (Failsafe).
- **Frontend:** `CatalogPage` and `PortalCatalogPage` keep `page` and `sort` in the URL and query with `page`; `CatalogTable` emits the page number.

## Goals / Non-Goals

**Goals:**

- forward keyset paging through an opaque cursor for the catalog and portal queries, compatible with `page`;
- the four REQ-071 targets, measured in CI;
- a statement time limit for the application and a tighter one for catalog queries.

**Non-Goals:**

- backward cursors;
- dropping the total;
- caching;
- latency targets for other read paths.

## Architecture Constraints

- **ADR-013:** the contract changes first. The new parameter and field are optional, so existing clients keep working.
- **ADR-028:** layers. The cursor format and checks live in `app.catalog`, and the keyset SQL lives in `core.catalog`.
- **ADR-024, ADR-033:** the timeout audit event goes through `AuditWriter.appendIndependently`, because the query transaction is already aborted when it is written.
- **REQ-160:** a cursor carries sort values of the last row. They are values of a visible column of a row the caller received, so the cursor reveals nothing new.
- **REQ-186:** the time limits complement the rate limits of `platform/request-limits`.

## Decisions

### D1 Sort keys as data

`CatalogQueryStore.page` derives a list of `SortKey(expression, type, descending)` from the sort, in the order of the `ORDER BY` above. The `ORDER BY` is rendered from that list, and so is the keyset predicate.

- **Types:** `type` is the SQL type of the key: `text`, `bigint`, `integer`, `numeric`, `date`, `boolean`, `uuid` or `timestamptz`. It is used to bind cursor values as `?::type`.
- **Key values:** the page statement also selects every key as `kN`, so the store returns the key values of each row next to the row. The last row's values become the cursor.
- **Behavior:** the order does not change, because the existing tests of the order stay green.

### D2 Keyset predicate

For keys `k1..kn` with values `v1..vn`, "after" is `OR` over `i` of (`k1 = v1 AND … AND k(i-1) = v(i-1) AND after_i`). Here `after_i` is:

- `ki > vi OR ki IS NULL` (ascending) or `ki < vi OR ki IS NULL` (descending), when `vi` is not null;
- `false`, when `vi` is null, because only nulls follow a null with `NULLS LAST`.

Equality for a null `vi` is `ki IS NULL`.

The predicate is added to the `WHERE` with the filter, and `OFFSET` is 0.

For the default order (name ascending, no rows join), the store uses the row comparison `(lower(e.name), e.id) > (?, ?)`. Together with the new index of D7, PostgreSQL can then read the page from the index instead of sorting every entity.

The count stays the unchanged `count` statement, so the total is the same as with `page`.

### D3 Cursor

- **Class:** `io.oea.app.catalog.CatalogCursors` encodes and checks cursors.
- **Content:** base64url of a compact JSON `{"f": fingerprint, "k": [values as text or null]}` plus an HMAC-SHA256 (first 16 bytes) over it.
- **Key:** random per backend start, held in memory.
  - A cursor survives no restart. The client then gets 400 `cursor` and falls back to `page` (D6).
  - Cursors are short-lived navigation state, so no stored key is needed.
  - With several backend instances, a cursor works only on the instance that issued it. OEA runs one backend today; the risk is listed below.
- **Fingerprint:** SHA-256 over:
  - catalog ID, channel, the resolved column keys in order, view ID, sort, join modes, normalized filters and filter mode, page size;
  - the catalog's `lastModifiedAt`, so a changed configuration invalidates old cursors.
- **Checks:** a wrong HMAC, a wrong fingerprint, a wrong number of values, or a value that does not parse as its key's type adds `cursor` to the invalid parameters. So does a cursor without `page`. Values are parsed in Java before binding, so no database error reaches the caller.
- **Size:** `cursor` has at most 2,000 characters. A page whose cursor would be longer, because the sort values of its last row are long (for example a sort by a description of 2,000 characters), carries no `nextCursor`; the next page then loads by `page` alone.
- **Page:** the cursor also carries the number of the page it leads to. A cursor sent with another `page`, or without `page` (which is then 0), adds `cursor` to the invalid parameters.
- **Filters in the fingerprint:** the filters count without their order, because the order does not change the result.

### D4 Statement time limits

- **Global:**
  - `oea.database.statement-timeout` is a `Duration` (default `60s`, between 1 s and 1 h).
  - A `BeanPostProcessor` on the `HikariDataSource` sets `connectionInitSql` to `SET statement_timeout = <milliseconds>`. The value is validated at startup and rendered as a number, never as configured text.
  - The migration step uses its own Flyway connection, so it stays unaffected.
- **Catalog:**
  - `oea.catalog.query-timeout` is a `Duration` (default `10s`, between 1 s and the global limit).
  - `CatalogQueries.query` first runs `SET LOCAL statement_timeout = <milliseconds>` in its read-only transaction. The limit ends with the transaction.
- **Check of the catalog limit:** `StatementTimeoutConfiguration` refuses the start when `oea.catalog.query-timeout` exceeds `oea.database.statement-timeout`.
- **Transaction:** `CatalogQueries.query` runs the query in its own read-only transaction (`TransactionTemplate`), so the timer, the timeout handling and the audit event sit outside it. The portal query checks the scope of the catalog first and then calls the same method.
- **Timeout handling:**
  - A `DataAccessException` with an `SQLException` of SQL state `57014` (`query_canceled`) in its causes becomes `CatalogQueryTimeoutException`.
  - `ApiExceptionHandler` answers it with 503 and the problem type `urn:oea:problem:query-timeout`, with the title "Query took too long" and a hint to narrow filters or joins.
  - The audit event `catalog.query-timeout` has category `SECURITY`, severity `WARNING`, target `catalog` and details `channel` and `limitMillis`.

### D5 Metric

- **Timer:** `CatalogQueryMetrics` records `oea.catalog.query` with the tags `channel` (`catalog` or `portal`) and `outcome` (`success`, `rejected` or `timeout`). It is modelled on `LoginMetrics`.
- **Measured span:** the whole service call, including the count and the aggregate joins, but not the JSON mapping or the HTTP layer, as REQ-071 asks.
- **Outcome `rejected`:** invalid parameters (400) and a catalog that is not visible (404).

### D6 Frontend

- **API clients:** `queryCatalog` and `queryPortalCatalog` accept `cursor`.
- **`CatalogPage` and `PortalCatalogPage`:**
  - When the requested page is the current page + 1 and the result has a `nextCursor`, they send `cursor` with `page`. Otherwise they send only `page`.
  - A 400 naming `cursor` repeats the query without the cursor.
  - The URL keeps only `page`.
  - The cursor is remembered per query (sort, join modes, filters, view), so a cursor of another query is never sent.
- **Time limit:** a 503 `query-timeout` shows "The query took too long. Narrow the filters or switch joins to compact mode and try again." instead of the general failure.

### D7 Indexes (migration `V22__catalog_query_indexes.sql`)

- `entities_active_name ON entities (metatype_id, lower(name), id) WHERE deleted_at IS NULL`: the default order and the keyset seek.
- The joins reach connections through `entity_connections_source` or `entity_connections_target` and check the connection's metatype on `entities` by primary key. These existing indexes are kept unless the measured plans show otherwise.
- Further indexes are added only if the measured plans need them (task 4.3). The design records them when they are added.

### D8 Measurement (`CatalogQueryLatencyIT`)

- **Seed data**, inserted with SQL in batches on an own database:
  - 10,000 applications with a status, a vendor and a description;
  - 3 target metatypes with connection metatypes;
  - on average 5 connections per application and join.
- **Catalog:** columns name, status and vendor, and 3 joins in aggregate mode, plus a filter on status `eq` and vendor `contains`.
- **Measurement:** 5 warm-up queries, then 30 measured queries per scenario through MockMvc, using the timer difference as in `LoginLatencyIT`.
- **Scenarios:**
  - (1) without joins and filters;
  - (2) with the 3 joins;
  - (3) with the 3 joins and the filters;
  - (4) the page after page 150 through the cursor, sorted by name and by status.
- **Pass criterion:** the 95th percentile is below the targets (200, 500, 600 and 200 ms). The measured values are logged and recorded in REQ-071.
- **Result (task 4.3):** all four targets are met without further indexes or rewritten statements; `entities_active_name` and the existing connection indexes suffice. 95th percentiles of the local run of 2026-11-07: (1) 6 ms, (2) 20 ms, (3) 84 ms, (4) 5 ms sorted by name and 18 ms sorted by status; in CI (run of #207): (1) 7 ms, (2) 18 ms, (3) 72 ms, (4) 5 ms and 17 ms. The count stays in the contract.

## Data Model

There are no new tables or columns. Migration `V22__catalog_query_indexes.sql` adds indexes (D7). `docs/data-model.puml` gets the indexes in the notes of `entities` and `entity_connections`.

## API

Delta to `api/openapi.yaml`:

- **`queryCatalog` and `queryPortalCatalog`:**
  - a new optional query parameter `cursor` (string, at most 2,000 characters), used together with `page`;
  - the 400 description names `cursor`;
  - a new response 503 with the problem type `urn:oea:problem:query-timeout`, as a new component response `QueryTimeout`.
- **`CatalogQueryResult`:** a new optional `nextCursor` (string or null), present when a following page exists.
- **Descriptions:** both operation descriptions explain the cursor and the time limit.

## Risks / Trade-offs

- **Keyset with a sort on a property column still sorts all filtered rows.** The cursor saves the `OFFSET` skip but not the sort. → The measurement includes a property sort on a deep page. If it misses the target, task 4.3 adds an index or a materialized sort key and records it.
- **The count runs on every query.** → It is part of the measured time. If it misses the target, task 4.3 optimizes it, for example as an index-only count over `entities_active`.
- **The in-memory cursor key breaks cursors after a restart and across instances.** → The client falls back to `page`. A shared key follows if OEA runs several backend instances.
- **A 60 s global limit could cut long administrative statements**, such as package imports. → It is configurable, and the existing import tests run far below it.
- **CI runners vary in speed.** → The targets apply to server time; the seed data and the warm-up keep the measurement stable, as for `LoginLatencyIT`.

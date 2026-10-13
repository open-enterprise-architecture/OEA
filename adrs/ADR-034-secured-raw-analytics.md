# ADR-034: Secured Raw SQL Analytics on a Masked Reporting Schema; Cypher Deferred

**Status**: accepted
**Date**: 2026-10-13
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer (security review 2026-10, SEC-17, SEC-23), Requirements Engineer (REQ-179, REQ-160, REQ-186)
**Informed**: Business Engineer (data model follow-up), Backend Engineer, Frontend Engineer
**Partially supersedes**: [ADR-013](./ADR-013-api-style.md) (Decision table row "Analytics: `POST /api/v1/analytics/query` (SQL analytics, §22)", which places no restriction on the query text, the database objects reachable or the principals allowed to call it)

## Context and Problem

Concept §22 asks for two query paradigms, graph traversal and analytical aggregation, and recommends offering raw query languages to advanced users next to typed endpoints: `/api/v1/graph/cypher` for Cypher and `/api/v1/analytics/query` for "SQL or SQL-like" analytics (§22.4). [ADR-013](./ADR-013-api-style.md) adopted `POST /api/v1/analytics/query` as "SQL analytics" without any restriction. [ADR-016](./ADR-016-persistence-strategy.md) and [ADR-030](./ADR-030-postgresql-only.md) fixed the persistence on PostgreSQL ≥ 15 with JSONB and **without Apache AGE in v1.0**; ADR-016 already removed `/api/v1/graph/cypher` from the v1.0 API, but concept §22 still describes it.

The security review of the requirements baseline (`security-reviews/requirements-baseline-2026-10.md`) rated **SEC-17 "Raw SQL / Cypher query endpoints planned"** as High: arbitrary SQL against the application database reads credential tables and the audit schema, bypasses property visibility (REQ-124 to REQ-128), and reaches dangerous PostgreSQL functions; expensive queries are a denial-of-service vector (**SEC-23**, no rate limits or quotas). The review proposed to drop raw queries (PR-23).

The owner decided otherwise (**Q8**, 2026-10-13): *raw SQL/Cypher analytics stay, but secured* – read-only database role, statement timeout, row limit, dedicated analytics permission, audit of every query, no access to the audit schema or credentials. The requirements engineer captured this as [REQ-179](../requirements/req/REQ-179-secured-raw-query-analytics.md). [ADR-032](./ADR-032-authorization-model.md) introduced the permission `analytics:query` and the built-in role `analyst`, and left one follow-up open: *"Mechanism for principal-specific reporting views of raw analytics (row-level security vs. per-request views)"*.

The hard part is that property visibility is **per principal, per property and – for connection-scoped properties – per entity** (REQ-127), and that it must hold on every read channel including aggregations, filters and grouping (REQ-160). A static read-only role alone cannot express that. This ADR decides how raw SQL is executed so that REQ-179, REQ-160, REQ-175 and REQ-186 hold, and how "Cypher" is handled on a PostgreSQL-only stack.

## Decision Drivers

- **Owner decision Q8**: raw analytics are a product feature for advanced users (SH-02, SH-03); a typed query model alone does not satisfy it.
- **Property visibility on every channel (REQ-160)**: restricted values must not be readable, filterable, groupable or aggregatable through raw SQL – including oracles via `WHERE`, `GROUP BY`, aggregates over one row, error messages or timing.
- **Separation from secrets and audit (REQ-179, ADR-024, REQ-174)**: no path from an analytics query to credentials, sessions, service account secrets, settings, authorization tables or the audit schema.
- **Availability (REQ-186, REQ-073)**: one analyst must not degrade the catalog latency of other users (REQ-071); limits must be enforced by the server, not by convention.
- **Accountability (REQ-175)**: every query, including rejected ones, is attributable to a principal.
- **PostgreSQL only, no graph extension (ADR-016, ADR-030)**: no Apache AGE in v1.0; no second database to operate.
- **Operator fit (SH-06)**: no additional runtime component; works with the standard PostgreSQL image and Docker Compose (REQ-075).
- **Single source of visibility logic (ADR-032 L4)**: the visibility rules live in `io.oea.core`; duplicating them in SQL creates divergence.
- **Defense in depth**: no single control (parser, database grants, masking) may be the only barrier.

## Considered Options

### Option (a): No raw queries – typed query model only

Analytics are expressed in a typed, server-compiled query model (entity type, filters, joins along connection types, aggregations, grouping), like the catalog filter model. The server generates parameterized SQL and passes all results through the L4 read projection (ADR-032).

- **Pro**:
  - Smallest attack surface; SQL injection and catalog access impossible by construction
  - Property visibility reuses the existing L4 projection without a database-side mechanism
  - Query cost is predictable; limits can be checked before execution
- **Con**:
  - Contradicts owner decision Q8
  - The analyses in concept §22.3 (window functions, CTEs, pivoting, calculated fields, subqueries) require a large DSL that approaches a proprietary query language – the risk §22.4 warns about
  - Every new analysis need becomes a feature request

### Option (b): Raw SQL against a reporting schema of masking views under a read-only role

A dedicated schema `reporting` contains views over the application tables. The views expose only analytics-relevant content and mask restricted property values for the calling principal. Queries run under a dedicated read-only database role that has privileges only on the reporting schema. The principal's visibility context is passed into the database transaction per request.

- **Pro**:
  - Full SQL expressiveness (joins, CTEs including `WITH RECURSIVE`, window functions) for the analyses of §22.3
  - Base tables, credentials, audit schema and settings are unreachable by grants, not only by parsing
  - Masking happens in the view projection, so `WHERE`, `GROUP BY`, aggregates and errors only ever see masked values
  - The reporting schema is a stable, documented contract decoupled from the internal table layout (Flyway migrations of base tables do not break analysts' queries)
  - No additional runtime component; works on a hot-standby replica as well
- **Con**:
  - Per-principal masking needs a tamper-proof way to pass the visibility context into the session
  - The views must be maintained along with the data model and metamodel
  - Shared database: heavy queries still compete with OLTP load unless limited
  - SQL is a large language; the statement allowlist must be maintained

### Option (c): Raw SQL with PostgreSQL row-level security on base tables

Analysts query the application tables directly; row-level security (RLS) policies restrict rows per principal (context via session variables); column privileges hide sensitive columns.

- **Pro**:
  - No duplicated view layer; RLS is a native, well-tested PostgreSQL feature
  - Policies are enforced by the database for every query shape
- **Con**:
  - Property visibility is **value-level inside JSONB** (`entities.properties`): RLS filters rows, it cannot mask individual keys of a JSONB column; column privileges cannot either. Masking would still need views – RLS alone does not solve the problem.
  - RLS on base tables also applies to the application role unless it has `BYPASSRLS` or permissive policies; a policy mistake breaks or silently opens the main application path
  - The internal table layout (base tables, JSONB structure, versioning tables) becomes a public contract; every migration can break analysts' queries
  - Credential, session and audit tables must be excluded by grants anyway – the same work as option (b) without its decoupling
  - Context passing has the same tamper problem as in option (b)

### Option (d): Separate analytics replica or database with masked data (e.g. nightly export)

A job exports repository content into a separate database (PostgreSQL, DuckDB) on a schedule; restricted properties are removed or masked during export; analysts query the copy.

- **Pro**:
  - Strong isolation: no path from analytics to the production database, its credentials or its audit schema
  - No OLTP load impact
  - Export format can be optimized for analytics (wide tables, columnar)
- **Con**:
  - Masking is per export, not per principal: either all restricted properties are removed for everybody (analysts with legitimate `readRoles` lose access) or one export per role combination is needed, which does not scale and cannot express connection-scoped visibility (REQ-127)
  - Stale data (up to a day) contradicts the use of analytics for current decisions (UC-06, UC-20)
  - A second database or file store to operate, back up, secure and include in GDPR processes (SH-06)
  - The export itself becomes a bulk read channel that must satisfy REQ-160 and data-subject rights (REQ-189 / PR-33)

### Sub-decision: how "Cypher" is handled without a graph extension

| Option | Description | Assessment |
|---|---|---|
| C1 – Not offered in v1.0, deferred | No Cypher endpoint; graph questions use the typed traversal endpoints and `WITH RECURSIVE` in raw SQL over `reporting.connections` | Consistent with ADR-016/ADR-030; no new component; contract prepared for a later addition |
| C2 – Translate Cypher to SQL | A server-side openCypher parser compiles a Cypher subset into SQL over the reporting views | No maintained Java openCypher-to-SQL compiler for this schema exists; a self-built compiler is a second query language implementation with its own semantics, tests and documentation; variable-length paths compile to recursive CTEs that are hard to bound; low value for v1.0 compared to effort |
| C3 – Introduce Apache AGE | Add the AGE extension and mirror the graph | Explicitly deferred to v2.0 by ADR-016; custom database image (ADR-030 operational simplicity); a materialized AGE graph cannot apply per-principal masking without the same context mechanism, and AGE's function surface would need its own allowlist |

## Decision

We choose **Option (b): raw SQL against a reporting schema of principal-masking views, executed under a dedicated read-only database role**, combined with a strict statement parser, hard resource limits and an audit event for every query. For Cypher we choose **C1: Cypher is not offered in v1.0**; it is deferred, not dropped.

Option (b) is the only option that satisfies Q8 with full SQL expressiveness while keeping per-principal property visibility (REQ-160) and excluding secrets and audit data by database grants rather than by parsing alone. RLS (c) cannot mask values inside JSONB and would make the internal table layout a public contract. An export database (d) cannot mask per principal or per connection scope and adds an operational component. The mechanism for principal-specific masking requested by the ADR-032 follow-up is a **signed per-request visibility context evaluated in security-barrier views** (section 3), not RLS on base tables.

### 1. Execution pipeline

Every analytics request passes these steps in order; a failure at any step stops processing:

| Step | Where | Check | Failure |
|---|---|---|---|
| 1 | Security filter chain (ADR-032 L1/L2) | Authenticated principal holding `analytics:query`; feature enabled (`analytics.enabled`) | 401 / 403 (`authz.denied` audited) |
| 2 | Rate and concurrency limiter | Per-principal rate and concurrency limits (section 5) | 429 with `Retry-After` |
| 3 | Statement validator | Parse tree satisfies the rules of section 4 | 422 with a reason code; audited as rejected |
| 4 | Context builder (application layer) | Computes the principal's visibility context with the L4 visibility component of `io.oea.core` (same code as every other read channel) and signs it | 500 (fail closed) |
| 5 | Analytics datasource | Separate connection pool with the role `oea_analytics`; transaction `READ ONLY`; transaction-local settings for timeout, context and signature | – |
| 6 | PostgreSQL | Grants (section 2), masking views (section 3), statement timeout and memory limits | 422 (timeout) or 422 (generic execution error) |
| 7 | Result reader | Streams rows with a cursor; stops at the row limit or the result size limit and sets the truncation indicator | – |
| 8 | Audit | Writes `analytics.query.executed` or `analytics.query.rejected` (section 6) | Query result is not returned if the audit write fails (fail closed) |

### 2. Database roles and grants

| Role | Login | Purpose | Privileges |
|---|---|---|---|
| `oea_reporting_owner` | no | Owns schema `reporting`, its views and the context functions in `reporting_ctx` | `SELECT` on exactly the base tables and columns the views read; no privileges on `audit`, credential, session, service-account, authorization, settings or webhook tables; not superuser, no `BYPASSRLS`, no `CREATEROLE`, no membership in predefined roles |
| `oea_analytics` | yes (own credentials, separate from `oea_app`) | Executes analysts' queries | `CONNECT` on the OEA database; `USAGE` on `reporting`; `SELECT` on the views of `reporting`; `EXECUTE` on `reporting_ctx.current_context()` and the allowlisted helper functions in `reporting`; nothing else |

Further rules for `oea_analytics`:

- No `USAGE` on the application schema, the audit schema (ADR-024; possibly a separate database anyway) or `reporting_ctx` tables; `REVOKE TEMPORARY ON DATABASE`; no `CREATE` on any schema.
- Not a member of `pg_read_all_data`, `pg_read_all_settings`, `pg_read_all_stats`, `pg_monitor`, `pg_read_server_files`, `pg_write_server_files`, `pg_execute_server_program`, `pg_signal_backend`; `NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT`.
- Role-level defaults (`ALTER ROLE … SET`): `default_transaction_read_only = on`, `search_path = reporting`, `statement_timeout` = configured maximum, `lock_timeout = 1s`, `idle_in_transaction_session_timeout = 60s`, `work_mem` capped (default 32 MB), `temp_file_limit` (default 1 GB). Per-request values may only be lower than the role defaults.
- `CONNECTION LIMIT` equal to the analytics pool size: a hard global concurrency cap independent of application code.
- The role, its grants and the reporting schema are created by Flyway under the migration owner role (REQ-174); the password of `oea_analytics` is a deployment secret (security review PR-32), never a default in the Compose file (PR-27).

The grants are the **primary** barrier against credentials and audit data; the statement validator (section 4) is the second.

### 3. Reporting schema and principal-specific masking

#### 3.1 Contents of `reporting` (v1)

| View | Content | Masking / filtering |
|---|---|---|
| `reporting.entity_types`, `reporting.connection_types` | Metatypes and connection types (id, key, label, layer, `is_connection`) | None (metamodel is readable with `entity:read`) |
| `reporting.property_definitions` | Property definitions (id, key, metatype, data type, `visibility_mode`) plus a column `readable` for the caller | `readRoles`/`writeRoles`, scoping connection type and override list are **not** exposed (security configuration) |
| `reporting.entities` | Non-deleted entities that are not connections: id, URN, metatype, name, description, `is_logical`, `created_at`, `updated_at` | Soft-deleted rows excluded; no actor columns (`created_by`, `updated_by` are personal data, REQ-189) |
| `reporting.connections` | Non-deleted connections: id, URN, connection type, `source_entity_id`, `target_entity_id`, name | Soft-deleted rows excluded; reified connections (ADR-004) also appear in `reporting.property_values` |
| `reporting.property_values` | Long format: `entity_id`, `property_definition_id`, `property_key`, typed value columns (`value_text`, `value_number`, `value_date`, `value_boolean`, `value_ref_entity_id`), `redacted` | Value columns `NULL` and `redacted = true` when the caller may not read the property for that entity |
| `reporting.plateaus`, `reporting.entity_plateau_states` | Plateaus and lifecycle state per entity and plateau | None (structural data) |
| `reporting.<metatype>` (generated) | One wide view per metatype with one column per property definition, for convenient SQL like the examples in §22.7 | Same masking expression per column; regenerated by the backend under `oea_reporting_owner` when the metamodel changes |
| `reporting.columns` | Introspection of the reporting schema (view, column, type, `readable` for the caller) | Replaces access to `information_schema` and `pg_catalog` |

Explicitly **not** in the reporting schema in v1: entity history (`entity_versions`, property value history), audit data, persons as accounts, credentials, sessions, roles and role assignments, service accounts, settings, authentication providers, webhooks, catalogs/dashboards configuration, i18n administration, soft-deleted content. Person **entities** (EA content, ADR-032 §2) are content and appear like any other entity.

The reporting schema is a documented, versioned interface: changes to view names or columns are listed in the release notes; removing or renaming a column of a non-generated view is a breaking change under the versioning rules of ADR-013.

#### 3.2 Principal-specific masking mechanism

1. **Context**: for each request the backend computes, with the L4 visibility component (`io.oea.core`), a visibility context for the principal:
   - principal ID and request ID,
   - the set of property definition IDs readable for **all** entities (public properties and role-restricted properties whose `readRoles` intersect the principal's roles),
   - for connection-scoped properties (REQ-127): per property definition the set of entity IDs for which the principal may read it, computed with the scope traversal and its cache (REQ-131; the context build reuses the warm cache and stays within the REQ-131 latency budget),
   - the PostgreSQL backend process ID of the pooled connection, and an expiry (now + statement timeout + 5 s).
2. **Signature**: the backend serializes the context as JSON and computes an HMAC-SHA-256 over it with the instance's reporting context key. It sets both as transaction-local settings in the same `READ ONLY` transaction, immediately before the analyst's statement: `set_config('oea.ctx', <json>, true)` and `set_config('oea.ctx_sig', <hmac>, true)`.
3. **Verification**: `reporting_ctx.current_context()` is a `SECURITY DEFINER`, `STABLE` function owned by `oea_reporting_owner`. It reads both settings, verifies the HMAC with the key stored in `reporting_ctx.keys` (readable only by the owner), checks backend PID and expiry, and returns the parsed context. Missing, expired, foreign or invalid contexts raise an error: the query fails (fail closed).
4. **Masking views**: every view is created `WITH (security_barrier = true)` and resolves the context once per statement in a single-row subquery (not per row). Values are masked in the **projection**, e.g. `CASE WHEN pd.id = ANY(ctx.readable_all) OR v.entity_id = ANY(ctx.scoped[pd.id]) THEN v.value_text END`, with `redacted` set accordingly.

Why this holds:

- User predicates, `GROUP BY`, aggregates, `ORDER BY`, joins and expressions that may raise errors operate on view **output columns**, i.e. on already masked values. Filter, aggregation, error and timing oracles therefore only ever observe `NULL` (REQ-160 AC2). The security barrier additionally prevents user-supplied expressions from being evaluated on rows before the views' own filters (deleted rows).
- The analyst cannot change the context: `set_config` and `current_setting` are rejected by the statement validator; and even a changed setting fails the HMAC check because `oea_analytics` cannot read the key.
- The visibility rules are evaluated once, in Java, by the same component as all other read channels; the database only applies a precomputed allowlist.
- The mechanism uses only transaction-local settings and functions, so it also works on a PostgreSQL hot-standby replica (section 7).

**Property-visibility override (REQ-128)** does **not** apply to raw analytics: REQ-128 requires an audit record per override read, which cannot be produced per value inside SQL. Principals read restricted properties in analytics only through `readRoles` or connection scope.

`pgcrypto` (shipped with the standard PostgreSQL distribution and image) provides `hmac()`; if the extension is unwanted, an HMAC built on the built-in `sha256()` is equivalent.

### 4. Statement validation

The query text is parsed by the backend with a parser that implements the PostgreSQL grammar (preferred: libpg_query through a Java binding; the library choice is a follow-up). Validation works on the parse tree, never on string patterns. A query is accepted only if all rules hold:

| # | Rule |
|---|---|
| V1 | Exactly one statement; maximum length 20,000 characters; maximum 50 bound parameters (`$1` … `$n`) supplied separately in the request |
| V2 | The statement is a `SELECT` (including `WITH`, `WITH RECURSIVE`, `UNION`/`INTERSECT`/`EXCEPT`, subqueries, `VALUES`); no `SELECT INTO`, no locking clause (`FOR UPDATE/SHARE`), no data-modifying CTE |
| V3 | Every relation reference resolves to a view in schema `reporting` (unqualified or qualified with `reporting`); references to any other schema – in particular `pg_catalog`, `information_schema`, `pg_toast`, `public`, `audit`, `reporting_ctx` – and to names starting with `pg_` are rejected |
| V4 | Every function call (including operators implemented by functions, aggregates, window functions) is on an allowlist maintained in the backend: aggregates (`count`, `sum`, `avg`, `min`, `max`, `percentile_cont`, `percentile_disc`, `string_agg`, `array_agg`, `bool_and/or`), window functions, string, numeric, date/time, conditional (`coalesce`, `nullif`, `greatest`, `least`), JSON accessors and the helper functions of `reporting`. Everything else is rejected; examples that must never pass: `set_config`, `current_setting`, `pg_sleep*`, `pg_read_file`, `pg_read_binary_file`, `pg_ls_dir`, `pg_stat_file`, `lo_*`, `dblink*`, `query_to_xml` and other `*_to_xml` functions that execute query text, `pg_terminate_backend`, `pg_cancel_backend`, `pg_advisory_*`, `nextval`, `setval`, `txid_*`, `pg_notify` |
| V5 | No casts to object identifier types (`regclass`, `regproc`, `regtype`, `regrole`, `regnamespace`, `oid`) and no `COLLATE` clauses referring to non-default collations |
| V6 | No `TABLESAMPLE` with a user-supplied method, no `ROWS FROM` with functions outside the allowlist, no `LATERAL` calls to functions outside the allowlist |
| V7 | Utility statements of any kind (`SET`, `RESET`, `SHOW`, `COPY`, `DO`, `CALL`, `EXPLAIN`, `LISTEN`, `PREPARE`, `LOCK`, `VACUUM`, DDL, DML, transaction control) are rejected |

Error responses on rejection name the rule (`V3`, `V4`, …) and the position in the query text, never internal object names. PostgreSQL errors during execution are mapped to generic problem responses (syntax position or "execution error" with the request ID); raw PostgreSQL messages are logged server-side only (security review PR-37).

### 5. Resource limits

All values are instance settings (`settings:manage`), with defaults below; per-request values in the body can only lower them. They complement the general limits of [REQ-186](../requirements/req/REQ-186-rate-limits-resource-quotas.md).

| Limit | Default | Enforcement |
|---|---|---|
| Statement timeout | 30 s (configurable up to 120 s) | `SET LOCAL statement_timeout`; role default as hard upper bound |
| Row limit | 10,000 rows (configurable up to 100,000) | JDBC cursor with `maxRows = limit + 1`; result marked `truncated: true`, `truncatedReason: rows` |
| Result size limit | 10 MB of serialized JSON | Reader stops and marks `truncatedReason: size` |
| Columns per result | 200 | Checked on result metadata; 422 above |
| Concurrent queries per principal | 2 | Application limiter; 429 above |
| Queries per principal | 30 per minute | Application rate limiter (same store as REQ-186); 429 with `Retry-After` |
| Analytics pool size / global concurrency | 4 connections | Separate connection pool; `CONNECTION LIMIT` of `oea_analytics`; waiting longer than 5 s → 429 |
| Memory per query | `work_mem` 32 MB, `temp_file_limit` 1 GB | Role defaults |

The analytics pool is separate from the application pool, so that saturating it cannot exhaust connections of the OLTP path (REQ-071, REQ-073). With several backend nodes, per-principal limits use the shared rate-limit store of REQ-186; the global cap is enforced by the database role.

### 6. Audit

Every request that passed authentication creates exactly one audit event in the audit schema (ADR-024, REQ-175):

| Field | Content |
|---|---|
| `event_type` | `analytics.query.executed` or `analytics.query.rejected` |
| principal | Principal ID (person or service account; pseudonymous display per REQ-189), session ID, client, source address, correlation/request ID |
| `query_hash` | SHA-256 of the query text as submitted |
| `query_fingerprint` | Normalized statement from the parse tree with all literals replaced by placeholders (like `pg_stat_statements`); bound parameter values are **not** stored |
| `referenced_views` | Reporting views referenced by the statement |
| outcome | `ok`, `truncated-rows`, `truncated-size`, `timeout`, `rejected-<rule>`, `rate-limited`, `execution-error` |
| metrics | Duration, row count returned, result bytes |

Result content is never written to the audit log (§22.12). Missing permission produces `authz.denied` (REQ-175 AC1) instead. Repeated rejections by the validator (rules V3, V4, V7) are a probing signal and are counted as security events for alerting.

### 7. API contract

`api/openapi.yaml` is changed in a separate spec-first step; this ADR does not change it.

| Operation | Authorization (`x-oea-authorization`, ADR-032) | Description |
|---|---|---|
| `POST /api/v1/analytics/query` | `analytics:query` | Request: `language` (enum, v1: `sql` only), `query` (string, `maxLength` 20,000), `parameters` (array, `maxItems` 50, scalar values), optional `maxRows` and `timeoutSeconds` (only lower than the instance limits). Response 200: `columns` (`name`, `type`), `rows` (array of arrays), `rowCount`, `truncated`, `truncatedReason`, `durationMs`, `requestId`. Errors: 400 (malformed body, parse error with position), 401, 403, 413 (query text too long), 422 (validation rule violated, timeout, execution error, too many columns; problem type with reason code), 429 (`Retry-After`), 503 (analytics disabled) |
| `GET /api/v1/analytics/schema` | `analytics:query` | Reporting views and columns with types and a `readable` flag for the caller (same content as `reporting.columns`) |

Further contract decisions:

- Synchronous execution only in v1; no asynchronous jobs, no saved queries, no CSV or file export (follow-ups).
- `GET /api/v1/analytics/views/{name}` (stored named views, concept §22.8) is not part of the v1 contract. If added later, named views execute through the same pipeline (sections 1–6) under the caller's context.
- `language: cypher` is not accepted in v1 (schema validation → 400). The `language` field exists so Cypher can be added later without a breaking change.
- Graph traversal stays on the typed endpoint `POST /api/v1/entities/{id}/impact` (ADR-013, ADR-016) with the depth limit of REQ-186.
- The feature is controlled by the instance setting `analytics.enabled`, **default off**; the operator enables it after provisioning the `oea_analytics` credentials. The built-in role `analyst` remains the only built-in holder of `analytics:query` and is not assignable to service accounts (ADR-032 matrix).

### 8. Cypher in v1.0

**Cypher is not offered in v1.0.** There is no `/api/v1/graph/cypher` endpoint and no Cypher translation. Graph questions are answered by:

1. the typed traversal endpoint (impact/lineage, depth ≤ 5 per REQ-186), and
2. `WITH RECURSIVE` queries in raw SQL over `reporting.connections`, bounded by the statement timeout and row limit.

Owner decision Q8 ("raw SQL/Cypher analytics stay") is implemented as: SQL now, Cypher **deferred** to the ADR that decides on Apache AGE or another graph engine (ADR-016: v2.0 at the earliest). That ADR must provide the same guarantees as this one: per-principal masking (the graph may only be built from the masked reporting projection or must evaluate the signed context), a function allowlist for the graph language, the same limits and the same audit event.

### 9. Read replica (optional)

Operators may point the analytics datasource at a PostgreSQL hot-standby replica of the OEA database to isolate analytics load completely. The roles, views, context functions and limits are identical (they replicate with the schema); the reporting context key is replicated with `reporting_ctx.keys`. Replica lag is visible to analysts as slightly stale data. This is a deployment option, not a requirement; the default is the primary database with the separate pool.

## Consequences

### Positive Consequences

- SEC-17 is resolved without dropping the feature (Q8): credentials, sessions, authorization data and the audit schema are unreachable by database grants; property visibility holds for raw SQL including filters, aggregates and grouping.
- The ADR-032 follow-up "mechanism for principal-specific reporting views" is decided: signed per-request context plus security-barrier views; visibility logic stays in one place (`io.oea.core`).
- Analysts get full SQL (joins, CTEs, recursive queries, window functions) on a stable, documented reporting schema that is decoupled from internal migrations.
- SEC-23 is addressed for the most expensive channel: separate pool, global and per-principal limits, memory caps; other users' catalog latency is protected.
- Every query is attributable (REQ-175) without storing query literals or result content.
- No new runtime component; standard PostgreSQL image; optional replica for load isolation.

### Negative Consequences / Trade-offs

- The reporting views, including the generated per-metatype views, must be maintained with every data model and metamodel change; a missing masking expression in a new view would be a leak. Mitigation: views are generated from one masking template and covered by a parametrized test that checks every column of every view against REQ-160.
- The statement validator and the function allowlist are security-critical code that must be maintained with PostgreSQL upgrades (new functions are rejected until allowlisted – fail closed, but may annoy analysts).
- Analysts cannot see entity history, audit data or soft-deleted content through SQL in v1, and cannot use the property-visibility override.
- Cypher users (§22.4, §22.7 examples) get no Cypher in v1.0; graph analyses require recursive SQL or the typed traversal endpoint.
- Without a replica, heavy analytics still share the primary database's I/O and CPU, bounded only by the limits.
- The context build adds latency per query (dominated by the connection-scope traversal; bounded by REQ-131).
- The signing key is one more secret to manage and rotate.
- The feature is off by default; operators must provision a second database credential to use it.

### Follow-up Decisions

- Choice of the SQL parser library (libpg_query binding vs. JSqlParser with PostgreSQL dialect) and the initial function allowlist (backend engineer + security engineer, as part of the OpenSpec change).
- Management and rotation of the reporting context key as part of the secrets and key management requirement (security review PR-32).
- Generation and naming rules for per-metatype wide views (`reporting.<metatype>`), including handling of metatype keys that are not valid SQL identifiers.
- Reporting views for entity history and plateau-to-plateau diffs with masking per version (v1.x, if a use case requires it).
- Stored named views (§22.8), saved queries, asynchronous execution and CSV export (v1.x).
- Cypher / Apache AGE (v2.0, together with the AGE decision of ADR-016).
- Whether a cost pre-check via `EXPLAIN` (without `ANALYZE`) is added to reject obviously expensive statements before execution (§22.13 `query-complexity-limit`); not part of v1.

## Required Changes to Other Artifacts

### Data model changes (for the business engineer)

The business engineer updates `docs/data-model.puml` and the business objects; this ADR does not change them.

| # | Change | Affected |
|---|---|---|
| DM-1 | New schema `reporting` with the views of section 3.1 (shown as a separate package of views, with the masking rule as a note) | `docs/data-model.puml` |
| DM-2 | New schema `reporting_ctx` with table `keys` (key ID, key material, `valid_from`, `valid_to`; readable only by `oea_reporting_owner`) and the function `current_context()` | `docs/data-model.puml` |
| DM-3 | Database roles `oea_reporting_owner` and `oea_analytics` added to the role overview next to the migration owner, runtime and audit roles (REQ-174, ADR-024) | `docs/data-model.puml` (roles note) |
| DM-4 | Audit event types `analytics.query.executed` and `analytics.query.rejected` with the fields of section 6 in the audit event catalog | `docs/data-model.puml` (audit schema), ADR-024 event catalog |
| DM-5 | Instance settings `analytics.enabled` (default `false`), `analytics.statementTimeoutSeconds`, `analytics.maxRows`, `analytics.maxResultBytes`, `analytics.maxConcurrentPerPrincipal`, `analytics.queriesPerMinute`, `analytics.poolSize` | `docs/data-model.puml` (`system_settings`), settings business object |
| DM-6 | `metamodel-configuration`: property definitions get a stable SQL column name used by generated reporting views | `business-objects/metamodel-configuration.md` |

### API contract changes (for a later spec-first step)

| # | Change |
|---|---|
| API-1 | `POST /api/v1/analytics/query` with request/response schemas, limits and error responses of section 7; `x-oea-authorization` `analytics:query` |
| API-2 | `GET /api/v1/analytics/schema` |
| API-3 | Settings schema extended with the `analytics.*` settings (DM-5) |
| API-4 | No `/api/v1/graph/cypher` and no `/api/v1/analytics/views/{name}` in v1 |

### Requirements (for the requirements engineer)

- [REQ-179](../requirements/req/REQ-179-secured-raw-query-analytics.md): Statement "SQL or Cypher" → SQL in v1, Cypher deferred (this ADR); add result size limit, concurrency limit, separate pool and default-off setting; timeout response fixed to 422 (instead of "408/422"); audit fields per section 6 (hash and literal-free fingerprint); override (REQ-128) does not apply; reference this ADR in `adrs`.
- [REQ-186](../requirements/req/REQ-186-rate-limits-resource-quotas.md): add the analytics rows (30 queries/min, 2 concurrent per principal, pool 4) to the target value table.
- [REQ-160](../requirements/req/REQ-160-visibility-on-all-read-channels.md): list "raw analytics (reporting schema)" as a read channel.
- [REQ-175](../requirements/req/REQ-175-security-event-logging.md): name the two analytics event types.
- [REQ-128](../requirements/req/REQ-128-property-visibility-admin-override.md): state that the override does not apply to raw analytics.

### Concept changes (concept §22 and §21; with CHANGELOG entry and version bump)

| Section | Change |
|---|---|
| §22.4 | Replace the endpoint list: `/api/v1/graph/cypher` is not offered in v1.0 (deferred with AGE); `/api/v1/analytics/query` is SQL only, against the reporting schema, permission `analytics:query`; `/api/v1/analytics/views/{name}` is a later extension; reference ADR-034 |
| §22.5 | Implementation notes: remove "Postgres + AGE" as walking-skeleton choice (ADR-016/ADR-030); Scenario 3 SQL example refers to `reporting.connections` / `reporting.property_values` instead of a fictional `relations` table; Cypher scenarios marked as future |
| §22.6 | Mark option 1 "Postgres + JSONB + Apache AGE" as superseded by ADR-016/ADR-030 (PostgreSQL + JSONB without AGE in v1.0) |
| §22.7 | Mark examples as exemplary; SQL examples rewritten against reporting views; Cypher examples marked "future (graph engine pending)" |
| §22.8 | Named views: deferred; when introduced they run under the caller's visibility context |
| §22.9 | Add the analytics limits (timeout 30 s, 10,000 rows, 10 MB) next to the p95 guideline of 2 s |
| §22.10 | CLI/offline analytics: snapshots contain only data the exporting principal may read (REQ-160); "identical query languages as the server" → SQL; Cypher example marked future |
| §22.12 | Query audit log: principal instead of `Role \| Person \| ApiKey` (API keys dropped, Q5); fields aligned with section 6 (hash + literal-free fingerprint, outcome, metrics); stored in the audit schema (ADR-024) and exportable to SIEM (REQ-175) |
| §22.13 | `query-complexity-limit`: replace "estimated cost" by the enforced limits of section 5 |
| §22.14 | Walking-skeleton recommendation: PostgreSQL + JSONB without AGE; typed traversal + secured SQL analytics (not part of the walking skeleton); no Cypher stretch goal; OpenAPI spec-first (ADR-013) instead of "generated from code" |
| §21.4 | Annotate `POST /api/v1/analytics/query` with "SQL on reporting schema, `analytics:query`, ADR-034"; `GET /api/v1/analytics/views/{name}` marked as later extension; `/api/v1/graph/traverse`, `/shortest-path`, `/neighborhood` aligned with the typed traversal endpoint of ADR-013/ADR-016 |

## Relation to Existing ADRs

| ADR | Relation |
|---|---|
| [ADR-013](./ADR-013-api-style.md) | Partially superseded: the Decision table row "Analytics: `POST /api/v1/analytics/query` (SQL analytics, §22)" is replaced by section 7 of this ADR (SQL only, reporting schema, `analytics:query`, limits, audit). REST + OpenAPI spec-first, versioning and all other rows remain valid. ADR-013 itself is not edited. |
| [ADR-016](./ADR-016-persistence-strategy.md) | Applied: no Apache AGE in v1.0, therefore no Cypher; graph traversal via recursive CTEs (typed endpoint and raw SQL). |
| [ADR-024](./ADR-024-audit-separation.md) | Used: analytics events are audit events; the audit schema is unreachable for `oea_analytics`. |
| [ADR-030](./ADR-030-postgresql-only.md) | Applied: PostgreSQL-specific features (security-barrier views, transaction-local settings, role defaults, `CONNECTION LIMIT`) are used deliberately. |
| [ADR-032](./ADR-032-authorization-model.md) | Implements the permission `analytics:query` and the role `analyst`; decides the follow-up "mechanism for principal-specific reporting views": signed per-request context and security-barrier views instead of RLS. Database-level barriers remain additional to L1–L4. |

## References

**Concept Chapters**:

- [§22 Analyzability and Query Architecture](../concept/70-platform/22-analyzability.md)
- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md) (§21.4)

**Related ADRs**:

- [ADR-004](./ADR-004-reification-details.md), [ADR-013](./ADR-013-api-style.md), [ADR-016](./ADR-016-persistence-strategy.md), [ADR-024](./ADR-024-audit-separation.md), [ADR-030](./ADR-030-postgresql-only.md), [ADR-032](./ADR-032-authorization-model.md)

**Use Cases / Requirements**:

- [REQ-179: Secured raw SQL and Cypher analytics](../requirements/req/REQ-179-secured-raw-query-analytics.md)
- [REQ-160: Property visibility enforced on every read channel](../requirements/req/REQ-160-visibility-on-all-read-channels.md)
- [REQ-186: Rate limiting and resource quotas](../requirements/req/REQ-186-rate-limits-resource-quotas.md)
- [REQ-131: Graph traversal latency](../requirements/req/REQ-131-graph-traversal-latency.md)
- [REQ-175: Security event logging](../requirements/req/REQ-175-security-event-logging.md)
- [REQ-124: PropertyDefinition visibilityMode](../requirements/req/REQ-124-propertydefinition-visibilitymode.md), [REQ-128: Property visibility admin override](../requirements/req/REQ-128-property-visibility-admin-override.md)
- REQ-127 (connection-scoped visibility), REQ-174 (audit owner role separation), REQ-189 (pseudonymous actor data)

**Security Review**:

- [Security review requirements baseline 2026-10](../security-reviews/requirements-baseline-2026-10.md) – SEC-17, SEC-23; owner decision Q8

## Notes

- The review's original proposal PR-23 ("no raw query languages") corresponds to option (a); it was overruled by owner decision Q8.
- Masking in the view projection, not in a `WHERE` clause, is essential: row filtering alone cannot express property-level visibility, and predicates on masked output columns cannot act as an oracle for the unmasked values.
- Confirmation by the owner that "Cypher deferred to the graph-engine ADR" is an acceptable reading of Q8 is recorded as an open point in the hand-over of this ADR.

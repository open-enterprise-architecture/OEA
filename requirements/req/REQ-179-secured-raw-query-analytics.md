---
id: REQ-179
title: Secured raw SQL analytics
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-06
    - UC-20
  business_objects:
    - entity
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/70-platform/22-analyzability.md
  adrs:
    - ADR-013
    - ADR-034
supersedes: []
superseded_by: []
---

# REQ-179: Secured Raw SQL Analytics

## Statement

The system **MUST** accept raw SQL analytics queries only when the instance setting `analytics.enabled` is on (default: off) and only from principals holding the dedicated permission `analytics:query`, and **MUST** execute them with a read-only database role in a separate connection pool against a reporting schema of views that mask restricted properties according to REQ-160 and that never expose the audit schema or credential tables, with a statement timeout, a row limit, a result size limit and per-principal concurrency limits, and **MUST** audit every query. Cypher is not offered in v1.0.

## Rationale

Advanced users need ad-hoc analytics beyond the typed catalog and dashboard model (concept §22). Arbitrary SQL against the application tables would bypass property visibility, read password and secret tables, and allow denial of service.

## Context

Source: security review SEC-17 (proposal PR-23, rewritten); owner decision Q8: raw SQL/Cypher analytics stay, but secured. ADR-013 names `POST /api/v1/analytics/query`; concept §22 names a Cypher endpoint.

ADR-034 (partially superseding the ADR-013 analytics row) decides the mechanism: reporting schema `reporting` of security-barrier views that mask values in the projection using a signed per-request visibility context computed by the same visibility component as every other read channel; database role `oea_analytics` with privileges only on these views; statement validation on the parse tree (single `SELECT`, reporting views only, function allowlist). Owner confirmation (2026-10-13): Cypher is deferred until a graph-engine decision (refines Q8); graph questions use the typed traversal endpoint or `WITH RECURSIVE` over `reporting.connections`. Raw analytics are disabled by default. The property-visibility override (REQ-128) does not apply to raw analytics.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Query language (v1: `sql` only), query text (max. 20,000 characters), optional bound parameters (max. 50), optional `maxRows` and `timeoutSeconds` that can only lower the instance limits

**Processing**:

- Reject if `analytics.enabled` is off (503) or the principal lacks `analytics:query` (403)
- Enforce per-principal rate and concurrency limits (REQ-186)
- Validate the statement: exactly one `SELECT`, only views of the reporting schema, only allowlisted functions; reject otherwise with 422 and the violated rule
- Execute in a read-only transaction under a dedicated database role that has `SELECT` only on the reporting schema, in a connection pool separate from the application pool; no access to application, audit or credential tables, no file, network or extension functions
- Reporting views mask restricted properties of REQ-160 for the requesting principal (value `NULL`, redaction marker); the property-visibility override (REQ-128) is not applied
- Enforce the limits of the target value table below
- Write an audit event (REQ-175) with principal, hash of the query text, literal-free query fingerprint, referenced reporting views, outcome, duration, row count and result size; bound parameter values and result content are not stored; the result is not returned if the audit write fails

**Limits** (defaults, configurable by `settings:manage`; per-request values can only lower them):

| Limit | Default | Maximum |
|---|---|---|
| Statement timeout | 30 s | 120 s |
| Row limit | 10,000 rows | 100,000 rows |
| Result size | 10 MB serialized JSON | – |
| Columns per result | 200 | – |
| Concurrent queries per principal | 2 | – |
| Queries per principal | 30 / min | – |
| Analytics connection pool (global concurrency) | 4 connections | – |

**Outputs**:

- Result columns and rows, `truncated` indicator with reason (`rows` or `size`), duration and request ID

**Error cases**:

- Feature disabled → 503
- Missing permission → 403
- `language` other than `sql` (e.g. `cypher`) → 400
- Query text too long → 413
- Validation rule violated (non-reporting object, non-allowlisted function, write or utility statement), timeout, execution error, too many columns → 422 with a generic problem response and reason code; no internal object names
- Rate or concurrency limit exceeded → 429 with `Retry-After`
- Row or size limit reached → truncated result with indicator

## Acceptance Criteria

**AC1**:

- Given: a principal without `analytics:query`
- When: it submits any analytics query
- Then: the response is 403 and no query is executed

**AC2**:

- Given: a principal with `analytics:query`
- When: it submits `SELECT * FROM password_credentials`, a query on the audit schema, or a call of `pg_read_file`
- Then: the query is rejected with 422 and no data is returned; executed directly under the analytics database role, the same statements fail with a permission error

**AC3**:

- Given: a principal with `analytics:query` but without read permission on `securityClassification`
- When: it queries the reporting view of application components, including a filter, `GROUP BY` or aggregate on that property
- Then: the column contains only masked values (`NULL`, redaction marker set), and the result does not differ from the result for an entity without that value

**AC4**:

- Given: a query that runs longer than the statement timeout
- When: executed
- Then: it is cancelled at the timeout and the response is 422 with a generic problem response

**AC5**:

- Given: any executed or validator-rejected analytics query
- When: the audit log is inspected
- Then: an event `analytics.query.executed` or `analytics.query.rejected` with principal, query hash, query fingerprint without literals, outcome, duration and row count exists, and no bound parameter value or result content is stored

**AC6**:

- Given: a write statement such as `UPDATE` or `CREATE TABLE`
- When: submitted
- Then: it is rejected and nothing changes

**AC7** (Disabled by default):

- Given: a fresh instance with the default configuration and a principal with `analytics:query`
- When: it submits a valid query
- Then: the response is 503 and no query is executed

**AC8** (Row and size limit):

- Given: a query whose result exceeds 10,000 rows, and another whose serialized result exceeds 10 MB
- When: executed with the default limits
- Then: each result is truncated with `truncated: true` and `truncatedReason` `rows` or `size` respectively

**AC9** (Concurrency limit):

- Given: a principal with 2 analytics queries still running
- When: it submits a third query
- Then: the response is 429 with `Retry-After`, and catalog queries of other principals stay within REQ-071

**AC10** (No Cypher, no override):

- Given: a principal with `analytics:query` that also holds a role in the property-visibility override list (REQ-128)
- When: it submits a query with `language: cypher`, and a SQL query on a restricted property it cannot read through `readRoles` or connection scope
- Then: the Cypher request is rejected with 400; the SQL result contains only masked values for that property

## Verification Method

- [ ] Method: test (automated, Testcontainers with the production role setup) + inspection of grants
- [ ] Pass criterion: AC1–AC10 green; a parametrized test checks every column of every reporting view against REQ-160
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-157, REQ-160, REQ-151, ADR-034
- **Follow-on effects**: REQ-175, REQ-186
- **Conflicts**: REQ-128 (override does not apply to raw analytics; aligned 2026-10-13)

## Risks of Non-Fulfillment

- Risk 1: disclosure of credentials and restricted properties, database overload – high

## Implementation Notes

- Per-principal masking: signed per-request visibility context in transaction-local settings, verified by a `SECURITY DEFINER` function, evaluated in security-barrier views (ADR-034 §3.2)
- Cypher deferred to the ADR deciding on a graph engine (ADR-034 §8); the request field `language` keeps the contract open

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-17 (PR-23), rewritten per owner decision Q8 |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-034: SQL only, Cypher deferred (title, statement, AC10); result size, concurrency limits and separate pool (limits table, AC8, AC9); timeout → 422 (AC4); audit fields with hash and literal-free fingerprint (AC5); disabled by default (AC7); override of REQ-128 does not apply (AC10); validator rejection → 422 (AC2) |

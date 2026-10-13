---
id: REQ-179
title: Secured raw SQL and Cypher analytics
type: functional
priority: must
status: proposed
version: 0.1.0
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
supersedes: []
superseded_by: []
---

# REQ-179: Secured Raw SQL and Cypher Analytics

## Statement

The system **MUST** accept raw SQL or Cypher analytics queries only from principals holding the dedicated permission `analytics:query`, and **MUST** execute them with a read-only database role against a reporting schema of views that exclude or mask restricted properties according to REQ-160 and that never expose the audit schema or credential tables, with a statement timeout and a row limit, and **MUST** audit every query.

## Rationale

Advanced users need ad-hoc analytics beyond the typed catalog and dashboard model (concept §22). Arbitrary SQL against the application tables would bypass property visibility, read password and secret tables, and allow denial of service.

## Context

Source: security review SEC-17 (proposal PR-23, rewritten); owner decision Q8: raw SQL/Cypher analytics stay, but secured. ADR-013 names `POST /api/v1/analytics/query`; concept §22 names a Cypher endpoint.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Query text (SQL or Cypher), optional parameters

**Processing**:

- Check `analytics:query`; reject otherwise with 403
- Execute in a read-only transaction under a dedicated database role that has `SELECT` only on the reporting schema; no access to application, audit or credential tables, no file, network or extension functions
- Reporting views apply the visibility rules of REQ-160 for the requesting principal (exclude or mask restricted properties)
- Enforce a statement timeout (default 30 s) and a row limit (default 10,000 rows)
- Write an audit event with principal, query text hash, duration and row count

**Outputs**:

- Result rows (truncated at the row limit with an indicator)

**Error cases**:

- Timeout → 408/422 with a generic message; row limit → truncated result with indicator; access to non-reporting objects → database permission error mapped to 403

## Acceptance Criteria

**AC1**:

- Given: a principal without `analytics:query`
- When: it submits any analytics query
- Then: the response is 403 and no query is executed

**AC2**:

- Given: a principal with `analytics:query`
- When: it submits `SELECT * FROM password_credentials`, a query on the audit schema, or a call of `pg_read_file`
- Then: the query fails with a permission error and no data is returned

**AC3**:

- Given: a principal with `analytics:query` but without read permission on `securityClassification`
- When: it queries the reporting view of application components
- Then: the column is absent or contains only masked values

**AC4**:

- Given: a query that runs longer than the statement timeout or returns more rows than the row limit
- When: executed
- Then: it is cancelled at the timeout, or the result is truncated at the row limit with an indicator

**AC5**:

- Given: any executed analytics query
- When: the audit log is inspected
- Then: an event with principal, query hash, duration and row count exists

**AC6**:

- Given: a write statement such as `UPDATE` or `CREATE TABLE`
- When: submitted
- Then: it is rejected and nothing changes

## Verification Method

- [ ] Method: test (automated, Testcontainers with the production role setup) + inspection of grants
- [ ] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-157, REQ-160, REQ-151
- **Follow-on effects**: REQ-175, REQ-186
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: disclosure of credentials and restricted properties, database overload – high

## Implementation Notes

- Per-principal masking in views needs a mechanism to pass the principal's visibility context to the database session (design decision; a new ADR amending ADR-013 is needed)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-17 (PR-23), rewritten per owner decision Q8 |

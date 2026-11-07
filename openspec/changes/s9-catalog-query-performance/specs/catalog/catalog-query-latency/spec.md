# Delta for catalog/catalog-query-latency

## Purpose

Defines how OEA measures the server-side processing time of catalog queries and the latency it guarantees for them at 10,000 primary entities (REQ-071).

## ADDED Requirements

### Requirement: Catalog query processing time is measured

Every catalog query and every portal query SHALL record its server-side processing time as the metric `oea.catalog.query`, tagged with the channel (`catalog` or `portal`) and the outcome (`success`, `rejected` or `timeout`).

Trace: REQ-071, #66

#### Scenario: Catalog query measured

- **WHEN** an architect queries a catalog
- **THEN** the metric `oea.catalog.query` with channel `catalog` and outcome `success` counts the query

#### Scenario: Rejected query measured

- **WHEN** a query is rejected with 400 for an unknown sort column
- **THEN** the metric counts it with outcome `rejected`

### Requirement: Catalog query latency

With 10,000 active entities of the primary metatype, the 95th percentile of the server-side processing time SHALL stay below the targets of REQ-071, verified by an automated measurement in CI: 200 ms without joins and filters, 500 ms with 3 joins in aggregate mode and on average 5 results per entity and join, 600 ms with the same joins and 2 filters combined with `and`, and 200 ms for a page beyond the first reached through the cursor.

Trace: REQ-071 AC1–AC3, #66, owner decision 2026-11-07

#### Scenario: Without joins

- **WHEN** 30 queries of the first page of a catalog without joins and filters run on 10,000 applications
- **THEN** the 95th percentile of `oea.catalog.query` is below 200 ms

#### Scenario: Three joins

- **WHEN** 30 queries run on the same catalog with 3 joins in aggregate mode and on average 5 results per entity and join
- **THEN** the 95th percentile is below 500 ms

#### Scenario: Filter and three joins

- **WHEN** 30 queries run with the 3 joins and 2 filters combined with `and`
- **THEN** the 95th percentile is below 600 ms

#### Scenario: Deep page through the cursor

- **WHEN** 30 queries each load the page after page 150 through the cursor of page 150, sorted by name and by a property
- **THEN** the 95th percentile is below 200 ms

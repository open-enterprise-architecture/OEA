# ADR-026: External System Integration — Write Access via API (optional)

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Partially superseded**: 2026-10-13 by [ADR-030](./ADR-030-postgresql-only.md) — multi-database parts no longer apply (PostgreSQL only)

---

## Context

OEA describes current and target states of an IT landscape. This data can be maintained manually
by architects — or automatically by external systems that populate the
current state from source systems:

- **Code scanners**: read repository structure and create application components
- **CI/CD pipelines**: report deployment status changes as lifecycle updates
- **Import scripts**: migrate legacy data from CMDB, ServiceNow, Alfabet, etc.
- **Rule-based engines**: check conformance and create findings as entities
- **AI-supported analysis tools**: extract architecture from artifacts

What they all have in common: They write in a structured and repeated manner to the same entities —
without prior knowledge of internal database IDs and with the requirement to be
executable multiple times idempotently.

This write capability is **optional**. OEA works fully without it.
It is activated when an installation sets the `integration` feature flag.

---

## Decision

### 1. External Identifier Field (`external_id`)

Each entity receives an optional field `external_id TEXT` — a stable identifier assigned by the
calling system (e.g. Maven artifact ID, Kubernetes service name,
CMDB CI ID).

```sql
ALTER TABLE entities ADD COLUMN external_id TEXT;
CREATE UNIQUE INDEX uq_entities_external_id ON entities (external_id)
  WHERE external_id IS NOT NULL;
```

Rules:
- Assigned by the writing system, not by OEA
- Unique per OEA instance (namespace convention recommended: `{system}:{id}`)
- Never generated automatically; remains NULL when created manually

### 2. Upsert Endpoint

```
PUT /api/v1/entities/by-external-id/{externalId}
```

Semantics: create-or-update based on the `external_id`. Returns `201 Created` on creation,
`200 OK` on update. Idempotent — repeated calls with identical data are safe.

No separate "check if exists" round trip needed.

### 3. Flat Write Schema

Writing systems do not reliably know internal property types. All
property values are therefore transmitted as `string` — the backend converts
according to `property_definitions.data_type` and returns structured validation errors.

```json
{
  "name": "Order Service",
  "metatypeId": "uuid-of-the-metatype",
  "externalId": "git:org/repo/order-service",
  "properties": [
    { "propertyDefId": "uuid", "value": "2.4.1" },
    { "propertyDefId": "uuid", "value": "active" }
  ]
}
```

Advantage: The writing system does not need to know any type mapping.
Disadvantage: Backend validation errors must be clearly addressed per property.

### 4. Batch Write

```
POST /api/v1/entities/batch
```

Accepts a list of entities and connections. Strategy selectable:

| Strategy | Behavior |
|---|---|
| `upsert` (default) | Create-or-update per `external_id` or `id` |
| `create-only` | Fails if the entity already exists |
| `skip-existing` | Skips existing ones, only creates new ones |

Atomicity: Configurable (`atomic: true/false`). In atomic mode, everything is rolled back at the
first error. In non-atomic mode: partial results
with per-item status in the response.

Maximum batch size: configurable, default 500 items.

### 5. Create Connections

```
POST /api/v1/connections
```

Minimal body: `{ sourceExternalId, targetExternalId, connectionMetatypeId }`.
Alternatively: `sourceId`/`targetId` (internal IDs). Both fields are upsert-capable
analogous to entities.

Validation against `metatype_connection_constraints` — an invalid
source-target meta type combination results in `422 Unprocessable Entity`.

### 6. Dry Run

Every writing endpoint accepts the query parameter `?dryRun=true`.
Validation is executed fully, no persistence. The response contains:
- What would be created / updated / skipped / fail
- Validation errors per item

### 7. System Identity in the Audit Log

The audit log (ADR-024) is extended by two fields:

```sql
ALTER TABLE audit.events ADD COLUMN agent_id   TEXT;  -- system identifier (e.g. "oea-scanner/1.0")
ALTER TABLE audit.events ADD COLUMN agent_run   TEXT;  -- correlation ID of a run
```

This makes it visible in the audit trail which external system created which entities in which
run — and not just "Source: API".

The `agent_id` value comes from the `X-Agent-Id` request header or the JWT claim
`agent_id`.

### 8. Metamodel Discovery

Writing systems must understand the metamodel before the first write:

```
GET /api/v1/metamodel/metatypes?q={freetext}
GET /api/v1/metamodel/allowed-connections?sourceMetatypeId={id}&targetMetatypeId={id}
```

The second endpoint answers: "Is a connection between meta type A and B allowed,
and which connection meta type is to be used for it?"

### 9. Metamodel Import/Export (Package Format)

So that external systems know the metamodel of an OEA instance without querying it
piece by piece, there is a package endpoint:

```
GET  /api/v1/admin/metamodel/export   → JSON package: MetaTypes + PropertyDefs + Viewpoints + Shapes
POST /api/v1/admin/metamodel/import   ← same format; strategy: merge | replace
```

This format is also the basis for exchangeable metamodel libraries
(e.g. "ArchiMate 3.2 base metamodel").

---

## Scope Boundaries

| What | This pattern | Not included |
|---|---|---|
| Manual maintenance by architects | No — UI side | – |
| Import from legacy file (CSV, XML) | Yes — via batch endpoint | Parser is the client's concern |
| Real-time synchronization (change streams) | No | separate topic (v2) |
| Read access by external systems | Available (resource API) | – |

---

## Security

- External write access requires dedicated service accounts (no user JWT)
- Service accounts have restricted roles: `integration-writer` (no admin operations)
- Rate limiting on batch endpoints (configurable)
- `external_id` must not contain internal IDs of other systems in plain text (namespace convention)

---

## Rejected Alternatives

### Manual maintenance only
Not scalable for large IT landscapes with frequent changes. Rejected.

### Own import file format (CSV, Excel)
Every system would have to bring a specific exporter. API is more universal. Rejected.

### Webhooks / push from source system directly into DB
No metamodel enforcement, no audit trail, no validation. Rejected.

---

## Consequences

**Positive:**
- Current state can be populated automatically and idempotently
- Arbitrary writing systems integrable without OEA-specific knowledge
- Audit trail shows the origin of every entity

**Negative / Compromises:**
- `external_id` must be assigned consistently by the writing system
- Flat write schema increases backend complexity (type conversion + error reporting)
- Batch atomicity under multi-DB (ADR-023) requires careful transaction control

---

## Affected Concept Chapters

- §6 (Entity Model, external_id), §9 (Metamodel Discovery), §14 (Audit), §21 (Integration)

## Related ADRs

- ADR-022: Structured property model
- ADR-023: Multi-DB strategy
- ADR-024: Audit separation (agent_id extension)

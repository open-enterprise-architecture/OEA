## 23. Open Issues – Status Overview

As of: 2026-06-26 — all 47 original items categorized and worked through.

---

### 23.1 Closed by ADR or Business Object

| # | Topic | Closed by | Decision (short form) |
|---|---|---|---|
| 2 | Identity schema / URN format | [ADR-001](../../adrs/ADR-001-urn-schema.md) | Integer ID internally; `urn:oea:{instance-slug}:{id}` for external references |
| 5 | BPMN storage | [business-objects/process.md](../../business-objects/process.md) | Internal model; `bpmn-contained-in` connection as repo representation; no native BPMN XML in the DB |
| 11 | Product vs. project | [ADR-003](../../adrs/ADR-003-product-vs-project.md) | `work-initiative` as built-in base; `product` and `project` as configurable metamodel subtypes |
| 13 | Continuum-repository relationship | [ADR-002](../../adrs/ADR-002-continuum-repository.md) | One repository with `scope` property (built-in/imported/organization) + import mechanism |
| 27 | API authentication scope | [ADR-006](../../adrs/ADR-006-auth-stack-choice.md) | OIDC; Microsoft Entra ID + Authentik as mandatory integrations; local auth optional |
| 30 | Maximum reification depth | [ADR-004](../../adrs/ADR-004-reification-details.md) | Depth 1 in v1.0; `allowsConnectionAsSource` flag; `maxConnectionDepth` configurable for v2.0 |
| 31 | Relation addressing | [ADR-004](../../adrs/ADR-004-reification-details.md) | All connections share the integer ID namespace with entities |
| 40 | Application-vs-technology classification default | [ADR-005](../../adrs/ADR-005-app-vs-tech-default.md) | No enforced default; two optional starter packages at bootstrapping |
| 46 | Visualization strategy / renderer notation | [ADR-007](../../adrs/ADR-007-canvas-library.md), [REQ-068](../../requirements/req/REQ-068-arc42-wysiwyg-editor.md), [REQ-078](../../requirements/req/REQ-078-embed-drawio.md) | Mermaid (client-side, no server), PlantUML (configured server), draw.io (embed via code block), Vue Flow (interactive canvas) |

---

### 23.2 Decided for v1.0 (Concept Decision)

These items are decided in the concept but still need a formal ADR at implementation.

---

#### #1 / #24 — Persistence technology and walking skeleton stack

**Decision**: PostgreSQL 15 + JSONB + Apache AGE (openCypher graph extension).

Rationale: One database instance for both query paradigms (SQL analytics + graph traversal, §22.1). JSONB enables flexible properties without schema migration for class 3 properties (§15.1). Apache AGE is open source (Apache 2.0), PostgreSQL-native, and supports openCypher. REQ-075 prescribes PostgreSQL 15 without proprietary extensions as a portability goal — AGE meets this requirement. → Formal ADR at backend sprint.

---

**#3 — Bitemporality: Is validity time (plateau) sufficient?**

**Decision**: Yes — only validity time in v1.0. No separate recording time.

Rationale: The plateau model (§11) fully covers validity periods (when is an architecture state valid?). Recording time (when was a change recorded in the system?) can be covered via the audit log (§21.8) without duplicating the data model. Full bitemporality is possible for v2.0 as an optional extension.

---

**#4 — Diff semantics: property-by-property or lifecycle-based?**

**Decision**: Both combined — exactly as defined in §11.5.

PlateauDiff contains: `entitiesAdded`, `entitiesRetired`, `entitiesModified` (property-by-property), `lifecycleTransitions`. Calculation rules are fixed in §11.5 and are binding. No further diff dimensions in v1.0.

---

#### #6 — Referential integrity across plateaus

**Decision**: Soft constraint (warning) in v1.0.

If entity A is marked `retired` in plateau P, entity B in plateau P may still reference A — the tool shows a warning ("cross-plateau conflict") but does not block. Rationale: Migration transition states are real; a hard constraint would block valid intermediate models. v2.0: configurable to error via metamodel configuration.

---

#### #10 — Schema profiles

**Decision**: Schema profiles as a formal concept are dropped.

ADR-005 starter packages take over this function. At bootstrapping, the admin selects one or more starter packages; this de facto yields an initial schema profile. Additional profiles would introduce a third configuration mechanism alongside starter packages and the `scope` property — unnecessary complexity.

---

#### #12 — SAFe capability collision (TOGAF capability vs. SAFe capability)

**Decision**: The stereotype approach is final.

- TOGAF capability = `EntityType: capability` (built-in, §6)
- SAFe capability = subtype via metamodel: `EntityType: safe-capability` with `extends: work-initiative` (or its own subtype of `capability`)

The UI displays the full EntityType name (`capability` vs. `safe-capability`). No naming conflict at data level, since the EntityType identifier is unique.

---

#### #23 / #25 — Query languages and graph query standard

**Decision**: Two-layer approach (§22.4) with openCypher as the graph standard.

```text
/api/v1/entities/search      ← typed REST queries (easy entry)
/api/v1/graph/traverse       ← structured traversal API (intermediate)
/api/v1/graph/cypher         ← openCypher for powerful graph queries (advanced)
/api/v1/analytics/query      ← SQL for analytics
/api/v1/analytics/views/{n}  ← named predefined views
```

openCypher (instead of Gremlin or ISO GQL): Apache AGE implements openCypher; tooling maturity; Neo4j compatibility for a later DB migration. No proprietary DSL — investment protection for query authors.

---

#### #26 — Event transport

**Decision**: Server-Sent Events (SSE) as default in v1.0.

`GET /api/v1/events` as an SSE stream; events are JSON with `type`, `entityId`, `plateauId`, `timestamp`. WebSocket for the collaboration session in v1.x (presence and co-editing, [§21.10](../70-platform/21-api-architecture.md), [ADR-047](../../adrs/ADR-047-real-time-collaboration.md); revised 2026-10-28, previously "optional from v2.0"). Kafka/webhooks as integration adapters (not part of the core event bus).

---

**#28 — Module isolation: same process, sidecar, or standalone services?**

**Decision**: Monolith-first in v1.0 — all modules in the same process.

Internal module communication via an internal event bus (no network overhead). Module boundaries are ensured by a clear package structure and interface contracts, so that later extraction to a sidecar/service is possible without an API breaking change. Rationale: The operational overhead of a distributed system is disproportionate for an OSS tool in v1.0.

---

**#29 — CLI query tool: full query engine locally or proxy?**

**Decision**: v1.0 — CLI is a proxy to the server (REST API client).

`oea query --server http://localhost:8080 "MATCH ..."` forwards queries to the server. Advantage: no duplicate query engine code in v1.0. v2.0: embedded DuckDB/Kuzu for offline and CI pipeline use without a running server (§22.10).

---

#### #32 — Relation lifecycle per plateau: UI representation

**Decision**: Edge color on the canvas by lifecycle state; 3-dot circle for the connection panel.

Color convention (consistent with §11.5 plateau diff visualization):

- `planned` / new in plateau: green
- `active` / unchanged: gray/neutral
- `sunset` / `modified`: yellow/orange
- `retired` / removed from plateau: red

Separate relation list panel: available in the detail panel of the canvas entity, not as a separate top-level view (too complex for v1.0).

---

#### #36 — Audit trail storage

**Decision**: Same PostgreSQL instance, separate append-only table `audit_events`.

No DELETE permission on this table, not even for admins (constraint at DB level). Fields: `id`, `timestamp`, `actor_id`, `action_type`, `entity_id`, `before_snapshot`, `after_snapshot`, `session_id`. v2.0: optional export to SIEM (Splunk, Elastic, Wazuh) via webhook adapter.

---

### 23.3 Deferred — Integration Phase or v2.0

These items are deliberately not decided for v1.0. They will be addressed in the respective module sprint or in v2.0.

| # | Topic | Deferred until | Initial orientation |
|---|---|---|---|
| 7 | ITSM connector scope | ITSM module sprint | ServiceNow as first target system (enterprise prevalence, SH-05); generic REST adapter as fallback |
| 8 | Sync conflict resolution | ITSM module sprint | Last-write-wins as default; manual resolution configurable; master system flag per property |
| 9 | PPM connector scope | PPM module sprint | Jira Software + OpenProject as first target systems; ServiceNow SPM as enterprise option |
| 14 | TRM taxonomy maintenance | v2.0 / starter package | TOGAF example TRM initially; own TRM importable as an organization-specific starter package |
| 15 | Scope expressiveness for schema evolution | v2.0 | v1.0: properties + IN lists (pattern 2, §15.2); graph queries as scope criterion from v2.0 |
| 16 | Data quality score weighting | v2.0 | Configurable per organization; schema defines weight property; default weights in the starter package |
| 17 | Person data retention and GDPR | GDPR module sprint | v1.0: reference-only (name + email); no biometric/sensitive data directly in OEA |
| 18 | Skill taxonomy | v2.0 | SFIA only as import option (not shipped due to SFIA license); import format: CSV/JSON |
| 19 | BPM tool adapter scope | BPMN module sprint | BPMN XML import from Camunda as first target; read-only initially |
| 20 | PCF integration | v2.0 | Do not ship APQC-PCF (license required); code references only; import option for licensees |
| 21 | Carbon assessment methodology | v2.0 | SCI (Software Carbon Intensity) as methodology candidate; manual in v1.0; automatic estimation v2.0 |
| 22 | Contract document storage | v2.0 | v1.0: external references only (URL/ID to DMS); no own document storage in OEA |
| 33 | GRC adapter priority | GRC module sprint | Verinice first (OSS, ISMS-oriented) or OSCAL export; depends on community feedback |
| 34 | Record of processing activities format | GDPR module sprint | BSI orientation (DE) as primary schema; CNIL/ICO as secondary option |
| 35 | Control framework integration | v2.0 | v1.0: structural support only (EntityType + relation); ISO 27001 Annex A as continuum artifact from v2.0 |
| 37 | Protection need inheritance | v2.0 | v1.0: manual maintenance with validation hint; automatic derivation (DataEntity → Application) from v2.0 |
| 38 | Property-level authZ configuration | Security sprint | Configurable per deployment (YAML in the admin area); admin UI in v1.1 |
| 39 | Segregation of duties enforcement | v2.0 | v1.0: soft constraint (warning); hard constraint configurable from v2.0; override by configured admin role |
| 41 | Requirements versioning trigger | Requirements module sprint | Trigger: changes to `statement`, `acceptanceCriteria`, `priority`; typo/format changes = no version bump |
| 42 | System requirements scope | Requirements module sprint | Both allowed (direct capture + external reference); admin-configurable via metamodel |
| 43 | ReqIF import/export | Requirements module sprint | Module/adapter, not core; define mapping conventions during module development |
| 44 | Logical stub auto-generation | v2.0 | Opt-in; auto-generated stubs with `(auto)` badge; no automatic expiry; "Promote" action for maintenance |
| 45 | Inventory view scope | UX sprint | Default: `applicationcomponent` + `technologycomponent` visible; all others configurable via viewpoint (ADR-002) |
| 47 | Legacy tool migration | v1.1 | ArchiMate Open Exchange (Archi/Sparx EA) as first import format; XMI as second |

---

← [Analyzability & Query Architecture](../70-platform/22-analyzability.md) · [🏠 Overview](../README.md) · [Next Steps](24-next-steps.md) →

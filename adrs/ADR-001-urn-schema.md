# ADR-001: URN Schema and Stability Guarantees

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer (entity.md, REQ-070)
**Informed**: all stakeholders

---

## Context and Problem

Every entity in the EA repository needs a unique, stable ID. This ID appears in API endpoints, cross-references between entities, audit logs, version tracking, and in external systems (CMDB, GRC, PPM) that reference OEA. The choice shapes many follow-up decisions.

**Prior decision already made** (entity.md v0.2.0): ArchitectureEntity.id is a **positive integer value (integer ≥ 1)**, unique per instance, sequential, immutable after creation. All existing requirements (REQ-070: `[[Name|entity:ID]]`), API endpoints (`/api/v1/entities/{id}`), and user stories already use these integer IDs. The ADR decision confirms and complements this implicit prior decision.

## Decision Drivers

- **Stability**: ID must remain stable for years, even when the entity is renamed
- **API simplicity**: Integer IDs are native in REST paths, joins, and SQL
- **Human readability** of linking: `[[CRM-System|entity:42]]` — name is visible, ID in the background
- **Global uniqueness** for external references (CMDB, cross-instance)
- **Performance**: Integer join is faster than string UUID join (relevant at 500k entities, REQ-074)

## Considered Options

### Option 1: UUID v4 internally
- Pro: global uniqueness without instance context
- Con: not human-readable; worse than integer for DB performance in JOIN operations

### Option 2: Descriptive URN with hierarchy
- Pro: self-describing (`urn:ea:org:application:crm-system`)
- Con: unstable on renaming — directly contradicts the stability driver

### Option 3 (chosen): Internal integer ID + constructible external URN
- **Internal**: positive integer (already established by entity.md)
- **External / cross-reference**: `urn:oea:{instance-slug}:{integer-id}` (e.g. `urn:oea:acme-corp:42`)
- Pro: best DB performance; stability guaranteed; externally constructible; no duplicate maintenance effort
- Con: external URN without semantic content hint (type is not in the URN)

### Option 4: ULID / sortable IDs
- Pro: sortable by time
- Con: not human-readable; no added value over integer for this use case

## Decision

**Option 3 is the decision.**

- **Primary ID**: `integer` (positive integer value, assigned automatically, immutable)
- **External reference URN** (for CMDB integration, cross-instance references, audit export): `urn:oea:{instance-slug}:{id}`
  - `instance-slug`: configurable during bootstrapping (e.g. `acme-corp`, `oea-demo`); immutable after first use
  - `id`: the integer ID of the entity
  - Example: `urn:oea:acme-corp:42`
- The `name` of an entity is **not part of the ID** — it is a mutable attribute
- API-internal: always integer path (`/api/v1/entities/42`)
- Export format (ADR-002 packages, audit log): URN format

## Consequences

### Positive Consequences
- Integer IDs are native in PostgreSQL, SQL JOINs, and optimal for cursor pagination (REQ-071)
- External URN is deterministically constructible from known parts — no separate URN table needed
- Entity mention format `[[Name|entity:42]]` (REQ-070) is consistent with this decision
- Stable IDs are a prerequisite for ID-stable linking in Arc42 documents (BR-07)

### Negative Consequences / Trade-offs
- `instance-slug` must be set during bootstrapping and is immutable afterwards (otherwise all external URNs become invalid)
- No type information in the ID — external systems must query the type via the API
- Cross-instance references (e.g. OEA instance A references an entity in OEA instance B) are possible via URN, but v1.0 does not resolve these references automatically

### Follow-up Decisions
- Bootstrapping REQ must include `instance-slug` as a mandatory field (complements REQ-013/014)
- Export format for audit log and package import (ADR-002) uses URN format

## References

**Concept Chapters**:
- [§2 Meta-Metamodel](../concept/00-overview/02-meta-metamodel.md)
- [§15 Schema Evolution](../concept/40-extensibility/15-schema-evolution.md)

**Dependent ADRs**: ADR-002 (package format uses URN), ADR-004 (reification — connections also have integer IDs)

**Related Requirements**: entity.md (BR-01–04), REQ-070 (entity mention), REQ-013/014 (bootstrapping)

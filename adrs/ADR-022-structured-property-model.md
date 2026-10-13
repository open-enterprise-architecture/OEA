# ADR-022: Structured Property Model with Category, Temporal Mapping, and Delta Versioning

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)

---

## Context

The previous model stores user-defined properties of entities as `properties JSONB`
directly in the `entities` table. Versioning is done through full snapshots in
`entity_versions.properties JSONB`.

This approach creates two problems:

1. **Data redundancy**: On every change, all properties of an entity are stored again,
   including the unchanged ones. With 20 properties and one change, this results in 20× redundancy per version.
2. **Lack of structure**: Property definitions are buried in the JSONB of `metamodel_configurations`
   — no real DB FK, no real schema, no indexable query on individual values.
3. **Rigidity in mapping**: Whether a property is mandatory for a meta type cannot be changed
   per effective date. Conformance rules cannot adapt over time.

---

## Decision

Introduction of a **four-layer structured property model**:

```text
property_categories          (grouping of property definitions)
      ↓
property_definitions         (reusable property types)
      ↓
metatype_property_mappings   (link property ↔ meta type, with necessity + effective date)
      ↓
entity_property_values       (effective values per entity instance)
```

### Layer 1 – property_categories

Flat (single-level) categorization of property definitions for structuring the UI
(e.g. "General", "Technical", "Governance", "Compliance").

No hierarchy (parent_id) in v1 — can be added later via parent_id without data loss.

### Layer 2 – property_definitions

Global, meta-type-independent type definitions. A property definition (e.g. "version") can be mapped in
several meta types with different necessity.

Typed-value columns instead of polymorphic JSONB:
`value_text`, `value_int`, `value_bool`, `value_date`, `value_ref` (FK→entities), `value_enum`

Additionally: `constraints JSONB` for data-type-specific validation rules (max_length, pattern,
min/max) and `i18n_labels JSONB` for multilingual labels.

### Layer 3 – metatype_property_mappings

Links a property definition to a meta type and specifies:

- `necessity`: `mandatory` | `warning` | `optional`
- `default_value JSONB`: optional preset value when creating a new entity (not mandatory)
- `valid_from DATE`: effective date of this necessity setting

**Temporal pattern (valid_from only, no valid_to)**:
When determining the current necessity, the row with the highest `valid_from ≤ CURRENT_DATE`
is used. No updates on existing rows — new effective dates are inserted as a new row.

```sql
SELECT necessity FROM metatype_property_mappings
WHERE metatype_id = :mt AND property_def_id = :pd
  AND valid_from <= :check_date
ORDER BY valid_from DESC
LIMIT 1;
```

This allows conformance queries for arbitrary historical dates (UC-20).

### Layer 4 – entity_property_values

One row per (entity_id, property_def_id) — current value. No JSONB blob in `entities` anymore.

For versioning, `entity_property_value_history` is maintained as a delta table:

- **One row per changed property** with the value *before* the change
- Unchanged properties produce no entry → no redundancy
- Restore (UC-15/UC-16): backward application of all delta rows up to the target version

### Additional Consequence – metatypes as a Real Table

Since `metatype_property_mappings.metatype_id` needs a real FK, meta types are extracted from the
`metamodel_configurations.config` JSONB and moved into their own `metatypes` table.
`entities.entity_type_id TEXT` (soft reference) becomes `metatype_id UUID FK→metatypes.id`.

The `config` JSONB in `metamodel_configurations` retains: viewpoints, architectureLayers,
constraintRules, stereotypes, documentCollectionDefinitions.

---

## Rejected Alternatives

### A – Pure JSONB (previous approach)

Simplest schema, but data redundancy per version and no indexability of individual values.
**Rejected**: Redundancy grows linearly with number of properties × versions.

### C – Event Sourcing (append-only)

Perfect audit log, no data loss. But too complex for v1: requires its own event-fold infrastructure.
**Deferred**: can be introduced in v2, the model is compatible.

---

## Consequences

**Positive:**

- Versioning redundancy is eliminated (only deltas stored)
- Individual property values are indexable and directly queryable
- Conformance (UC-20) can be evaluated historically and forward-looking (effective date)
- Property definitions are reusable across meta types
- Default values per mapping reduce manual effort when creating entities

**Negative / Compromises:**

- Schema complexity increases (+4 tables)
- Restore logic (UC-15): backward delta application is more complex than loading a snapshot
- Reading all properties of an entity requires a JOIN on `entity_property_values`
  (standard SQL, efficient with an index on `entity_id`)
- Migration of existing JSONB data requires a transformation script

---

## Affected Concept Chapters

- §6 (Entity Model), §9 (Metamodel), §14 (Audit and Versioning), §20 (Conformance)

## Related ADRs

- ADR-016: Optimistic locking (version column in entities remains)
- ADR-019: Soft-delete strategy (deleted_at in entities remains)
- ADR-021: parent_entity_id / structural grouper

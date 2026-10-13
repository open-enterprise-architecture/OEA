---
id: ADR-021
title: Implicit Parent-Child Connection on Component Nesting
status: proposed
date: 2026-06-28
deciders:
  - Repository Owner
categories:
  - Data Model
  - Semantics
  - Navigation
references:
  concept:
    - concept/20-entities/06-core-entity-types.md
  requirements:
    - REQ-141
    - REQ-142
  related_adrs:
    - ADR-020
    - ADR-010
---

# ADR-021: Implicit Parent-Child Connection on Component Nesting

## Status

Proposed

**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: [Kurt – Lead Enterprise Architect](../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md) (SH-03), [Michael – Solution Architect](../business-analysis/stakeholders/SH-04-michael-solution-architect.md) (SH-04)

## Context

In the explorer (ADR-020), components can be placed hierarchically under other components. The question is whether this structural placement has semantic effects — i.e. whether a connection (ArchiMate composition or aggregation) is automatically created between parent and child nodes.

In addition, a distinction must be made between:

- **Structuring aids** (`isStructuralGrouper=true`): folder-like nodes that serve exclusively for organization and do not represent an EA instance themselves
- **Instantiated entities** (`isStructuralGrouper=false`): real ArchitectureEntity instances (e.g. "SAP S/4HANA")

## Decision Drivers

- Nesting should deliver semantic added value, not just be visual grouping
- ArchiMate knows composition (part-whole, strong) and aggregation (part-whole, weak)
- Structuring aids (e.g. "Backend systems" as a folder) should not create a content connection
- The implicit connection must be traceable, visible, and deletable

## Options

### Option A — Implicit composition connection (instances only)

When placing an ArchitectureEntity element (`isStructuralGrouper=false`) under another element, a composition connection (`entityType: composition`) is automatically created as a new ArchitectureEntity. This connection appears in the "Connections" category of the solution. When the element is detached, the implicit connection is deleted.

For structuring aids (`isStructuralGrouper=true`) no connection is created.

**Pro**: Direct semantic statement; composition visible in the model; ArchiMate-compliant
**Con**: Surprise effect (user sees a new connection); model grows quickly

### Option B — No implicit connection (purely visual hierarchy)

Nesting is navigation only. No automatic connection is created.

**Pro**: No surprises; cleanly separated between navigation and model
**Con**: Loss of semantic information; composition must be captured manually afterwards

### Option C — User chooses connection type on every placement

A dialog on placement asks: "Composition, aggregation, or no connection?"

**Pro**: Maximum control; user decides
**Con**: High interaction effort; too complex for frequent operations

## Decision

**Option A — Implicit composition connection, instances only.**

When placing an instantiated entity (`isStructuralGrouper=false`) as a child of another entity, an ArchiMate composition connection is created automatically. The connection is labeled in the UI as "auto (Composition)" and can be manually converted to "Aggregation" or deleted. Structuring aids (`isStructuralGrouper=true`) receive no implicit connection.

## Consequences

- `ArchitectureEntity` receives two new fields: `parentEntityId` (nullable FK to own table) and `isStructuralGrouper` (boolean, default: false)
- Business rule: `parentEntityId` may only be set on elements (`isConnection=false`); connections cannot have children
- The implicit composition connection has `source=parentEntityId`, `target=childEntityId`, `autoCreated=true`
- When the `parentEntityId` field is cleared (detached from hierarchy), the `autoCreated=true` connection is removed automatically
- Manually changed connections (`autoCreated=false`) survive detachment
- `data-model.puml` must be updated: `parent_entity_id` and `is_structural_grouper` in the `entities` table
- Affected BO: `entity.md`

## Affected Concept Chapters

- §6 Core Entity Types
- §10 N-Connection / Data Lineage (ADR-010)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Solution Architect | Initial draft |

# ADR-010: Modeling the DataFlow↔DataObject Relationship (n-Connection vs. Property String)

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: SH-02 (Lukas), SH-03 (Kurt)
**Informed**: all stakeholders

## Context and Problem

In [UC-08 (Data Lineage)](../requirements/use-cases/UC-08-model-data-lineage.md) it must be modeled which **data objects** (`data-object` entities) a **data flow** (`data-flow` connection) transports. This is the key relationship for traversable lineage: "Which systems touch data object X?"

There are two fundamentally different modeling approaches:

1. **Property string** (`carriedDataObjectIds`): The DataFlow stores the referenced DataObject IDs as a comma-separated string in a PropertyValue.
2. **n-connection** (`carries-data`): A standalone connection type connects the DataFlow connection as source entity with the DataObject entity as target. A connection pointing to another connection as its source is an **n-connection** (second-degree connection).

Abacus (Avolution) and many other EA tools support only direct edges between nodes. Connections there are not first-class objects that can themselves act as the endpoint of another connection. This is a structural limitation that prevents complex relationship models — e.g. "which security control applies to which data flow?" or "which compliance requirement applies to which interface?".

OEA, through the `isConnection=true` semantics on `EntityTypeDefinition` and the `sourceEntityId`/`targetEntityId` model in `ArchitectureEntity`, has the **architectural foundation for n-connections** — the question is whether and how far this is enabled.

## Decision Drivers

- **Expressiveness**: Can the model represent arbitrarily complex relationships between relations? (differentiator compared to Abacus)
- **Queryability**: How laborious is lineage traversal in the backend?
- **Renderer complexity**: Can Vue Flow (ADR-007) meaningfully display edges that are themselves nodes of another edge?
- **Migratability**: Can a simpler solution in v1.0 later be migrated to the more powerful solution without data loss?
- **Metamodel control**: Should organizations decide themselves which modeling depth they need for data flows? (different needs depending on compliance requirements)

## Considered Options

### Option A: Property string (`carriedDataObjectIds`)

The `data-flow` EntityType receives a PropertyDefinition `carriedDataObjectIds` (varchar). Values: comma-separated integer IDs of `data-object` entities. Example: `"42,43"`.

The lineage API parses this string server-side and follows the IDs.

- **Pro**:
  - No change to the entity model (BR-04 remains unchanged)
  - Simple to implement; no special renderer handling
  - Works with the PropertyValue system from v0.6.0
- **Con**:
  - Soft reference: no FK constraint; IDs can become invalid (entity.md E2)
  - String parsing in the backend on every lineage query (performance with many data flows)
  - No type safety: a wrong value silently breaks the lineage
  - Only one fixed property name; for other relationship annotation needs (e.g. security controls on data flows), further property strings would have to be invented
  - Hard to extend: column-level lineage (field to field) cannot be cleanly represented in a string

### Option B: n-connection (`carries-data`)

A new EntityType `carries-data` (isConnection=true) is defined in the metamodel:
- `sourceEntityId` → ID of a `data-flow` connection (isConnection=**true**)
- `targetEntityId` → ID of a `data-object` entity (isConnection=false)

This presupposes that BR-04 in `entity.md` is relaxed: `sourceEntityId` may also point to an ArchitectureEntity whose meta type is `isConnection=true` — provided the own EntityType explicitly allows it (`allowsConnectionSource=true` on EntityTypeDefinition, or regulated via metamodel constraint).

```
SAP ERP (1) ──[data-flow: 5]──► Data Warehouse (2)
                   │
             [carries-data: 103]
                   │
                   ▼
            Customer master data (42)
```

- **Pro**:
  - True FK: `carries-data` entity has sourceEntityId=5 (DataFlow) and targetEntityId=42 (DataObject); referential integrity fully modelable
  - Extensible: `carries-data` can get its own PropertyDefinitions (e.g. `columnMapping`, `transformationRule`, `mandatory`)
  - n-connection as a general principle: the same pattern applies to `security-control → data-flow`, `compliance-requirement → interface`, etc. — no special solution, but a generic model
  - Column-level lineage extensible in v2.0 without schema break (property on `carries-data`)
  - Clean traversal: standard graph query without string parsing
  - **Clear competitive advantage over Abacus**: Abacus cannot model connections as endpoints of other connections; this is an often-cited structural weakness
- **Con**:
  - BR-04 in `entity.md` must be adjusted (sourceEntityId may point to connection entity if the metamodel allows it)
  - Vue Flow (ADR-007) normally renders edges between nodes; an edge whose source is itself an edge needs a special canvas representation (e.g. edge as anchor point on another edge — pattern known from hypergraphs and BPMN annotations)
  - More complex in the backend: graph traversal must understand connection entities as traversable nodes
  - Users must understand that a connection can itself appear as source/target — learning curve

### Option C: Hybrid approach (A for v1.0, migration to B in v2.0)

In v1.0 Option A is implemented (`carriedDataObjectIds` as property string). The data model is designed so that a later migration to Option B is possible: the property IDs are used as a seed for generating `carries-data` connections (migration script).

- **Pro**:
  - v1.0 can be delivered faster
  - No renderer special case in v1.0
  - Migration to B is possible without changing existing DataFlow entities
- **Con**:
  - Technical debt: property string and n-connection coexist temporarily
  - Migration script is error-prone (what happens with unresolved IDs?)
  - If the migration never takes place, the model remains permanently weaker than it could be

## Decision

We choose **Option B (n-connection)**, because it corresponds to OEA's core design principle (connections as first-class entities with integer IDs), creates a clear competitive advantage over Abacus, and the renderer question is solved by the **3-dot-circle pattern**.

**Renderer solution (3-dot circle, reference Obsidian)**:
A connection that is linked to further entities via n-connections (i.e. is itself the `sourceEntityId` of another connection) receives a small circle with three dots (`•••`) on its line in the canvas. Double-click opens a **connection panel** with all linked entities and connections. At the top edge of the panel are filter chips, one per existing connection type (e.g. `carries-data (2)`, `security-control (1)`). The basic idea comes from Obsidian's graph view / hover preview.

**Metamodel control**:
`allowsConnectionAsSource: bool` (default: `false`) is added as a new field on `EntityTypeDefinition` in MetamodelConfiguration. Only EntityTypes with `allowsConnectionAsSource=true` (e.g. `data-flow`) may appear as `sourceEntityId` in another connection. This is regulated by `entity.md BR-04` (relaxation of the `isConnection=false` obligation to `allowsConnectionAsSource=true`).

**Depth**: n-connections are **nested once** for v1.0 (connection-of-connection). Connection-of-connection-of-connection is not in scope for v1.0.

**Prerequisite for acceptance (fulfilled)**: Formal confirmation that the change of BR-04 (entity.md) and the new field `allowsConnectionAsSource` on EntityTypeDefinition are approved – done with entity.md v0.2.0 (BR-04 extended, 2026-06-26).

## Consequences

### Positive Consequences

- **entity.md BR-04** is relaxed: `sourceEntityId` may point to an ArchitectureEntity whose meta type is `isConnection=true`, provided its EntityTypeDefinition has `allowsConnectionAsSource=true`
- **metamodel-configuration.md**: `allowsConnectionAsSource: bool` (default: `false`) on EntityTypeDefinition; only explicitly approved types (e.g. `data-flow`) can appear as source in n-connections
- **Canvas**: 3-dot-circle indicator on connection line; double-click → connection panel with type filters (REQ-063)
- **Lineage API**: graph traversal understands connection entities as traversable intermediate nodes; no string parsing (REQ-062)
- **Extensible**: `carries-data` can be extended in v2.0 with a `columnMapping` property for column-level lineage without schema break

### Negative Consequences / Trade-offs

- More complex canvas renderer (3-dot indicator must be computed on every load: does this connection have n-connections?)
- Backend must understand connection entities as graph nodes (not only edges)
- User learning curve: that a line is itself an object that can have further connections is conceptually more demanding than property strings

### Follow-up Decisions

- Adjust entity.md BR-04 (approval of `allowsConnectionAsSource=true` types as source)
- MetamodelConfiguration v0.7.0: `allowsConnectionAsSource` on EntityTypeDefinition

## References

**Concept Chapters**:
- §6 Core Entity Types
- §13 Business–Technology Linking (lineage as bridge)
- §14 Extensibility

**Related ADRs**:
- [ADR-007](./ADR-007-canvas-library.md) – Vue Flow as canvas; renderer capabilities relevant for n-connection
- [ADR-004](./ADR-004-reification-details.md) – Reification (connections as first-class objects); conceptually related

**Use Cases / Requirements**:
- [UC-08: Model and Analyze Data Flow Map (Data Lineage)](../requirements/use-cases/UC-08-model-data-lineage.md)

**Business Objects**:
- [entity.md](../business-objects/entity.md) – BR-04 is directly affected by this decision
- [metamodel-configuration.md](../business-objects/metamodel-configuration.md) – possibly `allowsConnectionAsSource` on EntityTypeDefinition

## Notes

Abacus (Avolution) weakness: Connections there are pure edges (relationships), not first-class objects. It is not possible to define a connection as source or target of another connection. Typical consequence: annotating data flows with security controls, compliance requirements, or data classifications must be realized via workarounds (custom attributes on the edge as free text) – semantically poor, not queryable.

OEA's integer ID on every ArchitectureEntity (including connections) creates the technical foundation for n-connections. The open question is not whether it works, but **how far the metamodel degree of freedom is opened** and which display solution is chosen for the canvas.

Column-level lineage (field-to-field mapping) is explicitly not in scope for v1.0 (UC-08), but the n-connection approach (Option B) would make it possible to extend `carries-data` in v2.0 with a `columnMapping` property — without schema break.

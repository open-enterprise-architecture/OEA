# ADR-004: Reification Details (Max Depth, Addressing, UI)

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer (entity.md, ADR-010, REQ-063, US-066)
**Informed**: all stakeholders

---

## Context and Problem

The concept allows reified relations: relations can themselves be endpoints of other relations. Three detail questions were open:

1. **Maximum reification depth**: How many levels deep may a connection itself be a source?
2. **Relation addressing**: Do all connections have their own ID / are they directly addressable?
3. **UI representation**: How are reified relations displayed on the canvas?

**Prior decisions already made**:
- **entity.md v0.2.0**: All ArchitectureEntities (elements AND connections) share the same integer ID space. `sourceEntityId` may point to a connection if `allowsConnectionAsSource=true` (ADR-010).
- **ADR-010 (accepted, 2026-06-26)**: n-connection for data lineage: `carries-data` connects DataFlow (connection) with DataObject; max. 1 level deep for v1.0; `allowsConnectionAsSource` flag on EntityTypeDefinition.
- **REQ-063 + US-066**: Canvas shows 3-dot-circle indicator on connections with n-connections; double-click opens connection panel.

The ADR decision confirms and consolidates these implicit prior decisions.

## Decision Drivers

- **Avoid complexity**: Too deep reification leads to incomprehensible models
- **Performance**: Every addressable connection costs storage and index effort (important for REQ-074: 500k entities)
- **Consistency**: Uniform entity and connection addressing
- **Visualizability**: Vue Flow (ADR-007) must be able to render the representation

## Question 1: Maximum Reification Depth

### Options
- Option A (chosen): Default 1 level (v1.0); configurable in MetamodelConfiguration for v2.0
- Option B: Hard limit of 1 level, not configurable
- Option C: Unlimited with warning

### Decision on Question 1: **Option A**

- **v1.0**: Maximum depth = 1 level (`sourceEntityId` of a connection may point to another connection; `targetEntityId` must always be a non-connection — entity.md BR-04)
- **Mechanism**: `allowsConnectionAsSource: boolean` (default: false) on EntityTypeDefinition; only if `true` may the type act as `sourceEntityId` of a further connection
- **v2.0**: MetamodelConfiguration receives `maxConnectionDepth: integer` (default: 1); can be raised to 2 or 3 for complex lineage graphs
- **Rationale**: 1 level fully covers the primary use case (data lineage, carries-data). Deeper chains are rare in practice and produce exponentially more complex queries.

## Question 2: Addressing

### Options
- Option A (chosen): All connections have an integer ID and are directly addressable (`/api/v1/entities/{id}`)
- Option B: Only connections with meta properties have their own ID
- Option C: ID only on demand at first use of reification

### Decision on Question 2: **Option A**

- **Already decided by entity.md**: ArchitectureEntity covers elements AND connections. Both receive an integer ID from the shared sequence namespace on creation.
- `GET /api/v1/entities/{id}` works for any connection identically to any element
- All connections are URN-addressable: `urn:oea:{instance-slug}:{id}` (ADR-001)
- **Rationale**: Uniform model; no special treatment; catalog and join (UC-06, REQ-044–046) can use connections as primary entities (REQ-065); no on-demand ID assignment needed

## Question 3: UI Representation

### Options
- Option A: Reified connection as its own node in diagrams
- Option B (chosen): Small symbol (3-dot circle) on the edge
- Option C: Tooltip/detail view on click

### Decision on Question 3: **Option B (3-dot circle)**

- **Already decided by ADR-010 + REQ-063 + US-066**: Connections with at least one n-connection show a ••• indicator on the edge
- With multiple n-connections: single indicator with number (`••• 3`)
- Double-click on the indicator opens the connection panel (US-067): lists all n-connections with type filter chips
- **Reference**: Obsidian graph view (similar semantics for "has properties")
- **Rationale**: A dedicated node (Option A) would make the diagram drastically more complex; tooltip-only (Option C) is too hard to discover. The 3-dot symbol signals "more behind this" without breaking the layout.

## Overall Decision

| Question | Decision | Implemented by |
|---|---|---|
| Max depth | 1 level (v1.0); configurable for v2.0 | `allowsConnectionAsSource` on EntityTypeDefinition; entity.md BR-04 |
| Addressing | All connections have an integer ID; directly addressable | entity.md; shared ID namespace; ADR-001 |
| UI | 3-dot-circle indicator on edges; connection panel via double-click | ADR-010; REQ-063; US-066; US-067 |

## Consequences

### Positive Consequences
- Uniform API: no distinction between connection and element in `/api/v1/entities/{id}`
- Performance: integer ID join optimal for connections as well (REQ-071, REQ-074)
- Canvas stays clear: 3-dot symbol instead of additional nodes
- v2.0 extension to deeper reification possible without breaking change (`maxConnectionDepth` configurable)

### Negative Consequences / Trade-offs
- 1-level limit restricts some graph topologies; deliberate simplification for v1.0
- 3-dot indicator requires that Vue Flow (ADR-007) supports edge overlays (available via `EdgeLabelRenderer` in `@vue-flow/core`)

## References

**Concept Chapters**:
- [§2.1 Reification](../concept/00-overview/02-meta-metamodel.md)

**Dependent ADRs**: ADR-010 (n-connection data lineage — special case of reification)

**Related Requirements**: entity.md (BR-04), REQ-063 (3-dot indicator), REQ-064 (GDPR catalog), REQ-065 (n-connection catalog join), US-066, US-067

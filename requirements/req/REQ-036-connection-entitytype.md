---
id: REQ-036
title: Connection EntityType in the metamodel
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
    - SH-02
    - SH-04
  concept:
    - concept/20-entities/06-core-entity-types.md
    - concept/40-extensibility/14-extensibility.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-036: Connection EntityType in the metamodel

## Statement

When creating a custom EntityType, the system MUST allow setting the flag `isConnection=true`; a connection type MUST enforce exactly one `source` and one `target` reference on each of its instances; `source` and `target` MAY point to instances of arbitrary EntityType classes – including other connection instances, which yields T-relationships; the allowed types for `source` and `target` MAY optionally be restricted.

## Rationale

In enterprise architecture, connections between components often need to carry attributes themselves (protocol, data classification, bandwidth) and to be attachable to other connections. A simple relation field on the source object is not sufficient for this: the connection is an independent, referenceable object. The T-relationship (connection → connection) makes it possible to model branches, aggregation points, and network topologies without introducing separate helper objects. This corresponds to concepts such as the ArchiMate junction or the UML association class.

## Context

Connection types are full EntityTypes with all features: properties, own ID, lifecycle, audit log, constraint rules. The `isConnection` flag merely adds two mandatory fields (`source`, `target`) and makes it possible to display instances visually as edges instead of nodes. A connection type can have its own properties (e.g., `DataFlow` with `protocol`, `dataClassification`). The `allowedSourceTypes` and `allowedTargetTypes` lists restrict, where needed, which types are allowed as endpoints.

**T-relationship (connection → connection)**: If `DataFlow` contains the type `DataFlow` itself in `allowedSourceTypes` or `allowedTargetTypes` (or the lists are empty/null), a DataFlow instance can reference another DataFlow instance as source or target. This creates a T shape in the graph: the attached DataFlow branches off from the midpoint of the referenced DataFlow.

## Type-Specific Fields

### For type=functional

**Inputs** (when creating/editing an EntityTypeDefinition via GUI or import):

| Field | Type | Default | Description |
|---|---|---|---|
| `isConnection` | boolean | false | Marks the type as a connection type |
| `allowedSourceTypes` | string[] | null | Optional: list of allowed EntityType names for `source`; null = all allowed |
| `allowedTargetTypes` | string[] | null | Optional: list of allowed EntityType names for `target`; null = all allowed |

**Processing on instance creation** (when an entity of the connection type is created):

- System enforces the presence of `source` (reference to an existing entity of any type – unless `allowedSourceTypes` restricts it)
- System enforces the presence of `target` (analogous)
- Validation: `source` and `target` must reference existing entities of the allowed types
- `source` or `target` may be a connection instance → T-relationship; no recursive circularity check in MVP (cycles are permitted, presentation is the responsibility of the view layer)
- A connection instance can additionally have its own properties (like any other EntityType)

**Error Cases** (on instance creation):

- `source` missing → validation error "source is a mandatory field for connection type X"
- `target` missing → analogous
- `source` is not an allowed type (per `allowedSourceTypes`) → validation error with a specific message
- `source` or `target` entity does not exist → validation error "Entity not found"

**Error Cases** (on EntityType definition):

- `allowedSourceTypes` or `allowedTargetTypes` set, but `isConnection=false` → ignored (BR-08); no error message, but a note in the GUI

## Acceptance Criteria

**AC1**:

- Given: EntityType `DataFlow` with `isConnection=true`, `allowedSourceTypes=[ApplicationComponent, DataFlow]`, `allowedTargetTypes=[ApplicationComponent, DataFlow]`
- When: Kurt creates a new `DataFlow` instance without specifying `source`
- Then: the validation error "source is a mandatory field" appears; no object is saved

**AC2**:

- Given: `DataFlow` instance DF-1 (source=AppA, target=AppB) already exists
- When: Kurt creates a new `DataFlow` instance DF-2 with `source=DF-1` (connection → connection) and `target=AppC`
- Then: DF-2 is saved; in the graph DF-2 points to DF-1 (T-relationship: DF-2 branches off DF-1, AppC is the endpoint)

**AC3**:

- Given: `DataFlow` has `allowedSourceTypes=[ApplicationComponent]` (DataFlow not in the list)
- When: Kurt wants to create DF-2 with `source=DF-1` (a connection instance)
- Then: the validation error "DataFlow is not an allowed source type for DataFlow" appears

**AC4**:

- Given: EntityType `DataFlow` with `isConnection=true` and property `protocol (enum)`
- When: Kurt creates an instance with `source=AppA`, `target=AppB`, `protocol=REST`
- Then: the instance is saved with all fields (own properties coexist with source/target)

**AC5**:

- Given: `isConnection=false` (standard EntityType `ApplicationComponent`)
- When: Kurt creates an `ApplicationComponent` instance
- Then: `source` and `target` are not mandatory fields; no change to existing behavior

**AC6**:

- Given: a YAML import file (REQ-033) defines `DataFlow` with `isConnection=true`
- When: Kurt imports the file
- Then: `DataFlow` is imported correctly as a connection type; `isConnection=true` is visible in the GUI

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: create connection type; instance without source/target (AC1); T-relationship (AC2); allowedTypes violation (AC3); properties + source/target (AC4); normal type unchanged (AC5); YAML import (AC6)
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-032 (GUI EntityType form; extended by `isConnection` toggle and `allowedTypes` fields); REQ-033 (import; YAML schema extended by `isConnection`)
- **Downstream effects**: Future UC for entity creation (UC-05 or similar) must enforce source/target fields for connection types; the view layer (graph/table) must visualize connection instances as edges
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without connection types, relationships must be modeled as property fields (e.g., `sourceId: string`) – no referential integrity, no graph traversal possible; severe for EA modeling
- Risk 2: Without the T-relationship (connection → connection), network topologies and data flow branches cannot be modeled; medium severity

## Trade-offs

- Circularity check (connection A → connection B → connection A) is not implemented in MVP – permitted, since EA models avoid circular references from a business standpoint; the view layer must be able to handle cycles (cycle detection when rendering)
- `allowedSourceTypes` / `allowedTargetTypes` empty = everything allowed: maximum flexibility, but no protection against business-wise meaningless connections; constraint rules (UC-04 A3) can compensate for this

## Implementation Notes

- DB schema: connection instances receive two additional foreign key columns `source_entity_id` and `target_entity_id` in the entity table; both nullable at DB level, but enforced by application validation for connection types
- `source_entity_id` and `target_entity_id` point to the same entity table (self-join enables T-relationships)
- GUI form: with `isConnection=true` → mandatory dropdowns for `source` and `target` (search/autocomplete across all entities of the instance, filtered by `allowedTypes`)
- Graph visualization: connection instances displayed as edges with an arrow; for a T-relationship: edge meets another edge (T-junction rendering in the graph layout)

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from requirement: "Connections between 2 components can be captured as objects … connected with another connection, creating a T-relationship." The `isConnection` flag is the key mechanism: it turns an EntityType from a node into a directed edge of the EA graph without giving up the homogeneity of the entity table.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |

# US-135: Place a Component Hierarchically Under Another in the Browser

**ID**: US-135
**Story Points**: 5
**Status**: done
**Sprint**: S16
**Implementation**: OpenSpec change `us-135-component-hierarchy` – in the browser of the sidebar SCR-055 a component of "Current state" is placed under another one by drag and drop or "Move to…" (`PUT /entities/{id}/parent`, `entity:write`, `If-Match`); it writes a new version of the child with the field "Parent" and the audit event `entity.parent-changed`, refuses cycles and more than 10 levels, and creates the connection of the metatype marked as composition (`isComposition`, starter package 1.3.0) with `autoCreated=true`, which detaching or moving removes. Owner decisions 2026-11-18: the change applies directly in the catalog; a Solution Architect holds `entity:write`

## Story

As a Solution Architect, I want to place a component under another component in the browser via drag-and-drop or via the properties, so that the hierarchical composition of systems is visible in the model and the composition relationship is created automatically.

## Reference

**Use Case**: [UC-13: Manage Navigation Tree](../use-cases/UC-13-manage-navigation-tree.md)
**Requirements**: REQ-141, REQ-142
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**ADR**: ADR-021

## Acceptance Criteria

**AC1**:

- Given: component B (instantiated, `isStructuralGrouper=false`) and component A
- When: B is placed under A via drag-and-drop
- Then: B appears as a child node of A in the browser; automatic composition connection (autoCreated=true) appears under "Connections"

**AC2**:

- Given: structuring aid (folder) C and component D
- When: D is placed under C
- Then: D appears as a child node of C; NO connection is created

**AC3**:

- Given: component B is a child of A (with autoCreated connection)
- When: B is detached from the hierarchy (via drag to root level)
- Then: B appears at root level again; autoCreated connection disappears

## Technical Notes

- `parentEntityId` set/deleted on entity B
- Drag-and-drop as primary interaction; parent assignment via properties as an alternative
- No recursion: A cannot be placed under one of its children (cycle validation)

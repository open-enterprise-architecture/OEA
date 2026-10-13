# US-136: Create a Structuring Aid as a Folder Without an Implicit Connection

**ID**: US-136
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an Enterprise Architect, I want to create a structuring aid (folder) in the browser under which I can place components without ArchiMate connections being created automatically — so that I can organize the landscape purely for navigation without creating semantic noise in the model.

## Reference

**Use Case**: [UC-13: Manage Navigation Tree](../use-cases/UC-13-manage-navigation-tree.md)
**Requirements**: REQ-141, REQ-142
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**ADR**: ADR-021

## Acceptance Criteria

**AC1**:

- Given: user selects "New structuring aid" in the browser context
- When: name entered and confirmed
- Then: new node with folder icon (no ArchiMate icon) appears under "Components"; `isStructuralGrouper=true` set

**AC2**:

- Given: structuring aid S and instantiated component K
- When: K is placed under S
- Then: K appears as a child of S; NO new connection appears under "Connections"

**AC3**:

- Given: structuring aid S with 3 child components
- When: S is deleted
- Then: the 3 child components are moved to root level (no cascade delete); warning appears before deletion

## Technical Notes

- Structuring aid = ArchitectureEntity with `isStructuralGrouper=true`; has its own (non-ArchiMate) EntityType
- Folder icon in the browser clearly distinguishable from ArchiMate entity icons
- Structuring aids are not a TOGAF or ArchiMate concept — only a UI aid

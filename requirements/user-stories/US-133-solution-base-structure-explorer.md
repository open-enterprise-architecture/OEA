# US-133: Expand a Solution's Base Structure in the Browser

**ID**: US-133
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an Enterprise Architect, I want to expand a solution in the browser and immediately see the six base categories, so that I can navigate directly to components, connections, catalogs, FBBs, SBBs, or diagrams without first having to create folders.

## Reference

**Use Case**: [UC-13: Manage Navigation Tree](../use-cases/UC-13-manage-navigation-tree.md)
**Requirement**: REQ-139
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**ADR**: ADR-020

## Acceptance Criteria

**AC1**:
- Given: any solution in the browser
- When: solution node is expanded
- Then: exactly six child nodes appear: Components, Connections, Catalogs, Functional Building Blocks, Solution Building Blocks, Diagrams

**AC2**:
- Given: solution with 0 catalogs and 4 components
- When: solution expanded
- Then: "Components (4)" and "Catalogs (0)" (grayed out) visible; all six categories present

**AC3**:
- Given: "Functional Building Blocks" node expanded
- When: FBBs of the solution loaded
- Then: ABB objects of this solution listed with a small ArchiMate icon and name

## Technical Notes

- FBB = ArchitectureBuildingBlock (ABB) in the solution context; no new BO
- Empty categories: grayed out, not hidden (consistency for muscle memory)

# US-132: Solution Browser as Default Tab in the Browser Panel

**ID**: US-132
**Story Points**: 5
**Status**: done
**Sprint**: S16
**Implementation**: OpenSpec change `us-132-solution-browser` – the sidebar SCR-055 has the tabs "Browser" (default) and "Navigation tree"; the browser shows "Current state", the plateaus of the transition and target stage and the group "Projects" with their active solutions (`GET /views/browser`, `GET /views/browser/items`). Owner decisions 2026-11-18: project solutions appear under "Projects" instead of only flowing into "Current state" (AC2); the web portal is the client for now

## Story

As a Solution Architect, I want to see all solutions in a hierarchical overview immediately when opening the browser panel, so that I can navigate to the right working context without intermediate steps.

## Reference

**Use Case**: [UC-13: Manage Navigation Tree](../use-cases/UC-13-manage-navigation-tree.md)
**Requirement**: REQ-138
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**ADR**: ADR-020

## Acceptance Criteria

**AC1**:

- Given: user opens the native client and is logged in
- When: browser panel is loaded
- Then: tab "Browser" is active and shows "Current State" as the first node, followed by plateau groups with their solutions

**AC2**:

- Given: instance has solutions in plateau mode (AS-IS→TO-BE) and project mode
- When: browser panel is displayed
- Then: plateau solutions appear under their plateau node; project solutions do not appear as separate nodes (only in "Current State")

**AC3**:

- Given: solution with `status=archived`
- When: browser panel is displayed
- Then: archived solution is not visible

## Technical Notes

- "Current State" is a computed, read-only node — not a persisted object
- Plateaus are determined from `fromPlateauId`/`toPlateauId` of the solutions
- Tab "Architecture Tree" (UC-13 TreeNode) is retained as the second tab

# US-134: Current State Node Shows Aggregated Architecture

**ID**: US-134
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want a "Current State" node in the browser panel that aggregates all realized content from implemented solutions — regardless of whether they arose via plateau mode or project mode — so that I can navigate the overall state of the architecture at a glance.

## Reference

**Use Case**: [UC-11: Define Plateau and Go-Live](../use-cases/UC-11-define-plateau-and-go-live.md)
**Requirement**: REQ-140
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**ADR**: ADR-020

## Acceptance Criteria

**AC1**:

- Given: solution A (project mode, `status=implemented`, 3 components) and solution B (plateau mode, `status=implemented`, 5 components)
- When: "Current State → Components" expanded
- Then: 8 components visible (from both solutions combined)

**AC2**:

- Given: solution C (project mode, `status=in-progress`)
- When: "Current State" is browsed
- Then: contents of solution C not visible (not yet implemented)

**AC3**:

- Given: the same component appears in two solutions (reference entity)
- When: "Current State → Components" displayed
- Then: component appears only once (deduplication by entity ID)

## Technical Notes

- "Current State" is a read-only, computed node — not a persisted TreeNode
- Aggregation: all entities from solutions with `status=implemented`
- Deduplication at entity ID level (the same entity in multiple solutions appears once)

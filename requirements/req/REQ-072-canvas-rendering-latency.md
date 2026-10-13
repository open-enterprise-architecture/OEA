---
id: REQ-072
title: Canvas rendering latency (load and render diagram)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - entity
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-007
supersedes: []
superseded_by: []
---

# REQ-072: Canvas rendering latency (load and render diagram)

## Statement

The system MUST display an architecture diagram — from opening until the fully interactive display of all shapes and connections on the canvas — within a defined time budget.

## Rationale

The diagram canvas (React Flow, ADR-007) is the primary working tool for EA modelers (SH-03, SH-04). A diagram with 150+ elements (realistic for landscape maps (Bebauungspläne) and system context diagrams in mid-sized companies) must load fast enough that the modeling flow is not interrupted. Rendering that takes too long increases frustration and leads to flat, incomplete diagrams.

## Context

The measured time is from API response to the fully rendered, interactive canvas (time-to-interactive, TTI). Includes: data retrieval, React Flow render, SVG layout. Does not include: initial app loading (separate NFR) or Mermaid/PlantUML rendering in the Arc42 editor.

## Type-Specific Fields

### For type=non-functional

**Category**: performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Time-to-interactive, small diagram | < 500ms | 800ms | 1,500ms | 50 entities + 30 connections |
| Time-to-interactive, medium diagram | < 1,500ms | 2,500ms | 4,000ms | 150 entities + 100 connections |
| Time-to-interactive, large diagram | < 3,000ms | 5,000ms | 8,000ms | 400 entities + 300 connections |
| Frame rate when moving shapes | ≥ 30 fps | < 30 fps | < 15 fps | medium diagram; modern desktop browser |

## Acceptance Criteria

**AC1** (Small diagram):
- Given: Diagram with 50 entities + 30 connections; Chrome on standard developer hardware
- When: Diagram opened
- Then: Canvas interactive in < 500ms (measured from complete API response)

**AC2** (Medium diagram):
- Given: 150 entities + 100 connections
- When: Diagram opened
- Then: Time-to-interactive < 1,500ms

**AC3** (Large diagram):
- Given: 400 entities + 300 connections
- When: Diagram opened
- Then: Time-to-interactive < 3,000ms; no browser freezes > 100ms

**AC4** (Smooth pan/zoom):
- Given: medium diagram (150+100)
- When: User drags the canvas or zooms with the mouse wheel
- Then: Frame rate ≥ 30 fps (no visible stuttering)

## Verification Method

- [x] Method: test (automated, performance test in the browser)
- [x] Test setup: synthetic diagrams with defined entity/connection counts; Chrome DevTools performance profile or Playwright measurements
- [x] Measuring tool: Playwright + Chrome performance API (`performance.measure`)
- [x] Pass criterion: time-to-interactive values according to table; FPS via `requestAnimationFrame` loop
- [ ] Integrated in CI: yes, as a performance gate (Playwright headless)

## Dependencies

- **Prerequisites**: ADR-007 (React Flow, accepted); REQ-040 (EntityDeltas → canvas)
- **Downstream effects**: large diagrams may require virtualization (React Flow virtualization / windowing)

## Risks of Non-Fulfillment

- Modelers fragment large landscape maps (Bebauungspläne) into many small diagrams to avoid latency; coherence is lost

## Trade-offs

- React Flow virtualization (render only visible nodes): drastic latency improvement for large diagrams, but layout algorithms become more complex
- Initial auto-layout (e.g., ELK.js): increases TTI by 200–500ms, but saves manual positioning

## Implementation Notes

- Activate React Flow `nodesDraggable` and `elementsSelectable` only after the first render (reduces blocking JS)
- For diagrams > 300 nodes: enable `<ReactFlow>` with `onlyRenderVisibleElements`

## Implementation

- ADR(s): ADR-007
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

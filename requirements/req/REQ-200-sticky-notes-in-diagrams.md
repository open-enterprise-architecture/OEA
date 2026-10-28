---
id: REQ-200
title: Sticky notes in diagrams
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-10-28
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-24
  business_objects:
    - diagram
    - diagram-note
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
    - SH-01
    - SH-08
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-047
supersedes: []
superseded_by: []
---

# REQ-200: Sticky notes in diagrams

## Statement

The system SHOULD let a participant who may edit a diagram place, edit, colour, link visually and delete sticky notes on the canvas; the notes SHOULD be part of the diagram, visible to everyone who may read it and versioned with it, without creating or changing entities or connections.

## Rationale

Workshops record assumptions and open points where they apply; without notes in the diagram they end up on separate whiteboards and get lost (UC-24 step 7; owner decision of 2026-10-28).

## Context

A note is a [DiagramNote](../../business-objects/diagram-note.md) with text (Markdown subset, at most 4000 characters), colour and optional visual links to shapes. Notes are co-edited like other shapes (REQ-199) and appear in exports unless hidden.

## Type-Specific Fields

### For type=functional

**Trigger**: A participant adds, edits, recolours, links, hides in export or deletes a note

**Input**: text, colour, position, size, linked shapes

**Output / Behavior**: the note on the canvas for all participants; a new version of the diagram

**Business rules**: BR-DN-01 to BR-DN-03; creating, changing and deleting a note is recorded in the audit trail

## Acceptance Criteria

**AC1** (Create):

- Given: Kurt may edit the diagram
- When: he places a sticky note with the text "Assumption: event bus available in 2027"
- Then: the note appears for all participants, and the diagram history shows Kurt as author

**AC2** (No model change):

- Given: a note is linked visually to an interface
- When: the repository is queried
- Then: no entity or connection was created or changed

**AC3** (Read-only participant):

- Given: Anna may only read the diagram
- When: she opens the diagram
- Then: she sees the notes but cannot create, move or edit them

**AC4** (Export):

- Given: a diagram has one note with hiddenInExport and one without
- When: the diagram is exported
- Then: the export contains only the note without hiddenInExport

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: two or more browser sessions of different persons against the same instance (Playwright with several browser contexts)
- [x] Measuring tool: Playwright, backend integration tests
- [x] Pass criterion: all acceptance criteria green
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: BO diagram, diagram-note; REQ-199
- **Downstream effects**: diagram export formats

## Risks of Non-Fulfillment

- Workshop results stay outside the tool (medium)

## Trade-offs

- Notes are not queryable architecture content; findings that matter long-term belong into entities (e.g. constraints, requirements)

## Implementation

- ADR(s): ADR-047
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Owner request and decisions of 2026-10-28 (UC-24): priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; sticky notes and comment threads in diagrams.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (UC-24) |

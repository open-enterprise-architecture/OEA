---
id: REQ-069
title: Edit and display Arc42 documentation for an entity
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - arc42
    - entity
  stakeholders:
    - SH-04
    - SH-03
    - SH-05
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-069: Edit and display Arc42 documentation for an entity

## Statement

For every ArchitectureEntity whose EntityType is assigned to at least one Arc42ChapterCollection, the system MUST display an "Arc42 Documentation" tab. This tab shows all configured questions of the assigned collections and enables creating, editing and deleting Arc42MetaObject answer entities (REQ-068). Creating an answer MUST automatically generate an `arc42-describes` connection between the answer entity and the subject entity. The system MUST display a progress indicator (answered vs. open questions).

## Rationale

Without the tab as an entry point, the solution architect lacks context: they would have to search for Arc42 answers as free-standing entities instead of seeing them in the context of the system. The tab makes Arc42 documentation a first-class feature in the system detail view.

## Context

The implementation follows the pattern: "detail view of an ArchitectureEntity" + "Arc42 tab" as an additional tab alongside Properties, Connections, Diagrams. The questions come from the collections (MetamodelConfiguration), the answers are entities in the repository.

## Type-Specific Fields

### Tab Structure

```
[Basic data] [Properties] [Connections] [Diagrams] [Arc42 Documentation]
                                                    ↑ only if collection assigned

Arc42 Documentation
├── [Collection A: Standard Arc42 (4/12 answered)]    ← progress chip
│   ├── 1. Context boundary  ✓  [Edit]
│   ├── 2. Quality goals     ✗  [Answer now]
│   ├── ...
└── [Collection B: Security Review (0/5)]
    ├── ...
```

### Create / Edit Answer

- `POST /api/v1/entities` with entityTypeId=`arc42-meta-object` (or subtype), `questionRef`, `name`, `content`
- At the same time: `POST /api/v1/entities` with entityTypeId=`arc42-describes`, sourceEntityId=new-answer-id, targetEntityId=subject-id
- Both in one transaction

### Delete Answer

- `DELETE /api/v1/entities/{arc42AnswerId}`
- Cascades: the arc42-describes connection is deleted along with it
- The question appears again as "unanswered"

### API for Tab Data

`GET /api/v1/entities/{id}/arc42` returns:

```json
{
  "collections": [
    {
      "collectionId": "arc42-standard",
      "name": "Standard Arc42",
      "progress": { "answered": 4, "total": 12 },
      "questions": [
        {
          "questionId": "context-goals",
          "title": "1. Context boundary",
          "answered": true,
          "answerId": 201,
          "answerName": "Context boundary CRM system"
        },
        {
          "questionId": "constraints",
          "title": "2. Constraints",
          "answered": false,
          "answerId": null
        }
      ]
    }
  ]
}
```

## Acceptance Criteria

**AC1** (Tab visible with collection assignment):
- Given: EntityType `application-component` has collection `arc42-standard` assigned
- When: Michael opens the CRM-System entity (id=1)
- Then: "Arc42 Documentation" tab visible; 12 questions listed; all initially unanswered

**AC2** (Tab missing without assignment):
- Given: EntityType `technology-component` has no collection
- When: User opens a technology component entity
- Then: No Arc42 tab; no error

**AC3** (Create answer):
- When: Michael clicks "Answer now" on question 1, writes content, saves
- Then: arc42-meta-object entity created (id=201); arc42-describes(source=201, target=1) created; question 1 shows "✓"

**AC4** (Edit answer):
- When: Michael clicks "Edit" on question 1 (id=201)
- Then: WYSIWYG editor opens with the current content; after saving, entity 201 is updated

**AC5** (Progress indicator):
- When: 4 of 12 questions answered
- Then: Chip shows "4 / 12"; open questions visually distinguished (color or icon)

**AC6** (Multiple collections):
- Given: EntityType has 2 collections
- When: Arc42 tab opened
- Then: Both collections as separate sections (or tabs); progress per collection separate

**AC7** (Web portal read-only):
- When: CIO opens an entity in the web portal
- Then: Arc42 tab visible; contents with rendered diagrams; no "Answer" button

**AC8** (Delete answer):
- When: Michael clicks "Delete" on an answered question
- Then: arc42-meta-object entity (201) and arc42-describes connection deleted; question "unanswered" again

## Dependencies

- **Prerequisites**: REQ-067 (collections), REQ-068 (editor)
- **Downstream effects**: UC-06 catalog can list arc42-meta-object entities (documentation overview)

## Implementation Notes

- `GET /api/v1/entities/{id}/arc42`: aggregated endpoint; traverses arc42-describes connections inversely
- Arc42 tab only appears if `GET .../arc42` returns at least one collection

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

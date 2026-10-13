---
id: REQ-067
title: Configure Arc42ChapterCollection in the metamodel
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-09
  business_objects:
    - arc42
    - metamodel-configuration
  stakeholders:
    - SH-07
    - SH-03
  concept:
    - concept/40-extensibility/14-extensibility.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-067: Configure Arc42ChapterCollection in the metamodel

## Statement

The system MUST enable the metamodel administrator to create, edit and delete `Arc42ChapterCollection` objects in the MetamodelConfiguration. A collection consists of a `name`, an ordered list of `Arc42QuestionTemplate` entries and a list of `assignedEntityTypeIds`. The system MUST also ship a predefined built-in collection `arc42-standard` with the 12 classic Arc42 chapters, which can be imported as a starting point.

## Rationale

Without configurable collections, Arc42 would be a rigid format that does not fit the respective context. An SME needs 5 questions, a corporate group 15. The collection level allows arbitrary configuration per instance — maximum degree of freedom in line with the OEA core principle.

## Acceptance Criteria

**AC1** (Create collection):
- When: Admin posts `{ id: "arc42-sme", name: "Arc42 SME", questions: [...], assignedEntityTypeIds: ["application-component"] }`
- Then: HTTP 201; collection stored in MetamodelConfiguration; EntityType `application-component` shows the collection when opened

**AC2** (Add question):
- When: Admin adds `{ questionId: "context", title: "Context", questionText: "...", sortOrder: 1 }` to a collection
- Then: Question appears at position 1 in the collection

**AC3** (Import built-in standard collection):
- When: Admin clicks "Import standard Arc42"
- Then: Collection `arc42-standard` with 12 questions is created as a user-defined copy (editable); original remains immutable

**AC4** (Multiple assigned EntityTypes):
- When: `assignedEntityTypeIds: ["application-component", "technology-component"]`
- Then: Both EntityTypes show the collection in the Arc42 tab

**AC5** (Delete collection):
- When: Admin deletes a collection that already has answer entities
- Then: HTTP 422; message: "Collection has {N} answers — first remove answers or mark the collection as inactive"; no data loss

## Dependencies

- **Prerequisites**: REQ-036 (EntityTypeDefinition), REQ-033 (import — collections must come along with the MetamodelConfiguration import)
- **Downstream effects**: REQ-068 (WYSIWYG editor), REQ-069 (display and editing)

## Implementation Notes

- Arc42ChapterCollection is stored as part of the MetamodelConfiguration (new top-level array `arc42Collections: Arc42ChapterCollection[]`)
- Built-in `arc42-standard` is registered automatically at system start; it is not in the DB but in the code
- Import/export of collections via REQ-033/REQ-058 (MetamodelConfiguration import/export also covers arc42Collections)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

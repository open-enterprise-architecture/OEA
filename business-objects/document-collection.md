---
id: document-collection
title: DocumentCollection – Structured Documentation
version: 0.3.0
status: draft
created: 2026-06-26
last_updated: 2026-06-27
author: business-engineer
references:
  concept:
    - concept/20-entities/14-extensibility.md
  stakeholders:
    - SH-03
    - SH-04
  use_cases:
    - UC-04
    - UC-09
---

# DocumentCollection – Structured Documentation

## Purpose

OEA makes it possible to maintain structured text documentation directly on entities — linked to the EA model, not in a separate tool. The concept is fully generic: operators define their own documentation types (e.g. Arc42 architecture, third-party management, license register) as a `DocumentCollectionDefinition` with arbitrary chapters.

An entity can be assigned to multiple documentation types (1 entity → n DocumentCollections). The content of each chapter is stored as its own `DocumentationEntry` entity and linked to the documented entity via a `documents` connection.

**Two-level model:**
1. **Definition level** (metamodel): Which documentation types exist? Which chapters do they have? (`DocumentCollectionDefinition` in MetamodelConfiguration)
2. **Instance level**: What was documented for this entity? (`DocumentationEntry` entities per system, linked via `documents`)

## Core Concepts

### DocumentItem (atomic unit of a DocumentCollection)

`DocumentItem` is the atomic content unit of a `DocumentCollection`. It replaces and extends the former `DocumentationEntry`. Each DocumentItem corresponds to a chapter and carries both structural metadata (name, alias, position) and the actual content (WYSIWYG).

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4; globally unique |
| collectionId | string | required | | Reference to the associated `DocumentCollection` instance |
| name | string | required | | Chapter name; displayed in navigation and outline (max. 200 characters) |
| alias | string | optional | null | Short name for cross-references; must be unique within the collection (kebab-case; max. 60 characters); used as the primary search term in `{{` autocomplete |
| content | rich-text | required | `""` | WYSIWYG content; supports Markdown, Mermaid, PlantUML, Draw.io, entity mentions (`[[`) and DocumentItem cross-references (`{{`) |
| parentId | string | optional | null | Reference to the parent DocumentItem within the same collection; `null` = root chapter |
| sortOrder | integer | required | | Order among sibling items (shared scope: all children of the same `parentId`); ascending |
| chapterRef | string | optional | null | Optional link to `DocumentChapterDefinition.chapterId` of the originating template |
| createdAt | datetime | required | | ISO 8601, UTC |
| createdBy | reference | required | | target: person |
| lastModifiedAt | datetime | required | | ISO 8601, UTC |
| lastModifiedBy | reference | required | | target: person |

**Nesting:** DocumentItems form a tree within a collection. The depth is limited to **5 levels** (level 0 = root). Sibling items share the same `parentId` and are ordered via `sortOrder`. Cycles are forbidden (BR-11).

**Subtypes:** Via `extends: documentation-entry` at the EntityTypeDefinition level, subtypes with additional properties can be defined (e.g. `licenseType`, `expiryDate` for license register chapters). Subtypes inherit all DocumentItem attributes.

### DocumentationEntry (built-in EntityType — deprecated)

`documentation-entry` is retained as the base EntityType for subtype definitions but is replaced by `DocumentItem` at the instance level. Existing `DocumentationEntry` instances are converted to `DocumentItem` entries by migration.

### Documents (built-in connection type)

`documents` is a built-in connection type with `isConnection=true`. It links a `DocumentationEntry` instance to the documented entity (the "subject").

| Attribute | Type | Description |
|---|---|---|
| sourceEntityId | integer | DocumentationEntry instance (type: `documentation-entry` or subtype) |
| targetEntityId | integer | Documented object (any EntityType, e.g. `application-component`) |

Through this connection, the system can retrieve all associated documentation entries for any entity (`GET /api/v1/entities/{id}/documentation`) and, conversely, find the documented object for any entry.

### DocumentCollectionDefinition (metamodel object)

A `DocumentCollectionDefinition` is an operator-defined documentation type with an ordered list of chapter templates. It is stored in the MetamodelConfiguration and can be assigned to arbitrary EntityTypes.

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | kebab-case; globally unique in the MetamodelConfiguration |
| name | string | required | | Display name (e.g. "Arc42 Architecture", "Third-Party Management", "License Register") |
| description | string | optional | null | Description of the collection's purpose |
| assignedEntityTypeIds | string[] | required | [] | EntityTypeDefinition IDs for which this collection applies; empty = available for all EntityTypes |
| chapters | DocumentChapterDefinition[] | required | [] | Ordered list of chapter templates |

### DocumentChapterDefinition (sub-object of DocumentCollectionDefinition)

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| chapterId | string | required | | kebab-case; unique within the collection |
| title | string | required | | Chapter name (e.g. "System Context", "Vendor Assessment", "License Type") |
| prompt | string | optional | null | Completion hint displayed below the chapter |
| sortOrder | integer | required | | Order within the collection; ascending |
| entryEntityTypeId | string | optional | `documentation-entry` | Which subtype is used for the entry; default = base type |

## Predefined Documentation Types (Templates)

OEA ships predefined `DocumentCollectionDefinitions` as an optional template set. Operators can adopt, adapt, or ignore them:

### Template: Arc42 Architecture Documentation (`arc42-standard`)

| # | chapterId | title | Arc42 Reference |
|---|---|---|---|
| 1 | context-goals | Introduction and Goals | §1 |
| 2 | constraints | Constraints | §2 |
| 3 | system-context | Context and Scope | §3 |
| 4 | solution-strategy | Solution Strategy | §4 |
| 5 | building-blocks | Building Block View | §5 |
| 6 | runtime-view | Runtime View | §6 |
| 7 | deployment-view | Deployment View | §7 |
| 8 | crosscutting-concepts | Cross-cutting Concepts | §8 |
| 9 | architecture-decisions | Architecture Decisions | §9 (refers to ADRs) |
| 10 | quality-requirements | Quality Requirements | §10 (refers to NFRs/REQs) |
| 11 | risks | Risks and Technical Debt | §11 |
| 12 | glossary | Glossary | §12 |

### Template: Third-Party Management (`third-party-management`)

| # | chapterId | title |
|---|---|---|
| 1 | vendor-profile | Vendor Profile and Assessment |
| 2 | sla-conditions | SLA Conditions |
| 3 | exit-strategy | Exit Strategy |
| 4 | contact-persons | Contact Persons |

### Template: License Register (`license-register`)

| # | chapterId | title |
|---|---|---|
| 1 | license-type | License Type and Model |
| 2 | license-scope | Scope of Use and Restrictions |
| 3 | costs-renewal | Costs and Renewal Deadlines |
| 4 | compliance-notes | Compliance Notes |

**Note on Arc42 §9 and §10**: In OEA, these chapters can refer to existing artifacts — ADRs and REQs are already in the tool; the WYSIWYG content can set links to these artifacts instead of rewriting everything.

## WYSIWYG Editor: Supported Formats

The `content` value of a `DocumentationEntry` entity supports:

| Format | Syntax | Rendering |
|---|---|---|
| Markdown | Standard CommonMark | Inline rendering |
| Mermaid | ` ```mermaid ` … ` ``` ` | Client-side rendering via mermaid.js |
| PlantUML | ` ```plantuml ` … ` ``` ` | Server-side rendering via PlantUML server or WASM |
| Draw.io | ` ```drawio ` … ` ``` ` | SVG rendering via draw.io viewer library; editing in the draw.io editor popup |
| Entity mention | `[[` → autocomplete → `[[Name\|entity:ID]]` | Inline badge with link to the entity (in code blocks: plain text name) |
| DocumentItem cross-reference | `{{` → autocomplete → `{{Name\|item:ID}}` | Inline link to the referenced chapter; shows the `name` of the target item (in code blocks: plain text name) |

### Entity Mention via `[[`

Anywhere in the editor — in free text, within Mermaid, PlantUML, and Draw.io code blocks — typing `[[` triggers an autocomplete dropdown that searches live against the entity repository.

**Storage format by context:**

| Context | Input | Stored Value |
|---|---|---|
| Markdown free text | `[[CRM` → selection | `[[CRM-System\|entity:1]]` |
| Mermaid code block | `[[CRM` → selection | `CRM-System` (plain text) |
| PlantUML code block | `[[CRM` → selection | `CRM-System` (plain text) |
| Draw.io shape label | `[[CRM` → selection | `CRM-System` (plain text) |

In **Markdown free text**, `[[Name|entity:ID]]` is stored. On rendering, the system resolves the current name via ID — if the entity is renamed, the badge automatically shows the new name. In **code blocks**, the name is inserted as plain text; renaming in the repo is not automatically propagated.

### DocumentItem Cross-Reference via `{{`

Typing `{{` in the free-text area of the WYSIWYG editor opens an autocomplete dropdown for DocumentItems within the same `DocumentCollection`. Search is done via both `alias` and `name`.

**Storage format:**

| Context | Input | Stored Value | Rendering |
|---|---|---|---|
| Markdown free text | `{{conte` → select "Context and Scope" | `{{Context and Scope\|item:uuid}}` | Inline link; shows the current `name` of the target item |
| Code blocks | `{{conte` → selection | `Context and Scope` (plain text) | No link; plain text name |

**Resolution:** On rendering, the system resolves the current `name` of the referenced DocumentItem via ID. If the chapter is renamed, the reference automatically shows the new name. If the chapter is deleted, the reference shows `[deleted chapter|item:uuid]` in red text (analogous to entity mention).

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| documents | ArchitectureEntity (any) | n:1 | no | `documents` connection; each entry documents exactly one subject |
| answersChapter | DocumentChapterDefinition | n:1 | yes | via `chapterRef`; assigns the entry to a chapter |
| belongsToCollection | DocumentCollectionDefinition | n:1 | yes | via `collectionRef` → collection |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `documentation-entry` entities MUST be linked to exactly one subject via a `documents` connection | onCreate | – |
| BR-02 | `documents` MUST NOT be created between two `documentation-entry` instances (`sourceEntityId` must not be a `documentation-entry`) | onCreate | – |
| BR-03 | `DocumentCollectionDefinition.chapters[].sortOrder` must be unique within the collection | onCreate, onUpdate | – |
| BR-04 | If a `DocumentCollectionDefinition` is assigned to an EntityType that already carries another collection, both are valid (additive assignment); an EntityType can carry n collections | onCreate | – |
| BR-05 | At most one `DocumentationEntry` may exist per (subject entity + `chapterRef` + `collectionRef`); saving again overwrites the existing entry | onCreate, onUpdate | – |
| BR-06 | `DocumentChapterDefinition.entryEntityTypeId` must be an EntityType that has `documentation-entry` as a direct or transitive parent type (`extends` chain) | onCreate, onUpdate | – |
| BR-07 | The `content` value may be at most 100,000 characters; code blocks are not rendered before saving | onRead | – |
| BR-08 | Entity mentions in Markdown free text are stored in the format `[[Name\|entity:ID]]`; on rendering, the system MUST resolve the current `name` value via ID — for a deleted entity: `[deleted|entity:ID]` in red text | onRead | – |
| BR-09 | Entity mentions within code blocks (Mermaid, PlantUML, Draw.io) are stored as plain text names; no ID resolution on rendering | onCreate, onUpdate | – |
| BR-10 | Draw.io code blocks (` ```drawio `) contain valid `<mxGraphModel>` XML; an invalid XML structure is flagged as an error before saving; the XML MUST be sanitized server-side or via DOMParser (no `<script>` tags, no `javascript:` URIs in `href` attributes) | onSave, onRead | – |
| BR-11 | `DocumentItem.parentId` MUST NOT lead to a cycle; the cycle check occurs when setting and changing `parentId` | onCreate, onUpdate | – |
| BR-12 | The nesting depth of a DocumentItem (distance to the root) MUST NOT exceed 5; level 0 = `parentId=null` | onCreate, onUpdate | – |
| BR-13 | `DocumentItem.alias` must be unique within the same `DocumentCollection` (case-insensitive); spaces and special characters are not allowed (kebab-case validation) | onCreate, onUpdate | – |
| BR-14 | DocumentItem cross-references (`{{Name\|item:uuid}}`) MUST be resolved via ID on rendering; for a deleted target, shows `[deleted chapter\|item:uuid]` in red text | onRead | – |
| BR-15 | A DocumentItem cross-reference MUST NOT point to a DocumentItem of a different `DocumentCollection`; collection boundaries are scope boundaries for `{{` | onCreate, onUpdate | – |

## Usage

- **Metamodel admin (UC-04)**: creates `DocumentCollectionDefinitions` (e.g. Arc42, third-party, license register), configures chapters, assigns EntityTypes
- **Solution architect / Lead EA (UC-09)**: opens an entity (e.g. CRM-System), navigates to the documentation tab, fills in chapters via the WYSIWYG editor
- **Read-only user (Web Portal)**: reads content, sees rendered Mermaid/PlantUML diagrams

## Delimitation

- **NOT** the concept paper (`concept/`): the concept paper is project-internal documentation; `DocumentCollections` are system-specific content in the EA repository
- **NOT** a diagram (`diagram.md`): diagrams are canvas-based visual models; documentation entries are textual content with optional inline diagrams
- **NOT** a use case or REQ: chapter contents are architecture descriptions or management information, not requirements

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft as arc42.md (Arc42-specific) |
| 0.2.0 | 2026-06-27 | Business Engineer | Generalization: Arc42MetaObject → DocumentationEntry; Arc42Describes → Documents; Arc42ChapterCollection → DocumentCollectionDefinition; Arc42QuestionTemplate → DocumentChapterDefinition; three predefined templates (Arc42, third-party, license register); 1-entity-to-n-collections relationship explicitly modeled (BR-04/BR-05) |
| 0.3.0 | 2026-06-28 | Business Engineer | `DocumentItem` introduced as new atomic instance unit (replaces `DocumentationEntry`); attributes: `name`, `alias`, `content`, `parentId` (nesting up to 5 levels), `sortOrder`; `{{` cross-reference syntax in the WYSIWYG editor for cross-chapter references; BR-11–15 added; `DocumentationEntry` marked as deprecated |

---
identifier: trm-category
name_de: TRM-Kategorie
name_en: TRM Category
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - TRM Category
  - Technology Reference Model Category
  - TRM-Knoten
references:
  - concept: concept/10-foundations/04-enterprise-continuum-trm.md
  - adr: adrs/ADR-002-continuum-repository.md
---

# Business Object: TRMCategory

## Definition

A `TRMCategory` is a node in the hierarchical classification of the **Technical Reference Model (TRM)**. The TRM is a concrete example of a `foundation`-level reference architecture: a complete taxonomy of all technology service categories that an organization can use. Every `ArchitectureEntity` of a technology component type can be assigned to one or more TRM categories.

## Description

The TRM is a **classification hierarchy** — not an instance model. It does not describe which concrete products are present, but which *types* of services exist and which products (SBBs) are preferred, acceptable, deprecated, or prohibited for each service type.

TOGAF 10 provides an example TRM with three hierarchy levels:

```text
Level 1: Application Platform Services
  Level 2: Data Interchange Services
    Level 3: Data Formats & Protocols
    Level 3: Electronic Data Interchange
  Level 2: Data Management Services
    Level 3: Database Management
    Level 3: Data Warehousing
  Level 2: Security Services
    Level 3: Identity & Access Management
    Level 3: Cryptographic Services
```

Organizations adapt the TOGAF example TRM and add industry-specific categories. Imported TRM structures are immutable; own extensions can be added.

**Usage:** When an enterprise architect creates a technology entity (e.g. "PostgreSQL 16"), they assign it to TRM categories (e.g. "Database Management", "Relational DBMS"). This enables TRM-based analyses: Which categories are occupied twice? Which deviate from the standard?

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | UUID | required | | v4, globally unique | Primary key |
| name | string | required | | max. 255 characters; unique among siblings within the same `parentId` | Category name (e.g. "Identity & Access Management") |
| description | string | optional | null | max. 3000 characters; Markdown | Description of the service type and its delimitation |
| level | integer | required | | 1–5; level 1 = top level | Hierarchy depth in the TRM |
| parentId | reference | optional | null | target: TRMCategory; null = root node (level 1) | Parent category |
| evaluationCriteria | string | optional | null | max. 2000 characters; Markdown | Criteria by which SBBs are evaluated for this category (e.g. scalability, license costs, vendor support) |
| lastReviewedAt | date | optional | null | ISO 8601 | Date of the last governance review of this category |
| scope | enum | required | `organization` | `[built-in, imported, organization]` | Origin: `imported` = from TRM package (e.g. TOGAF); `organization` = own extension |
| sourcePackage | string | optional | null | max. 255 characters; only if `scope=imported` | Source package (e.g. "TOGAF 10 TRM") |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| parent | [trm-category](./trm-category.md) | n:0..1 | yes | Parent category; null = root node |
| children | [trm-category](./trm-category.md) | 1:n | yes | Child categories |
| preferredStandard | [solution-building-block](./solution-building-block.md) | n:0..1 | yes | Preferred product / SBB for this category (governanceStatus=approved) |
| acceptedAlternatives | [solution-building-block](./solution-building-block.md) | n:m | yes | Acceptable alternatives (governanceStatus=acceptable) |
| deprecatedOptions | [solution-building-block](./solution-building-block.md) | n:m | yes | Phasing-out products that may still exist in existing systems |
| classifiedEntities | [entity](./entity.md) | 1:n | yes | Technology entities assigned to this category (via `entity.trmClassificationIds`) |

## Business Rules

| Rule ID | Statement | Trigger |
|---|---|---|
| BR-01 | TRMCategories with `scope=imported` or `scope=built-in` are read-only (nodes cannot be deleted or renamed) | onUpdate, onDelete |
| BR-02 | No cycle in the `parent` graph: a category must not be (directly or indirectly) its own child | onCreate, onUpdate |
| BR-03 | `level` MUST be consistent with the `parent` chain: `level = parent.level + 1` | onCreate, onUpdate |
| BR-04 | An SBB cannot simultaneously be `preferredStandard`, `acceptedAlternatives` AND `deprecatedOptions` in the same TRMCategory — these are mutually exclusive | onCategoryUpdate |
| BR-05 | A TRMCategory with child nodes (`children` not empty) cannot be deleted; remove the children first | onDelete |

## Lifecycle

TRM categories have no formal lifecycle status. They can:

- for `imported` categories: never be deleted or renamed
- for `organization` categories: be extended or restructured at any time (if no entities are assigned)

`lastReviewedAt` signals how current the category's SBB assignments are. Categories without a review in the last 24 months should be marked as outdated.

## Analyses (Concept §4.7)

- **Technology diversity**: How many different SBBs implement the same TRM category? (5 message brokers instead of 2 → standardization potential)
- **TRM coverage**: Which TRM categories have a `preferredStandard` SBB, which do not?
- **Standard enforcement**: Entities whose `instanceOfSBBId` does NOT correspond to the `preferredStandard` of the assigned TRMCategory → deviation report
- **Lifecycle per category**: Landscape map (Bebauungsplan) filtered to a TRM category shows technology change within a service area

## Extension of ArchitectureEntity

Through this BO, technology entities receive an additional attribute (to be added to `entity.md` in the future):

| New Attribute | Type | Optional | Description |
|---|---|---|---|
| `trmClassificationIds` | UUID[] | optional | References to TRM categories; n:m; only meaningful for entities of a PhysicalTechnologyComponent type |

## Example

```text
TRM tree (excerpt):
Level 1: Infrastructure Services
  Level 2: Data Management Services
    Level 3: Database Management
      preferredStandard: SBB "PostgreSQL 17"
      acceptedAlternatives: [SBB "MySQL 8.x", SBB "Amazon Aurora PostgreSQL"]
      deprecatedOptions: [SBB "Oracle Database 11g"]

Entity "acme-db-prod":
  entityTypeId: physical-technology-component
  trmClassificationIds: [<UUID of "Database Management">]
  instanceOfSBBId: <UUID of "PostgreSQL 17">
  → Conformant: instanceOf = preferredStandard ✓
```

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Business Engineer | Initial draft; based on TOGAF TRM §4.6 (concept); hierarchy, SBB assignment, analyses |

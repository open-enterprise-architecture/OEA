---
identifier: architecture-building-block
name_de: Architektur-Baustein (ABB)
name_en: Architecture Building Block
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - ABB
  - Architecture Building Block
  - Wiederverwendbarer Baustein
references:
  - concept: concept/10-foundations/04-enterprise-continuum-trm.md
  - adr: adrs/ADR-002-continuum-repository.md
---

# Business Object: ArchitectureBuildingBlock (ABB)

## Definition

An `ArchitectureBuildingBlock` (ABB) is a reusable, technology-neutral architecture specification that describes *what* a building block must deliver and *which properties* it must have — without prescribing how it is implemented. ABBs form the **Architecture Continuum**: from universal foundation building blocks to organization-specific specifications.

## Description

ABBs sit at a higher level of abstraction than concrete `ArchitectureEntity` instances. The relationship chain is:

```
ABB „Identity Provider"          ← specification: what must it be able to do?
  └── SBB „Keycloak 23.0"        ← concrete implementation (implements ABB)
        └── Entity „acme-iam"    ← production instance (instanceOf SBB, conformsTo ABB)
```

**Continuum hierarchy** (from generic to specific):

| Level | Meaning | Example |
|---|---|---|
| `foundation` | Universal, defined on the framework side (e.g. TOGAF TRM) | "Security Service", "Data Exchange Service" |
| `common-systems` | Reusable across industries | "Identity Provider", "API Gateway" |
| `industry` | Industry-specific (e.g. banking, telecommunications) | "Core Banking System", "BSS/OSS" |
| `organization` | Organization-specific; can be promoted back into the continuum | "Acme Central IAM Service" |

Foundation and common-systems ABBs are typically imported from continuum packages (ADR-002: `scope=imported`); organization-specific ABBs are created in-house (`scope=organization`).

ABBs can refine each other: a specialized ABB `MFA-capable Identity Provider` refines the more generic `Identity Provider`. This creates a refinement tree within the Architecture Continuum.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | UUID | required | | v4, globally unique | Primary key |
| name | string | required | | max. 255 characters; unique per `continuumLevel` | Readable name (e.g. "Identity Provider") |
| description | string | optional | null | max. 5000 characters; Markdown | Purpose and business positioning |
| continuumLevel | enum | required | `organization` | `[foundation, common-systems, industry, organization]` | Placement in the Architecture Continuum |
| industry | string | optional | null | max. 100 characters; only relevant if `continuumLevel=industry` | Industry (e.g. "Banking", "Telecommunications", "TM Forum") |
| maturityLevel | enum | required | `experimental` | `[experimental, emerging, established, industry-standard]` | Maturity level of the building block |
| governanceStatus | enum | required | `proposed` | `[proposed, approved, deprecated]` | Approval status in the organization |
| specifications | string | optional | null | max. 10000 characters; Markdown | Requirements and constraints that implementations (SBBs) must satisfy |
| scope | enum | required | `organization` | `[built-in, imported, organization]` | Origin: `built-in` = OEA standard; `imported` = from continuum package; `organization` = created in-house |
| sourcePackage | string | optional | null | max. 255 characters; only if `scope=imported` | Source package (e.g. "TOGAF 10 TRM", "TM Forum Frameworx 23.0") |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| refines | [architecture-building-block](./architecture-building-block.md) | n:0..1 | yes | This ABB refines a more generic ABB (refinement hierarchy in the continuum) |
| refinedBy | [architecture-building-block](./architecture-building-block.md) | 1:n | yes | Specializations of this ABB |
| implementedBy | [solution-building-block](./solution-building-block.md) | 1:n | yes | Concrete products / components that implement this ABB |
| usedIn | [reference-architecture](./reference-architecture.md) | n:m | yes | Reference architectures in which this ABB occurs |
| conformingEntities | [entity](./entity.md) | 1:n | yes | Instances explicitly declared conformant to this ABB (via `entity.conformsToABBId`) |

## Business Rules

| Rule ID | Statement | Trigger |
|---|---|---|
| BR-01 | ABBs with `scope=imported` or `scope=built-in` are read-only; editing and deletion are blocked | onUpdate, onDelete |
| BR-02 | The `industry` field MUST be set if `continuumLevel=industry` | onCreate, onUpdate |
| BR-03 | An ABB must not refine itself — no direct or indirect cycle in the `refines` graph | onCreate, onUpdate |
| BR-04 | ABBs with `governanceStatus=deprecated` are selectable but produce a warning on new use | onUse |
| BR-05 | ABBs with `continuumLevel=foundation` or `common-systems` SHOULD originate from continuum packages (`scope=imported`); manual creation produces a governance warning | onCreate |

## Lifecycle

```
proposed → approved → deprecated
```

- `imported` ABBs start directly as `approved` (validated by the package)
- `organization` ABBs go through `proposed → approved`
- `deprecated` ABBs cannot be deleted as long as entities reference them

## Analyses (Concept §4.5)

- **ABB coverage gap**: ABBs without an implementing SBB or without a conformant instance → gap in architecture delivery
- **Refinement tree**: hierarchy from `foundation` → `organization` as tree view
- **Conformance check**: entities without `conformsToABBId` even though a matching ABB exists

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Business Engineer | Initial draft; based on Enterprise Continuum §4 (concept); ADR-002 (scope-based) |

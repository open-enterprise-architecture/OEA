---
identifier: reference-architecture
name_de: Referenzarchitektur
name_en: Reference Architecture
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Referenzarchitektur
  - Blueprint
  - Reference Architecture
references:
  - concept: concept/10-foundations/04-enterprise-continuum-trm.md
  - adr: adrs/ADR-002-continuum-repository.md
---

# Business Object: ReferenceArchitecture

## Definition

A `ReferenceArchitecture` is a prefabricated, reusable architecture blueprint that assembles a group of `ArchitectureBuildingBlocks` (ABBs) and `ArchitecturePatterns` within a defined scope into a complete solution. It serves as a starting point for concrete solution architectures and reduces the effort for recurring architecture tasks.

## Description

Reference architectures are the composition level of the Architecture Continuum: they combine ABBs (specifications) and patterns (structures) into a coherent whole. A reference architecture is neither a plateau nor a solution — it is a blueprint that can be instantiated in any number of solutions.

**Examples:**

| Reference Architecture | Scope | ABBs Used |
|---|---|---|
| Cloud-Native Microservices | cross-organization | API Gateway, Service Mesh, Container Runtime, Identity Provider, Event Bus |
| Three-Tier Web App | common-systems | Presentation Layer, Business Logic Layer, Persistence Layer |
| TOGAF Application Platform | foundation | TOGAF TRM Infrastructure, Middleware, Application Services |
| Acme Banking Core Platform | organization | Core Banking System, Regulatory Reporting, Customer Identity |

A reference architecture can serve as a template for a [Plateau](./plateau.md): "instantiated as" means that a plateau describes the target state that corresponds to this reference architecture.

**Continuum placement:**

| continuumLevel | Meaning |
|---|---|
| `foundation` | Generally valid, framework-side (e.g. TOGAF Application Platform) |
| `common-systems` | Reusable across industries |
| `industry` | Industry-specific (e.g. banking, energy) |
| `organization` | Organization-specific; self-developed blueprint |

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | UUID | required | | v4, globally unique | Primary key |
| name | string | required | | max. 255 characters | Name (e.g. "Cloud-Native Microservices Platform") |
| description | string | optional | null | max. 5000 characters; Markdown | Purpose, scope, and application context |
| continuumLevel | enum | required | `organization` | `[foundation, common-systems, industry, organization]` | Placement in the Architecture Continuum |
| targetIndustry | string | optional | null | max. 100 characters; only relevant if `continuumLevel=industry` | Industry (e.g. "Banking", "Energy", "Public Sector") |
| maturityLevel | enum | required | `experimental` | `[experimental, emerging, established, industry-standard]` | Maturity level |
| governanceStatus | enum | required | `proposed` | `[proposed, approved, deprecated]` | Approval status |
| scope | enum | required | `organization` | `[built-in, imported, organization]` | Origin, analogous to ABB/SBB |
| sourcePackage | string | optional | null | max. 255 characters; only if `scope=imported` | Source package |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| composedOf | [architecture-building-block](./architecture-building-block.md) | n:m | yes | ABBs that are part of this reference architecture |
| basedOnPatterns | [architecture-pattern](./architecture-pattern.md) | n:m | yes | Patterns on which this reference architecture is structurally built |
| instantiatedAs | [plateau](./plateau.md) | 1:n | yes | Plateaus that use this reference architecture as a blueprint |

## Business Rules

| Rule ID | Statement | Trigger |
|---|---|---|
| BR-01 | Reference architectures with `scope=imported` or `scope=built-in` are read-only | onUpdate, onDelete |
| BR-02 | `targetIndustry` MUST be set if `continuumLevel=industry` | onCreate, onUpdate |
| BR-03 | A reference architecture SHOULD reference at least one ABB or one pattern; empty blueprints produce a warning | onCreate |
| BR-04 | Reference architectures with `governanceStatus=deprecated` are no longer selectable as a template for new plateaus | onPlateau |

## Lifecycle

```
proposed → approved → deprecated
```

Imported reference architectures start as `approved`.
Organization-specific ones go through `proposed → approved`.
Deprecated reference architectures are retained for analysis and traceability.

## Analyses

- **Blueprint adoption**: How many plateaus are based on this reference architecture? Low adoption → possibly poorly communicated or not fitting
- **ABB gaps**: Which ABBs in a reference architecture have no SBB or no instance in the organization?
- **Deviations**: If a plateau references this reference architecture but leaves certain ABBs unfilled — explicit deviation documentation

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Business Engineer | Initial draft; based on Enterprise Continuum §4 (concept) |

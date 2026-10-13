---
identifier: solution-building-block
name_de: Lösungs-Baustein (SBB)
name_en: Solution Building Block
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - SBB
  - Solution Building Block
  - Produkt-Baustein
references:
  - concept: concept/10-foundations/04-enterprise-continuum-trm.md
  - adr: adrs/ADR-002-continuum-repository.md
---

# Business Object: SolutionBuildingBlock (SBB)

## Definition

A `SolutionBuildingBlock` (SBB) is the concrete implementation of one or more `ArchitectureBuildingBlocks` (ABBs) by a product, an open-source component, or a self-developed solution. SBBs form the **Solutions Continuum**: from universal product categories to organization-specifically evaluated and approved products.

## Description

While an ABB describes *what* a component must deliver, an SBB describes *with what* this can be realized. SBBs are the link between the abstract Architecture Continuum and concrete repository instances:

```text
ABB "Identity Provider"
  ├── SBB "Keycloak 23.0"      (approved; community-supported)
  ├── SBB "Azure AD B2C"       (acceptable; vendor lock-in risk)
  └── SBB "LDAP standalone"    (deprecated; no MFA support)
```

The `ArchitectureEntity` "acme-keycloak-prod" is an instance of the SBB "Keycloak 23.0" (`entity.instanceOfSBBId`). Via the SBB-ABB chain, ABB conformance can also be derived.

**Governance status** controls which SBBs are usable for new projects:

| governanceStatus | Meaning | Usable for new projects? |
|---|---|---|
| `approved` | Preferred choice; recommended | yes, preferred |
| `acceptable` | Permitted with justification | yes, with restriction |
| `deprecated` | Being phased out; only in existing systems | existing only; no new implementation |
| `prohibited` | Prohibited (security, license, EOL) | no; existing systems must migrate |

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | UUID | required | | v4, globally unique | Primary key |
| name | string | required | | max. 255 characters | Product name (e.g. "Keycloak", "Azure API Management") |
| vendor | string | optional | null | max. 255 characters | Manufacturer or community (e.g. "Red Hat", "Microsoft", "Apache Foundation") |
| version | string | optional | null | max. 100 characters | Product version or version range (e.g. "23.x", ">= 3.2.0") |
| description | string | optional | null | max. 5000 characters; Markdown | Properties, license terms, known restrictions |
| continuumLevel | enum | required | `organization` | `[foundation, common-systems, industry, organization]` | Placement in the Solutions Continuum |
| maturityLevel | enum | required | `experimental` | `[experimental, emerging, established, industry-standard]` | Maturity level of the product |
| governanceStatus | enum | required | `acceptable` | `[approved, acceptable, deprecated, prohibited]` | Approval status for new projects |
| evaluationNotes | string | optional | null | max. 3000 characters; Markdown | Rationale for the governance status; trade-offs |
| scope | enum | required | `organization` | `[built-in, imported, organization]` | Origin, analogous to ABB |
| sourcePackage | string | optional | null | max. 255 characters; only if `scope=imported` | Source package |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| implements | [architecture-building-block](./architecture-building-block.md) | n:m | yes | ABBs whose requirements this SBB satisfies |
| instantiatedAs | [entity](./entity.md) | 1:n | yes | Concrete instances in the repository that reference this SBB (`entity.instanceOfSBBId`) |
| trmCategories | [trm-category](./trm-category.md) | n:m | yes | TRM categories under which this SBB falls |

## Business Rules

| Rule ID | Statement | Trigger |
|---|---|---|
| BR-01 | An SBB with `governanceStatus=prohibited` produces a blocking warning if it is to be set as `instanceOfSBBId` of a new entity | onEntityCreate, onEntityUpdate |
| BR-02 | An SBB with `governanceStatus=deprecated` produces a governance warning on new use but does not block | onEntityCreate |
| BR-03 | SBBs with `scope=imported` or `scope=built-in` are read-only | onUpdate, onDelete |
| BR-04 | An SBB SHOULD reference at least one implemented ABB; SBBs without an ABB reference are merely a product catalog entry without architectural statement (governance warning) | onCreate |

## Lifecycle

```text
acceptable → approved → deprecated → prohibited
```

Imported SBBs start as `acceptable`. The organization evaluates and sets the governance status. When a product reaches EOL or security problems occur, it is set to `deprecated` or `prohibited`; existing instances receive a notice in the UI.

## Analyses (Concept §4.5)

- **SBB diversity**: How many SBBs implement the same ABB? (standardization potential)
- **Standards drift**: Instances whose `instanceOfSBBId` points to a `deprecated` or `prohibited` SBB
- **TRM coverage**: Which TRM categories have approved SBBs, which do not?

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Business Engineer | Initial draft; based on Enterprise Continuum §4 (concept); ADR-002 |

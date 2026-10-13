# ADR-003: Product vs. Project – Coexistence or Separation?

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer (metamodel-configuration.md, solution.md)
**Informed**: all stakeholders

---

## Context and Problem

Modern IT organizations work product-oriented (long-term responsibility, no end date) and project-oriented (time-limited, defined scope). OEA must support both worlds. The open question: Are Product and Project modeled as separate EntityTypes, or as stereotypes of a common concept?

**Prior decision already made** (metamodel-configuration.md): OEA is metamodel-driven — EntityTypes are not hardcoded classes but configurable EntityTypeDefinitions with an `extends` mechanism. Separate "hardcoded" EntityTypes for Product and Project contradict this approach.

## Decision Drivers

- **Metamodel consistency**: no special treatment for Product/Project; same model as all other types
- **Reflect reality**: both concepts coexist in most organizations
- **PPM integration**: lifecycle differences (endDate optional/mandatory) can be expressed via properties
- **Extensibility**: org-specific subtypes (e.g. `CapexProject`, `InternalProduct`) must be possible

## Considered Options

### Option 1: Separate EntityTypes (hardcoded)
- Pro: properties clearly separated; no stereotype concept needed
- Con: contradicts metamodel approach; org cannot define its own sub-variants; code duplication in the backend

### Option 2 (chosen): Common built-in concept + subtypes in the metamodel
- `work-initiative` as built-in EntityType (scope=built-in, isBuiltIn=true)
- `product` and `project` as built-in subtypes (`extends: work-initiative`)
- Lifecycle differences via properties and `creationSteps` (REQ-066 wizard)
- Org can define its own subtypes: `capex-project extends project`, `platform-product extends product`
- Pro: full metamodel consistency; extensible; no special code
- Con: Admin must understand that product/project are configurable types (not "built in")

### Option 3: WorkPackage as base
- Pro: consistency with the WorkPackage concept
- Con: WorkPackage is tactical (sprint, task); Product/Project are strategic — conceptually wrong base

## Decision

**Option 2 is the decision.**

**Built-in EntityTypes** (scope=built-in, as starter package `oea-togaf-starter`):

```
work-initiative (built-in)
  ├── product        extends: work-initiative
  └── project        extends: work-initiative
```

**Properties**:

| Property | work-initiative | product | project |
|---|---|---|---|
| `name` | required | inherited | inherited |
| `description` | optional | inherited | inherited |
| `status` | `planned\|active\|closed` | inherited | inherited |
| `startDate` | optional | inherited | required |
| `endDate` | optional | not used (no end) | required |
| `owner` | optional | required (Product Owner) | optional (Sponsor) |
| `budget` | optional | optional | optional |

**CreationSteps** (REQ-066 wizard):
- `project`: Step 1 = basic info (name, startDate, endDate), Step 2 = domain assignment
- `product`: Step 1 = basic info (name, owner), Step 2 = domain assignment

**Connection types** (built-in):
- `initiative-realizes-solution` (work-initiative → solution): links initiative with EA solution
- `initiative-part-of` (work-initiative → work-initiative): program/project hierarchy

## Consequences

### Positive Consequences
- No special treatment in the backend; product/project are regular EntityTypeDefinitions
- Extensible: `capex-project extends project` without code change
- Catalog + dashboard (UC-06, UC-07) work without adjustment for these types
- Wizard (REQ-066) covers the different mandatory fields via `creationSteps`

### Negative Consequences / Trade-offs
- First-time users without metamodel knowledge might be confused that "create project" happens via a configurable type
- Lifecycle validation (endDate mandatory for project) must be implemented in the wizard/API as a constraint — not as a hard DB schema constraint

### Follow-up Decisions
- `initiative-realizes-solution` connection is added in solution.md
- `work-initiative` types as part of the `oea-togaf-starter` package (ADR-002)

## References

**Concept Chapters**:
- [§18 PPM Integration](../concept/60-integrations/18-ppm-integration.md)
- [§11 Temporal Model](../concept/30-dynamics/11-temporal-model.md)

**Related Business Objects**: metamodel-configuration.md (EntityTypeDefinition + extends), solution.md

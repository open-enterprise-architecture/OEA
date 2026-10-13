# ADR-005: Application-vs-Technology Classification Principle (Default)

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer (metamodel-configuration.md, ADR-002)
**Informed**: all stakeholders

---

## Context and Problem

§6.1.1 of the concept requires every organization to have a binding architecture principle that defines the distinction between Application and Technology:

- **Variant A** ("Platform-as-Technology"): Platform services are Technology, even with their own operations team — classic TOGAF reading
- **Variant B** ("Platform-as-Application"): Products with a Product Owner are Applications, even with a technical character — modern product-org reading

The open question: Which variant is the **default delivery state** of OEA?

**Prior decision already made** (metamodel-configuration.md + ADR-002): OEA is fully metamodel-driven. There are no hardcoded EntityTypes "ApplicationComponent" or "TechnologyComponent" in the code — these are configurable EntityTypeDefinitions. The term "default" therefore means: **which starter configuration is offered during bootstrapping?**

## Decision Drivers

- **Open-source character**: A rigid default principle could exclude or favor certain organizations
- **Non-enforcement**: OEA should support modeling decisions, not make them
- **Low entry barrier**: Without any starting point, first-time users face an empty metamodel — frustration
- **Consistency with ADR-002**: Starter configurations as importable packages; no hardcoding

## Considered Options

### Option 1: Variant A as default ("Platform-as-Technology")
- Pro: more classic TOGAF reading; more familiar to senior EAs
- Con: does not fit product orgs (growing majority); could be perceived as bias

### Option 2: Variant B as default ("Platform-as-Application")
- Pro: fits modern product/platform engineering trends
- Con: deviates from classic EA teaching; unfamiliar to traditional EA departments

### Option 3 (chosen): Deliberately undefined + two starter packages
- OEA is delivered without an enforced app/tech principle
- During bootstrapping, the admin chooses from two optional starter packages (or starts without)
- **Pro**: no bias; respects the organization's decision; low entry barrier through concrete examples
- **Con**: First-time users must make a decision (but this is deliberately intended)

### Option 4: Variant A as default, switching possible at any time
- Pro: pragmatic middle ground
- Con: "switching possible at any time" is in fact laborious once 10,000 entities have already been modeled; the supposed advantage is illusory

## Decision

**Option 3 is the decision.**

OEA has **no enforced default** for Application vs. Technology. Instead there are two optional starter packages (ADR-002 import mechanism) that are offered during bootstrapping (UC-02):

### Starter Package A: `oea-starter-togaf-classic`
Modeled after the classic TOGAF/ArchiMate reading (Variant A: Platform-as-Technology):

| EntityType | Category | Example |
|---|---|---|
| `application-component` | Application | CRM system, ERP |
| `application-service` | Application | Customer API |
| `data-object` | Application | Customer master data |
| `technology-component` | Technology | Kubernetes cluster, PostgreSQL |
| `technology-service` | Technology | Database service |
| `system-software` | Technology | Linux, JVM |
| `network-node` | Technology | Load balancer |

### Starter Package B: `oea-starter-product-org`
Modeled after the modern product-org reading (Variant B: Platform-as-Application):

| EntityType | Category | Example |
|---|---|---|
| `product` (from ADR-003) | Application | CRM platform, data platform |
| `service` | Application | Customer API, analytics service |
| `data-product` | Application | Customer master data set |
| `infrastructure` | Technology | Cloud region, on-prem DC |
| `runtime-platform` | Technology | EKS cluster, VM farm |

### Bootstrapping Flow (complements UC-02)
1. On first login, the admin is asked: "Import starter configuration?"
2. Options: `oea-starter-togaf-classic`, `oea-starter-product-org`, "Start empty"
3. The choice imports the package as `scope=imported`; the admin can then add own types or fork existing ones

### No Enforcement
- Both packages can be freely modified after import (fork via "As own copy" → `scope=organization`)
- Admin can import both packages at the same time (some organizations have hybrid models)
- No code path in the backend depends on the choice — they are pure EntityTypeDefinition configurations

## Consequences

### Positive Consequences
- No bias towards one classification principle
- Low entry barrier through concrete starter configurations
- Full extensibility: org can add its own subtypes (`private-cloud-app extends application-component`)
- Consistent with ADR-002 (import mechanism) and ADR-003 (work-initiative as metamodel type)

### Negative Consequences / Trade-offs
- Two starter packages must be maintained (standalone OSS artifacts)
- First-time users must make a conscious decision during bootstrapping — but this is a conceptual necessity, not a weakness

### Follow-up Decisions
- UC-02 (bootstrapping) receives a step for starter configuration (complements REQ-013/014)
- `oea-starter-togaf-classic` and `oea-starter-product-org` are managed as standalone package files in the repository (`packages/` directory — comes with the Walking Skeleton)

## References

**Concept Chapters**:
- [§6.1.1 Application vs. Technology](../concept/20-entities/06-core-entity-types.md)

**Dependent ADRs**: ADR-002 (import mechanism for starter packages), ADR-003 (product/project as configurable types)

**Related Requirements**: REQ-013/014 (bootstrapping), UC-02, UC-04 (configure metamodel)

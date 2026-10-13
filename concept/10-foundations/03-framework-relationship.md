## 3. Relationship to Arc42 and TOGAF

TOGAF and Arc42 are both planned as standards – but they address different levels and conflict at several points. This chapter makes the friction points explicit so that modeling decisions and schema design can resolve them deliberately.

### 3.1 Basic Orientation

| Dimension | TOGAF | Arc42 |
|---|---|---|
| Type | Enterprise architecture framework | Documentation template for software architectures |
| Scale | Enterprise, portfolio | Single system |
| Core | ADM (process) + Content Metamodel (structure) | 12 predefined documentation chapters |
| Target roles | Enterprise/domain architects, program managers | Software/solution architects, development teams |
| Planning horizon | Months to years (baseline → target) | Lifecycle of a system |

The frameworks are **complementary, not competing**: TOGAF says *how to develop*, Arc42 says *how to document*. However, conceptual tensions arise at the seams.

### 3.2 Conceptual Friction Points

**Friction 1: Hierarchy of components**
- Arc42 building blocks are recursively hierarchical (whitebox/blackbox dialectic)
- TOGAF Application Components are conceptually flat with explicit `partOf` relations
- *Resolution in the metamodel*: Application Components support hierarchy via `partOf`; Arc42 building blocks are a stereotype on Application Component

**Friction 2: View concept**
- Arc42 has three mandatory views (building block, runtime, deployment)
- TOGAF knows flexibly definable viewpoints/views (ISO 42010)
- *Resolution*: Arc42 views are predefined TOGAF viewpoints in the schema `arc42`

**Friction 3: Quality requirements**
- Arc42 has an explicit quality tree as a mandatory chapter
- TOGAF has requirements as a cross-cutting concern, but without a precise NFR structure
- *Resolution*: Quality requirements are modeled as a separate EntityType with a tree structure (adopted from Arc42)

**Friction 4: Architecture decisions**
- Arc42 + community: established ADR culture (individual, versioned, git-based)
- TOGAF: architecture decisions mostly in large documents, less agile
- *Resolution*: ADRs are a first-class EntityType in the repository (Arc42 culture wins)

**Friction 5: Time and versioning** (the biggest difference)
- TOGAF: baseline + target + transition architectures are first-class
- Arc42: no temporal concept – one document = one state
- *Resolution*: The plateau concept (see [§11 (Temporal Model)](../30-dynamics/11-temporal-model.md)) goes beyond both frameworks; Arc42 documents are generated per plateau

**Friction 6: Standards and principles**
- TOGAF: central, enterprise-wide architecture principles/standards
- Arc42: system-local constraints (chapter 2) and cross-cutting concepts (chapter 8)
- *Resolution*: Standards/principles centrally in the repository; Arc42 constraints reference or supplement them

**Friction 7: Interface perspective** (the only real contradiction)
- TOGAF: interface is a neutral connection between systems
- Arc42: interface is described system-centrically (outgoing/incoming from the perspective of *one* system)
- *Risk*: Two Arc42 documents and one EA entry may describe the same interface differently
- *Resolution*: Interface definition exclusively in the repository, Arc42 documents pull data from there (central consequence for document generation)

### 3.3 Common Metamodel as Resolution

The real value of the meta-metamodel strategy is that both frameworks are mapped onto *one common data structure*. What appears as a contradiction in the frameworks becomes **two views of the same data** in the repository:

- The **enterprise view** shows portfolio, landscape, plateaus – TOGAF-compliant
- The **system view** shows a single system as an Arc42 document – generated from the same repository
- **Interfaces, standards, ADRs** live centrally and are consistent in both views

This is the technical core: The friction does not disappear in the standards, it is resolved in the tool.

### 3.4 Consequences for Schema Design

Concretely, this results in the following for schema design:

1. **Application Components support hierarchy** (for Arc42 building blocks)
2. **QualityRequirement as a separate EntityType** with a tree structure (from Arc42)
3. **ADRs as a first-class EntityType** (from Arc42 practice, supplements TOGAF)
4. **Plateau concept as an extension of both frameworks** (see [§11 (Temporal Model)](../30-dynamics/11-temporal-model.md))
5. **Interfaces centrally in the repository** – Arc42 documents only as readers
6. **Standards/principles centrally** – Arc42 constraints reference them
7. **Arc42 document generator**: generates a complete Arc42 document for a system and plateau from the repository, with all 12 chapters populated from modeled entities

---

← [Meta-Metamodel](../00-overview/02-meta-metamodel.md) · [🏠 Overview](../README.md) · [Enterprise Continuum & TRM](04-enterprise-continuum-trm.md) →

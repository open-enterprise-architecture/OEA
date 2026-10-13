## 4. Enterprise Continuum and Technical Reference Model

Two TOGAF concepts that are fundamental to the repository structure and often underestimated: the **Enterprise Continuum** as a classification and organization structure for reusable architecture artifacts, and the **Technical Reference Model (TRM)** as a taxonomic classification of technology services.

### 4.1 Enterprise Continuum as a Separate Repository Area

The Enterprise Continuum distinguishes **reusable blueprints** from **concrete organization instances**. This distinction runs through the entire repository.

**Architecture Continuum** (specifying, from generic to specific):
- Foundation Architectures (universal building blocks)
- Common Systems Architectures (cross-industry)
- Industry Architectures (industry-specific, e.g., TM Forum Frameworx)
- Organization-Specific Architectures

**Solutions Continuum** (implementing, parallel to it):
- Foundation Solutions
- Common Systems Solutions
- Industry Solutions
- Organization-Specific Solutions

### 4.2 Architecture Building Blocks and Solution Building Blocks

Central distinction in the Enterprise Continuum:

- **Architecture Building Block (ABB)**: Specification of a reusable architecture unit. Describes *what* something does and *which properties* it must have, technology-neutral.
- **Solution Building Block (SBB)**: Concrete implementation of an ABB by a product or a component.

Example: ABB "Identity Provider" (must speak SAML 2.0, must support MFA) is implemented by SBB "Keycloak 23.0" or SBB "Azure AD".

This is the same logical/physical logic as at instance level – but at a higher level of abstraction: it is not about a concrete instance *in* the organization, but about blueprints *for* the organization.

### 4.3 Repository Structure

```
Repository:
├── /continuum/                    (reusable blueprints)
│   ├── /architecture-building-blocks/
│   ├── /solution-building-blocks/
│   ├── /patterns/
│   └── /reference-architectures/
│
└── /instances/                    (concrete organization entities)
    ├── /applications/
    ├── /technologies/
    └── ...
```

An organization instance references ABBs/SBBs:

```yaml
PhysicalApplicationComponent "acme-keycloak-prod":
  instanceOf: SBB "keycloak"
  conformsToABB: "identity-provider"
```

### 4.4 New EntityTypes

```yaml
EntityType: ArchitectureBuildingBlock (ABB)
  properties:
    - name, description
    - continuumLevel: foundation | common | industry | organization
    - maturityLevel: experimental | emerging | established | industry-standard
    - governanceStatus: proposed | approved | deprecated
    - specifications: text / structured
    - requirements: Requirement[]
    - industry: string (for industry level)
  relations:
    - refinesABB: ABB (hierarchy: more specific ABB refines more generic one)
    - implementedBy: SBB[]

EntityType: SolutionBuildingBlock (SBB)
  properties:
    - name, vendor, version
    - continuumLevel: foundation | common | industry | organization
    - maturityLevel
    - governanceStatus: approved | acceptable | deprecated | prohibited
  relations:
    - implements: ABB[]
    - instantiatedAs: PhysicalApplicationComponent[] | PhysicalTechnologyComponent[]

EntityType: ArchitecturePattern
  properties:
    - name, description, problem, solution
    - applicability, consequences
    - maturityLevel
  relations:
    - appliedIn: Entity[] (where is the pattern concretely implemented?)
    - relatedPatterns: ArchitecturePattern[]

EntityType: ReferenceArchitecture
  properties:
    - scope, targetIndustry
  relations:
    - composedOf: ABB[]
    - basedOnPatterns: ArchitecturePattern[]
    - instantiatedAs: Plateau (usable as target state)
```

### 4.5 Analyses over the Enterprise Continuum

Separating the continuum from instances opens up new view possibilities:

- **ABB coverage**: Which ABBs have we defined but have no instance for? (gap)
- **SBB diversity**: How many different SBBs fulfill the same ABB? (unwanted diversity)
- **Standards drift**: Which instances deviate from their referenced SBB (e.g., non-approved version)?
- **Continuum maturity**: Which organization-specific artifacts have the potential to migrate back into the continuum as a common systems or industry blueprint?

### 4.6 Technical Reference Model (TRM)

The TRM is a concrete example of a foundation architecture: a **taxonomy of technology services** with categories and subcategories. TOGAF ships an example TRM that should be adapted to the organization.

In the metamodel, the TRM is a **separate classification hierarchy**:

```yaml
EntityType: TRMCategory
  properties:
    - name, description
    - level: 1 | 2 | 3  (hierarchy depth)
  relations:
    - parent: TRMCategory
    - children: TRMCategory[]
    - preferredStandard: SBB | PhysicalTechnologyComponent
    - acceptedAlternatives: SBB[]
    - deprecatedOptions: SBB[]
    - evaluationCriteria: string[]
    - lastReviewed: date
```

Each `PhysicalTechnologyComponent` can be assigned to one or more TRM categories:

```yaml
PhysicalTechnologyComponent gains:
  - trmClassification: TRMCategory[] (n:m)
```

### 4.7 TRM-Based Analyses

- **Technology diversity**: How many products do we have per TRM category? Five message brokers when two would suffice?
- **TRM coverage**: Which TRM services have we covered, which are missing?
- **Standard enforcement**: Which technologies deviate from the preferred standard of their TRM category?
- **Lifecycle per category**: Landscape map (Bebauungsplan) filtered to one TRM category shows change within a service area

### 4.8 Use in the Workflow

The Enterprise Continuum is not just documentation but **active in the architecture workflow**:

1. **New project starts** → check whether existing ABBs/SBBs/patterns can be reused
2. **New technology is evaluated** → classify in the TRM, check against existing standards
3. **Architecture decision** → uses ABBs/patterns as a reference in ADRs
4. **Experience from a project** → if an organization-specific artifact becomes reusable, promote it backwards into the continuum

### 4.9 Constraints

```yaml
constraint: instance-conforms-to-sbb
  appliesTo: PhysicalApplicationComponent | PhysicalTechnologyComponent
  rule: "if entity.instanceOf != null, entity properties must satisfy SBB specification"
  severity: warning

constraint: technology-has-trm-classification
  appliesTo: PhysicalTechnologyComponent
  rule: "entity.trmClassification.length >= 1"
  severity: warning
  message: "Technology not classified in the TRM – standards governance not possible"

constraint: sbb-implements-abb
  appliesTo: SolutionBuildingBlock
  rule: "entity.implements.length >= 1"
  severity: warning
  message: "An SBB without an ABB reference is just a product catalog entry"
```

---

← [Framework Relationship](03-framework-relationship.md) · [🏠 Overview](../README.md) · [Principles, Standards & ADRs](05-principles-standards-adrs.md) →

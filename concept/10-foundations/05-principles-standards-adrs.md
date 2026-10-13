## 5. Architecture Principles, Standards, and Decisions

Principles, standards, and ADRs are the **normative backbone** of the repository: they define *how* architecture should be shaped and document *why* which decisions were made. This chapter details the three concepts, their relationships, and the compliance model.

### 5.1 Distinction between Principle, Standard, and Guideline

The three terms are often used synonymously but are conceptually clearly distinguishable.

| Aspect | Principle | Standard | Guideline |
|---|---|---|---|
| Answers | Why? Where to? | What? How? | How best? |
| Level of abstraction | high | concrete | concrete |
| Technology reference | agnostic | mostly specific | mostly specific |
| Lifespan | years to decades | months to years | months to years |
| Bindingness | guiding statement | binding | recommended |
| Verifiability | qualitative only | measurable / verifiable | verifiable |
| Example | "Cloud-First" | "AWS as the primary cloud provider" | "Prefer CloudFormation over CDK" |

**The hierarchy**: Principles are parents of standards; standards can have guidelines. A principle "Cloud-First" manifests in several standards (cloud provider, container orchestrator, IaC tool). If the cloud provider changes, the standard changes, but the principle remains.

### 5.2 EntityType: ArchitecturePrinciple

Modeled on the TOGAF standard template:

```yaml
EntityType: ArchitecturePrinciple
  properties:
    - name: string                    # Short title, e.g. "Buy before Build"
    - statement: text                 # The principle itself, concisely worded
    - rationale: text                 # Why does this principle apply?
    - implications: text              # What follows from it in practice?
    - category: enum[business, data, application, technology, security, cross-cutting]
    - status: proposed | approved | deprecated | superseded
    - approvedBy: Role | OrganizationUnit
    - approvedDate: date
    - reviewDate: date                # when to review
  relations:
    - refinedByStandards: ArchitectureStandard[]
    - derivedFrom: Driver | Goal      # where the principle comes from
    - supersededBy: ArchitecturePrinciple  # on replacement
```

### 5.3 EntityType: ArchitectureStandard

```yaml
EntityType: ArchitectureStandard
  properties:
    - name: string
    - description: text
    - type: technology | integration | data | security | process | documentation
    - scope: enum[global, domain-specific, project-specific]
    - mandatoryLevel: enum[must, should, may]  # RFC 2119 style
    - status: draft | active | deprecated | superseded
    - version: string                 # Standards have versions!
    - effectiveFrom, effectiveUntil: date
    - references: url[]               # external standards (ISO, RFC, …)
  relations:
    - implementsPrinciple: ArchitecturePrinciple[]
    - appliesTo: EntityType[]         # which entity types are affected
    - preferredTechnology: SBB | PhysicalTechnologyComponent
    - acceptedAlternatives: SBB[]
    - relatedStandards: ArchitectureStandard[]
    - relatedGuidelines: Guideline[]
    - supersededBy: ArchitectureStandard
```

The important difference from principles: standards have a **version** and a **mandatoryLevel** (must/should/may) – this makes them verifiable and governable.

### 5.4 EntityType: Guideline

```yaml
EntityType: Guideline
  properties:
    - name, description
    - recommendation: text
    - rationale: text
  relations:
    - relatesToStandard: ArchitectureStandard
    - relatesToPrinciple: ArchitecturePrinciple
```

Guidelines are explicitly non-binding. Their existence in the metamodel prevents teams from modeling recommendations as standards (which would lead to false bindingness).

### 5.5 Compliance Model: Waiver, Deviation, Violation

Reality: violations of standards are common. The metamodel knows three states:

**Deviation** – identified deviation, not yet assessed
**Waiver** – approved exception, time-limited, with conditions
**Violation** – non-approved deviation, must be remedied

```yaml
EntityType: ComplianceDeviation
  properties:
    - discoveredAt: date
    - description: text
    - status: open | waiver-requested | waivered | violation | resolved
    - severity: low | medium | high | critical
    - remediationPlan: text
  relations:
    - deviatesFrom: ArchitectureStandard
    - affects: Entity | Relation      # a deviation can be an Entity OR a Relation
    - discoveredBy: Role
    - resolvedBy: Project | ADR
    - relatedWaiver: ArchitectureWaiver

EntityType: ArchitectureWaiver
  properties:
    - reason: text                    # Why is the deviation made?
    - grantedDate: date
    - expiresDate: date               # Waivers are ALWAYS time-limited
    - conditions: text                # under which conditions
    - renewable: boolean
  relations:
    - waivesStandard: ArchitectureStandard
    - grantedFor: Entity[] | Relation[]  # a waiver can cover entities or relations
    - grantedBy: Role | ARB
    - documentedInADR: ADR
    - resultsFromDeviation: ComplianceDeviation
```

Key property: **waivers always have an expiry date**. Unlimited waivers are architecture debt that becomes invisible. The metamodel enforces expiry.

### 5.6 Architecture Decision Records (ADRs)

ADRs are **separate entities** that reference other entities – not merely text fields on applications. This is the core of the Arc42 ADR culture.

```yaml
EntityType: ADR (ArchitectureDecisionRecord)
  properties:
    - id: string                      # e.g. "ADR-042"
    - title: string
    - status: proposed | accepted | deprecated | superseded | rejected
    - date: date
    - context: text                   # Which problem, which constraints
    - decision: text                  # What was decided
    - alternatives: Alternative[]     # What was considered and rejected
    - consequences: {positive: text, negative: text, neutral: text}
    - origin: intentional | emergent  # see [§19.1 (Agile Scaling)](../60-integrations/19-agile-scaling.md) (SAFe)
    - decisionLevel: enterprise | domain | system | component
  relations:
    - affects: Entity[] | Relation[]  # n:m – can affect entities AND relations
    - decidedBy: Role | ARB
    - participants: Role[]
    - supersedes: ADR                 # if this ADR replaces an earlier one
    - supersededBy: ADR               # back reference
    - relatesTo: ADR[]                # loose connections
    - basedOnPrinciple: ArchitecturePrinciple[]
    - referencesStandard: ArchitectureStandard[]
    - documentsWaiver: ArchitectureWaiver  # if the decision justifies a waiver

Alternative:
  - name: string
  - description: text
  - prosAndCons: text
  - reasonForRejection: text
```

### 5.7 ADRs per Entity and Relation – the Cross-Reference Power

The relationship `ADR.affects` is n:m and accepts both **entities and relations** as targets (see reification in [§2 (Meta-Metamodel)](../00-overview/02-meta-metamodel.md)). This is the core of navigability:

- An **application** has accumulated several ADRs over time (technology choice, architecture pattern, data storage, …)
- A **standard** is established by an ADR (the decision to introduce this standard is itself an ADR)
- A **plateau** is shaped by ADRs (strategic direction decisions)
- An **interface** (as an entity) can have an ADR (synchronous vs. asynchronous, protocol choice)
- A **relation between entities** can have an ADR if the decision concerns the connection, not the systems – e.g., "Shop will communicate with ERP asynchronously instead of synchronously" affects the relation `Shop -[uses]-> ERP`, not Shop or ERP themselves

Views that result from this:

- **ADR history of an entity or relation**: all decisions that have shaped this element, chronologically
- **Impact of an ADR**: all entities and relations affected by this decision
- **Decision trail**: superseding chain shows how a decision has evolved over time
- **Orphan ADRs**: ADRs that no longer reference any entities/relations (because all affected ones are retired – cleanup candidates)

### 5.8 The Navigation Chain

The complete normative chain in the repository:

```text
Goal / Driver
    ↓ motivates
ArchitecturePrinciple
    ↓ is refined into
ArchitectureStandard
    ↓ is established/changed by
ADR
    ↓ affects
Entity (Application, Interface, Technology, …)
    ↓ may deviate via
ComplianceDeviation
    ↓ is approved as
ArchitectureWaiver
    ↓ is documented in
ADR
```

This chain is navigable in both directions. From any entity, the normative roots can be queried: "Which standards/principles apply to this application, and which ADRs shaped these decisions?"

### 5.9 Compliance Views

Several views become possible automatically through this model:

- **Standards compliance report**: per standard, how many entities are compliant/deviating
- **Active waivers**: all valid waivers, sorted by expiry date (renewal backlog)
- **Expired waivers**: waivers whose expiry date has passed (immediate action required)
- **Principle impact**: which standards, ADRs, and entities depend on which principle
- **Decision lineage**: complete ADR chain for an entity
- **Governance debt**: sum of open deviations × severity (technical debt analog)

### 5.10 Constraints

```yaml
constraint: waiver-has-expiry
  appliesTo: ArchitectureWaiver
  rule: "entity.expiresDate != null AND entity.expiresDate > entity.grantedDate"
  severity: error
  message: "A waiver without an expiry date is invisible architecture debt"

constraint: active-standard-has-principle
  appliesTo: ArchitectureStandard
  when: "entity.status == 'active'"
  rule: "entity.implementsPrinciple.length >= 1"
  severity: warning
  message: "Standard without a principle reference – why does this standard exist?"

constraint: waiver-documented-in-adr
  appliesTo: ArchitectureWaiver
  rule: "entity.documentedInADR != null"
  severity: warning
  message: "Waiver without an ADR – rationale history is missing"

constraint: violation-has-remediation
  appliesTo: ComplianceDeviation
  when: "entity.status == 'violation'"
  rule: "entity.remediationPlan != null AND entity.resolvedBy != null"
  severity: error

constraint: superseded-adr-has-successor
  appliesTo: ADR
  when: "entity.status == 'superseded'"
  rule: "entity.supersededBy != null"
  severity: error
```

### 5.11 Workflow Integration

The model directly supports the following workflows:

**A new entity is created**: The tool checks which standards apply to this EntityType and flags potential deviations.

**A standard is changed**: Impact analysis shows all affected entities; automatically generated deviation candidates for entities that would now deviate.

**A waiver expires**: The tool notifies the owner and ARB in good time; if not renewed, the deviation automatically becomes a violation.

**An ADR is created**: Linking to affected entities is mandatory (otherwise why the ADR?). Check whether existing ADRs must be superseded.

**Architecture Review Board** (from [§18.9 (PPM Integration)](../60-integrations/18-ppm-integration.md)): ARB decisions result in ADRs; waiver approvals go through ADRs; principle/standard changes are documented by ADRs.

### 5.12 Distinction from Compliance Requirements (external)

Important: The standards described here are **internal architecture standards**. External regulatory compliance requirements (GDPR, SOX, ISO 27001, DORA) are modeled separately as `ComplianceRequirement` (see PPM chapter [§18 (PPM Integration)](../60-integrations/18-ppm-integration.md)). The difference:

- `ArchitectureStandard`: technical/architectural norms set by the organization itself
- `ComplianceRequirement`: regulatory requirements imposed from outside

Both can overlap: A `ComplianceRequirement` (e.g., "Data must be encrypted") often manifests as an internal `ArchitectureStandard` (e.g., "TLS 1.3 is mandatory"). The relation `ArchitectureStandard.derivedFrom: ComplianceRequirement` makes this explicit.

---

← [Enterprise Continuum & TRM](04-enterprise-continuum-trm.md) · [🏠 Overview](../README.md) · [Core Entity Types](../20-entities/06-core-entity-types.md) →

## 7. Motivation, Stakeholders, and Goals

An EA repository describes not only *what* exists, but also *why* it exists and *for whom*. The motivation layer is the answer level for these questions – from the TOGAF Motivation Extension and the ArchiMate Motivation Layer.

### 7.1 The Motivation Chain

The concepts build on each other:

```text
Driver (external/internal driver)
   ↓ triggers
Assessment (evaluation of the current state)
   ↓ motivates
Goal (strategic goal)
   ↓ refined by
Outcome (measurable result)
   ↓ realized by
Capability + Course of Action
   ↓ implemented through
Architecture Elements (Applications, Processes, Data, Technology)
```

### 7.2 New EntityTypes

```yaml
EntityType: Vision
  properties:
    - statement: text                 # long-term, inspiring statement
    - timeHorizon: string             # e.g. "2030+"
  relations:
    - pursuedBy: OrganizationUnit
    - refinedBy: Goal[]

EntityType: Driver
  properties:
    - type: external | internal
    - category: market | regulatory | technology | competitive | strategic | financial
    - description: text
    - urgency: low | medium | high | critical
  relations:
    - triggers: Assessment[] | Goal[]

EntityType: Assessment
  properties:
    - type: strengths | weaknesses | opportunities | threats | performance
    - findings: text
    - date: date
  relations:
    - assesses: Capability | Process | ApplicationComponent | Entity
    - conducted by: Role
    - motivates: Goal[]

EntityType: Goal
  properties:
    - statement: text
    - category: strategic | tactical | operational
    - timeHorizon: date
    - priority: critical | high | medium | low
    - status: proposed | active | achieved | abandoned
  relations:
    - refinedBy: Goal[] (hierarchy)
    - measuredBy: Outcome[]
    - contributesTo: Vision
    - supportedBy: Capability[]

EntityType: Outcome
  properties:
    - description: text
    - measurementCriteria: text
    - targetValue, currentValue: number | string
    - targetDate: date
  relations:
    - measures: Goal
    - trackedBy: KPI

EntityType: CourseOfAction
  properties:
    - type: strategy | tactic | policy
    - description: text
  relations:
    - pursues: Goal
    - realizedBy: WorkPackage[] | Project[]

EntityType: Stakeholder
  properties:
    - name: string
    - type: individual | group | organization
    - internal: boolean
    - influence: low | medium | high
    - interest: low | medium | high
  relations:
    - represents: Role | OrganizationUnit (optional)
    - hasConcerns: Concern[]
    - associatedWith: Capability[] | Process[] | ApplicationComponent[]

EntityType: Concern
  properties:
    - description: text
    - category: functional | performance | security | compliance | cost | usability | other
    - severity: informational | worrying | blocking
  relations:
    - raisedBy: Stakeholder[]
    - addressedBy: Requirement[] | ArchitecturePrinciple[] | ADR[]

EntityType: Requirement
  properties:
    - statement: text                 # the requirement itself, concisely worded
    - rationale: text                 # why the requirement exists
    - type: functional | non-functional | constraint | assumption
    - category: business | data | application | technology | security | compliance | usability | performance | cross-cutting
    - priority: must | should | could | wont (MoSCoW)
    - status: identified | analyzed | approved | allocated | realized | verified | rejected | deferred | retired
    - source: text                    # where the requirement comes from
    - sourceType: stakeholder | regulation | contract | workshop | audit-finding | strategy | other
    - acceptanceCriteria: text        # mandatory for status >= 'approved'
    - verificationMethod: test | inspection | demonstration | analysis | none
    - verificationStatus: not-verified | verified | failed
    - version: integer                # new version on every semantic change
  relations:
    # Sources and rationale (upward)
    - raisedBy: Stakeholder[]         # who raised the requirement
    - addresses: Concern[]            # which stakeholder concern is addressed
    - derivedFromGoal: Goal[]         # strategic justification
    - derivedFromCompliance: ComplianceRequirement[]   # regulatory justification
    - derivedFromPrinciple: ArchitecturePrinciple[]    # principle reference
    
    # Implementation (downward)
    - realizedBy: Entity[] | Relation[]   # which architecture elements fulfill it
    - implementedInPlateau: Plateau[]     # in which plateau it must be fulfilled
    - allocatedTo: WorkPackage[]          # which work package delivers the fulfillment
    - referencesStandard: ArchitectureStandard[]  # standard fulfills the requirement
    - documentedInADR: ADR[]              # ADR justifies/fulfills the requirement
    
    # Relationships to other requirements
    - refines: Requirement              # refines a coarse requirement
    - refinedBy: Requirement[]          # back reference for hierarchy
    - dependsOn: Requirement[]          # presupposes others
    - conflictsWith: Requirement[]      # contradicts others
    - supersedes: Requirement           # replaces an old version
    
    # Risk connections
    - mitigatesRisk: Risk[]             # requirement reduces risk
    - riskIfNotMet: Risk                # what happens if not fulfilled

EntityType: Assumption
  properties:
    - statement: text
    - confidence: low | medium | high
    - validationStatus: unvalidated | validated | invalidated
    - impactIfWrong: text
  relations:
    - underlies: ADR | Requirement | Goal
    - verifiedBy: Assessment
```

**Stereotypes for different requirement levels**:

The EA repository focuses on **architecture requirements** – business and architectural requirements that affect several systems or the landscape as a whole. **System requirements** (requirements for concrete systems to be built) classically belong in tools like Jira, Doors, Polarion, or Jama. Pragmatically, however, they are allowed in the repository – as stereotypes:

```yaml
Stereotype: ArchitectureRequirement (Default)
  basedOn: Requirement
  intent: "Requirement on the architecture, cross-system"

Stereotype: SystemRequirement
  basedOn: Requirement
  intent: "Requirement on a concrete system, often the result of an architecture requirement"
  additionalProperties:
    - targetSystem: ApplicationComponent
    - externalReference: string  # Reference into the actual system requirements tool

Stereotype: QualityRequirement
  basedOn: Requirement
  intent: "Non-functional requirement related to the quality tree (Arc42 chapter 10)"
  additionalProperties:
    - qualityCategory: performance | security | usability | maintainability | reliability | scalability
    - measurableTarget: string  # concrete target value, e.g. "p95 response < 200ms"

Stereotype: ComplianceRequirement (already exists, see §17/§20)
  intent: "External regulatory requirement – often becomes the source for ArchitectureRequirements"
```

The **recommendation**: SystemRequirements are modeled in the EA repository only if they are architecturally relevant (cross-system constraints, compliance evidence, trace obligations). Detailed requirements for a single system remain in the respective system tools, linked via `externalReference`.

### 7.3 Stakeholder-Concern Mapping

The central practice from ISO 42010: **viewpoints are derived from stakeholder concerns**. In the repository:

```yaml
View (already exists) gains:
  - addressesConcerns: Concern[]
  - forStakeholders: Stakeholder[]
```

This makes it possible to check: Which stakeholder concerns are not addressed by at least one view?

### 7.4 Glossary as a First-Class Concept

An often forgotten element: consistent terminology across the entire repository.

```yaml
EntityType: GlossaryTerm
  properties:
    - term: string
    - definition: text
    - domain: Domain                  # business context
    - aliases: string[]               # synonyms
    - abbreviations: string[]
    - status: active | deprecated
    - source: string                  # source of the definition
  relations:
    - relatedTerms: GlossaryTerm[]
    - supersedes: GlossaryTerm
    - usedIn: Entity[]                # where the term is used
```

Glossaries are particularly valuable for:

- Business unit–IT communication (shared language)
- Cross-domain projects (customer term in sales vs. support)
- Compliance audits (terminology traceability)

---

← [Core Entity Types](06-core-entity-types.md) · [🏠 Overview](../README.md) · [Organization, Roles & Persons](08-organization-roles-persons.md) →

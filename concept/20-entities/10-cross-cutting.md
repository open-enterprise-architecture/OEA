## 10. Additional Cross-Cutting Concepts

Briefer supplements that round out the metamodel.

### 10.1 Location and Geography

```yaml
EntityType: Location
  properties:
    - name
    - type: country | region | city | site | building | room | zone | cloud-region
    - coordinates: {lat, lng}
    - jurisdictionalRegion: string  # for compliance (EU, US, APAC)
    - dataResidencyZone: string
  relations:
    - partOf: Location
    - hosts: TechnologyComponent[] | ApplicationComponent[]
    - regulatedBy: ComplianceRequirement[]
```

**Important applications**:

- Data residency evidence (GDPR, BDSG)
- Disaster recovery planning (multi-region)
- Sovereign cloud compliance
- Latency-critical applications (node distribution)

### 10.2 Security Modeling

```yaml
EntityType: SecurityZone
  properties:
    - name
    - trustLevel: public | dmz | internal | restricted | top-secret
    - networkSegment: string
  relations:
    - contains: TechnologyComponent[] | ApplicationComponent[]
    - connectedTo: SecurityZone[] (via TrustBoundary)

EntityType: TrustBoundary
  properties:
    - description
    - securityControls: string[]    # e.g. "firewall", "waf", "idp"
  relations:
    - separates: SecurityZone, SecurityZone
    - monitoredBy: ApplicationComponent

EntityType: DataClassification (extended as enum)
  values: public | internal | confidential | restricted | pii | phi | pci
  relations:
    - appliesTo: DataEntity
    - requiresControls: SecurityControl[]

EntityType: SecurityControl
  properties:
    - type: encryption | access-control | audit | monitoring
    - implementation: text
  relations:
    - protects: Entity[]
    - mandatedBy: ComplianceRequirement[]

# CIA protection need classification per ISO 27005 / BSI IT-Grundschutz
EntityType: ProtectionNeed
  properties:
    - confidentiality: normal | high | very-high
    - integrity: normal | high | very-high
    - availability: normal | high | very-high
    - justification: text            # rationale for the classification
    - assessedBy: Role
    - assessedDate: date
    - nextReview: date
  relations:
    - appliesTo: Entity              # Application, Technology, DataEntity, Process, ...
```

**Protection need inheritance**: The protection need of an application component can be derived from the maximum of the protection needs of the processed data entities. The metamodel allows both explicit maintenance and calculated derivation.

### 10.3 Sustainability / Green IT

Increasingly important for enterprise reporting (CSRD, GHG Protocol):

```yaml
ApplicationComponent, TechnologyComponent optionally gain:
  - estimatedAnnualCarbonKg: number
  - energyEfficiencyRating: string
  - powerConsumptionKwh: number
  - sustainabilityCertifications: string[]  # e.g. "ENERGY STAR"

EntityType: CarbonAssessment
  properties:
    - entity: reference
    - assessmentMethod: string        # e.g. "SCI", "GHG Protocol Scope 3"
    - co2eKgPerYear: number
    - confidence: low | medium | high
    - assessmentDate: date
  relations:
    - assesses: Entity
    - contributesToGoal: Goal         # e.g. "Net Zero 2030"
```

**Applications**:

- Carbon hotspot analysis (which applications/technologies cause the most emissions)
- Sustainability target state (plateau with a lower total CO2 footprint)
- Vendor selection under sustainability aspects
- CSRD reporting

### 10.4 Localization and Internationalization

For globally positioned organizations:

```yaml
Entity optionally gains:
  - supportedLanguages: string[]     # ISO 639-1
  - supportedRegions: string[]
  - i18nStatus: not-applicable | planned | partial | complete

ApplicationComponent extended by:
  - localizationScope: string[]      # what is localized (UI, content, date formats, currency)
```

### 10.5 Contract Modeling (TOGAF Governance Extension)

```yaml
EntityType: Contract
  properties:
    - contractType: SLA | OLA | UC | data-sharing | license | outsourcing
    - status: active | expired | renegotiating
    - validFrom, validTo: date
    - autoRenew: boolean
  relations:
    - parties: OrganizationUnit[] | Vendor[]
    - governs: Entity[]               # what the contract governs
    - hasMetrics: KPI[]
    - documentLocation: url
```

Especially important for:

- SaaS contracts (license compliance)
- Outsourcing relationships
- Data sharing agreements (GDPR-relevant)

### 10.6 Deliverable (Implementation Layer)

Still missing from ArchiMate:

```yaml
EntityType: Deliverable
  properties:
    - description
    - deliveryDate: date
    - status: planned | in-progress | delivered | accepted
    - type: document | artifact | system | training | data-migration
  relations:
    - producedBy: WorkPackage | Project
    - handedOverTo: Role | OrganizationUnit
    - enables: Plateau                # deliverable enables the plateau transition
```

### 10.7 Requirements Repository Aspects

The Requirement EntityType itself is defined in [§7.2 (Motivation, Stakeholders, Goals)](07-motivation-stakeholders-goals.md). This section adds the operational aspects: lifecycle, linking discipline, views, and constraints that make the tool an **Architecture Requirements Repository**.

**Lifecycle**: Requirements pass through a clearly defined status workflow:

```text
identified → analyzed → approved → allocated → realized → verified
                              ↓
                          rejected | deferred
                              ↓
                          retired (at end of life)
```

Critical transitions:

- `identified → analyzed`: First business assessment, rationale documented
- `analyzed → approved`: Acceptance criterion is mandatory (see constraints below)
- `approved → allocated`: Assignment to plateau and work package
- `allocated → realized`: Implementation completed, realization relation maintained
- `realized → verified`: Acceptance criterion successfully verified

**Permitted links**: Requirements are the central link between strategy and implementation. Meaningful connections are:

| Link | Meaning | Weight |
|---|---|---|
| Requirement ↔ Goal/Outcome | Strategic rationale | Should exist for every architecture requirement |
| Requirement ↔ Stakeholder/Concern | Source and addressee | Important for prioritization |
| Requirement ↔ Entity / Relation | Realization | Core of the trace matrix |
| Requirement ↔ Standard / ADR | Establishment via standard/decision reference | Central for governance |
| Requirement ↔ ComplianceRequirement | Regulatory source | Mandatory for compliance-driven requirements |
| Requirement ↔ Plateau | Temporal placement | Mandatory for approved+ |
| Requirement ↔ WorkPackage | Implementation allocation | Mandatory for allocated+ |
| Requirement ↔ Requirement | Hierarchy and relationships | refines/dependsOn/conflictsWith |
| Requirement ↔ Risk | Risk mitigation | For security/compliance requirements |

**Links to be used with caution**:

- Requirement → Process: only at process level, not to individual activities (that would be BPM tool detail)
- Requirement → Interface: only if interface-specific; general API requirements belong in standards
- Requirement → Capability: legitimate, but avoid overlap with Goal

**Links not permitted**:

- Requirement → Person (instead of role/stakeholder): GDPR and personnel change problem
- Requirement → code artifacts (repos, branches, files): belongs in ALM, not in EA
- Requirement → concrete dev tickets: goes via WorkPackage, not directly
- Requirement → test implementations: acceptance criterion yes, test code no

**Anti-pattern: requirement formulated as a task**: "System X must be migrated to the cloud" is a WorkPackage, not a requirement. The requirement would be: "System X must be able to run in a scalable, geographically distributed environment with availability ≥ 99.9%". The tool should promote the **what** instead of **what we will do** through templates and validations.

**Views and reports**:

- **Trace matrix**: all requirements × all realizing entities, with coverage indicator
- **Coverage gaps**: requirements with status `approved` or higher but without a `realizedBy` link
- **Verification status**: requirements with status `realized` but without successful verification
- **Conflict view**: all `conflictsWith` relationships with resolution status
- **Plateau coverage**: requirements per plateau, filtered to non-`verified`
- **Stakeholder view**: requirements grouped by `raisedBy`, with status per stakeholder
- **Compliance trace**: ComplianceRequirement → ArchitectureRequirement → realizing entities

**Constraints**:

```yaml
constraint: approved-requirement-has-acceptance-criteria
  appliesTo: Requirement
  when: "entity.status in [approved, allocated, realized, verified]"
  rule: "entity.acceptanceCriteria != null AND entity.acceptanceCriteria != ''"
  severity: error
  message: "An approved requirement without an acceptance criterion is wishful thinking, not an engineering artifact"

constraint: approved-requirement-has-rationale
  appliesTo: Requirement
  when: "entity.status in [approved, allocated, realized, verified]"
  rule: "entity.derivedFromGoal.length >= 1 OR entity.derivedFromCompliance.length >= 1 
         OR entity.addresses.length >= 1"
  severity: warning
  message: "Approved requirement without strategic, regulatory, or stakeholder rationale"

constraint: realized-requirement-has-realization
  appliesTo: Requirement
  when: "entity.status in [realized, verified]"
  rule: "entity.realizedBy.length >= 1"
  severity: error

constraint: allocated-requirement-has-plateau-and-workpackage
  appliesTo: Requirement
  when: "entity.status in [allocated, realized, verified]"
  rule: "entity.implementedInPlateau.length >= 1 AND entity.allocatedTo.length >= 1"
  severity: error

constraint: requirement-conflicts-resolved-before-approval
  appliesTo: Requirement
  when: "entity.status in [approved, allocated, realized, verified]"
  rule: "all entity.conflictsWith have resolution-ADR or both have status='deferred'"
  severity: warning

constraint: system-requirement-has-target-system
  appliesTo: Requirement
  when: "entity has stereotype 'SystemRequirement'"
  rule: "entity.targetSystem != null"
  severity: error
```

**Versioning**: Every semantic change to a requirement creates a new version. The old version remains referenceable (for ADR and audit traceability). The general versioning mechanism from the temporal model is used – requirements are not special cases.

**What the repository is not**: Not an issue tracker (no comment threads, watchers, activity streams), not a test management tool (no test execution), not a document management system. These functions would dilute the EA focus – those who need them integrate specialized tools via the API.

---

← [Process Architecture](09-process-architecture.md) · [🏠 Overview](../README.md) · [Temporal Model](../30-dynamics/11-temporal-model.md) →

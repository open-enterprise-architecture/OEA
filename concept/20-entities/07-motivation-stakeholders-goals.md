## 7. Motivation, Stakeholder und Ziele

Ein EA-Repository beschreibt nicht nur *was* existiert, sondern auch *warum* es existiert und *für wen*. Die Motivation-Schicht ist die Antwort-Ebene auf diese Fragen – aus TOGAF Motivation Extension und ArchiMate Motivation Layer.

### 7.1 Die Motivations-Kette

Die Konzepte bauen aufeinander auf:

```
Driver (externer/interner Treiber)
   ↓ triggers
Assessment (Bewertung des Ist-Zustands)
   ↓ motivates
Goal (strategisches Ziel)
   ↓ refined by
Outcome (messbares Ergebnis)
   ↓ realized by
Capability + Course of Action
   ↓ implemented through
Architecture Elements (Applications, Processes, Data, Technology)
```

### 7.2 Neue EntityTypes

```yaml
EntityType: Vision
  properties:
    - statement: text                 # langfristige, inspirierende Aussage
    - timeHorizon: string             # z.B. "2030+"
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
    - refinedBy: Goal[] (Hierarchie)
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
    - statement: text                 # die Anforderung selbst, prägnant formuliert
    - rationale: text                 # warum existiert die Anforderung
    - type: functional | non-functional | constraint | assumption
    - category: business | data | application | technology | security | compliance | usability | performance | cross-cutting
    - priority: must | should | could | wont (MoSCoW)
    - status: identified | analyzed | approved | allocated | realized | verified | rejected | deferred | retired
    - source: text                    # woher stammt die Anforderung
    - sourceType: stakeholder | regulation | contract | workshop | audit-finding | strategy | other
    - acceptanceCriteria: text        # Pflicht für status >= 'approved'
    - verificationMethod: test | inspection | demonstration | analysis | none
    - verificationStatus: not-verified | verified | failed
    - version: integer                # neue Version bei jeder semantischen Änderung
  relations:
    # Quellen und Begründung (aufwärts)
    - raisedBy: Stakeholder[]         # wer hat die Anforderung gestellt
    - addresses: Concern[]            # welche Stakeholder-Sorge wird adressiert
    - derivedFromGoal: Goal[]         # strategische Begründung
    - derivedFromCompliance: ComplianceRequirement[]   # regulatorische Begründung
    - derivedFromPrinciple: ArchitecturePrinciple[]    # Prinzip-Bezug
    
    # Realisierung (abwärts)
    - realizedBy: Entity[] | Relation[]   # welche Architektur-Elemente erfüllen sie
    - implementedInPlateau: Plateau[]     # in welchem Plateau muss erfüllt sein
    - allocatedTo: WorkPackage[]          # welches WorkPackage liefert die Erfüllung
    - referencesStandard: ArchitectureStandard[]  # Standard erfüllt die Anforderung
    - documentedInADR: ADR[]              # ADR begründet/erfüllt die Anforderung
    
    # Beziehungen zu anderen Anforderungen
    - refines: Requirement              # verfeinert eine grobe Anforderung
    - refinedBy: Requirement[]          # rückreferenz für Hierarchie
    - dependsOn: Requirement[]          # setzt andere voraus
    - conflictsWith: Requirement[]      # widerspricht anderen
    - supersedes: Requirement           # ersetzt eine alte Version
    
    # Risiko-Verbindungen
    - mitigatesRisk: Risk[]             # Anforderung reduziert Risiko
    - riskIfNotMet: Risk                # was passiert bei Nicht-Erfüllung

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

**Stereotypen für unterschiedliche Anforderungs-Ebenen**:

Das EA-Repository fokussiert auf **Architecture Requirements** – fachlich-architektonische Anforderungen, die mehrere Systeme oder die Landschaft als Ganzes betreffen. **System Requirements** (anforderungen an konkret zu bauende Systeme) gehören klassischerweise in Tools wie Jira, Doors, Polarion oder Jama. Pragmatisch werden sie aber im Repository erlaubt – als Stereotype:

```yaml
Stereotype: ArchitectureRequirement (Default)
  basedOn: Requirement
  intent: "Anforderung an die Architektur, system-übergreifend"

Stereotype: SystemRequirement
  basedOn: Requirement
  intent: "Anforderung an ein konkretes System, oft Resultat einer Architecture Requirement"
  additionalProperties:
    - targetSystem: ApplicationComponent
    - externalReference: string  # Verweis ins eigentliche System-Requirements-Tool

Stereotype: QualityRequirement
  basedOn: Requirement
  intent: "Nicht-funktionale Anforderung mit Bezug zum Qualitätsbaum (Arc42 Kapitel 10)"
  additionalProperties:
    - qualityCategory: performance | security | usability | maintainability | reliability | scalability
    - measurableTarget: string  # konkrete Zielgröße, z.B. "p95 response < 200ms"

Stereotype: ComplianceRequirement (existiert bereits, siehe §17/§20)
  intent: "Externe regulatorische Anforderung – wird oft Quelle für ArchitectureRequirements"
```

Die **Empfehlung**: SystemRequirements werden im EA-Repository nur dann modelliert, wenn sie architektonisch relevant sind (Cross-System-Constraints, Compliance-Nachweise, Trace-Pflichten). Detail-Anforderungen für ein einzelnes System bleiben in den jeweiligen System-Tools, mit Verlinkung über `externalReference`.

### 7.3 Stakeholder-Concern-Mapping

Die zentrale Praxis aus ISO 42010: **Viewpoints werden aus Stakeholder-Concerns abgeleitet**. Im Repository:

```yaml
View (bereits existiert) erhält:
  - addressesConcerns: Concern[]
  - forStakeholders: Stakeholder[]
```

Damit lässt sich prüfen: Welche Stakeholder-Concerns sind nicht durch mindestens eine View adressiert?

### 7.4 Glossar als First-Class-Konzept

Ein oft vergessenes Element: konsistente Terminologie über das gesamte Repository.

```yaml
EntityType: GlossaryTerm
  properties:
    - term: string
    - definition: text
    - domain: Domain                  # fachlicher Kontext
    - aliases: string[]               # Synonyme
    - abbreviations: string[]
    - status: active | deprecated
    - source: string                  # Quelle der Definition
  relations:
    - relatedTerms: GlossaryTerm[]
    - supersedes: GlossaryTerm
    - usedIn: Entity[]                # wo wird der Begriff verwendet
```

Glossare sind besonders wertvoll bei:
- Fachbereich-IT-Kommunikation (gemeinsame Sprache)
- Cross-Domain-Projekten (Customer-Begriff in Sales vs. Support)
- Compliance-Audits (Begriffs-Nachvollziehbarkeit)

---

← [Kern-Entitätstypen](06-kern-entitaetstypen.md) · [🏠 Übersicht](../README.md) · [Organisation, Rollen & Personen](08-organisation-rollen-personen.md) →

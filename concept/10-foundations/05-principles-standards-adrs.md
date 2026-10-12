## 5. Architekturprinzipien, Standards und Entscheidungen

Prinzipien, Standards und ADRs sind das **normative Rückgrat** des Repositorys: sie legen fest, *wie* Architektur gestaltet werden soll, und dokumentieren, *warum* welche Entscheidungen getroffen wurden. Dieses Kapitel präzisiert die drei Konzepte, ihre Beziehungen und das Compliance-Modell.

### 5.1 Abgrenzung Prinzip – Standard – Guideline

Die drei Begriffe werden oft synonym verwendet, sind aber konzeptionell klar unterscheidbar.

| Aspekt | Principle (Prinzip) | Standard | Guideline |
|---|---|---|---|
| Antwort auf | Warum? Wohin? | Was? Wie? | Wie am besten? |
| Abstraktionsgrad | hoch | konkret | konkret |
| Technologie-Bezug | agnostisch | meist spezifisch | meist spezifisch |
| Lebensdauer | Jahre bis Jahrzehnte | Monate bis Jahre | Monate bis Jahre |
| Verbindlichkeit | Leitaussage | verbindlich | empfohlen |
| Prüfbarkeit | nur qualitativ | messbar / prüfbar | prüfbar |
| Beispiel | "Cloud-First" | "AWS als primärer Cloud-Provider" | "CloudFormation bevorzugen gegenüber CDK" |

**Die Hierarchie**: Prinzipien sind Eltern von Standards; Standards können Guidelines haben. Ein Prinzip "Cloud-First" manifestiert sich in mehreren Standards (Cloud-Provider, Container-Orchestrator, IaC-Tool). Ändert sich der Cloud-Provider, ändert sich der Standard, aber das Prinzip bleibt.

### 5.2 EntityType: ArchitecturePrinciple

Orientiert am TOGAF-Standard-Template:

```yaml
EntityType: ArchitecturePrinciple
  properties:
    - name: string                    # Kurzer Titel, z.B. "Buy before Build"
    - statement: text                 # Das Prinzip selbst, prägnant formuliert
    - rationale: text                 # Warum gilt dieses Prinzip?
    - implications: text              # Was folgt daraus praktisch?
    - category: enum[business, data, application, technology, security, cross-cutting]
    - status: proposed | approved | deprecated | superseded
    - approvedBy: Role | OrganizationUnit
    - approvedDate: date
    - reviewDate: date                # wann zu überprüfen
  relations:
    - refinedByStandards: ArchitectureStandard[]
    - derivedFrom: Driver | Goal      # woher kommt das Prinzip
    - supersededBy: ArchitecturePrinciple  # bei Ablösung
```

### 5.3 EntityType: ArchitectureStandard

```yaml
EntityType: ArchitectureStandard
  properties:
    - name: string
    - description: text
    - type: technology | integration | data | security | process | documentation
    - scope: enum[global, domain-specific, project-specific]
    - mandatoryLevel: enum[must, should, may]  # RFC-2119-Stil
    - status: draft | active | deprecated | superseded
    - version: string                 # Standards haben Versionen!
    - effectiveFrom, effectiveUntil: date
    - references: url[]               # externe Standards (ISO, RFC, …)
  relations:
    - implementsPrinciple: ArchitecturePrinciple[]
    - appliesTo: EntityType[]         # welche Entity-Typen sind betroffen
    - preferredTechnology: SBB | PhysicalTechnologyComponent
    - acceptedAlternatives: SBB[]
    - relatedStandards: ArchitectureStandard[]
    - relatedGuidelines: Guideline[]
    - supersededBy: ArchitectureStandard
```

Der wichtige Unterschied zu Prinzipien: Standards haben eine **Version** und einen **mandatoryLevel** (muss/soll/kann) – das macht sie prüfbar und governance-fähig.

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

Guidelines sind explizit unverbindlich. Ihre Existenz im Metamodell verhindert, dass Teams Empfehlungen als Standards modellieren (was zu falscher Verbindlichkeit führen würde).

### 5.5 Compliance-Modell: Waiver, Deviation, Violation

Die Realität: Verstöße gegen Standards sind häufig. Das Metamodell kennt drei Zustände:

**Deviation** – festgestellte Abweichung, noch nicht bewertet
**Waiver** – genehmigte Ausnahme, zeitlich begrenzt, mit Auflagen
**Violation** – nicht-genehmigte Abweichung, muss behoben werden

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
    - affects: Entity | Relation      # Abweichung kann Entity ODER Relation sein
    - discoveredBy: Role
    - resolvedBy: Project | ADR
    - relatedWaiver: ArchitectureWaiver

EntityType: ArchitectureWaiver
  properties:
    - reason: text                    # Warum wird abgewichen?
    - grantedDate: date
    - expiresDate: date               # Waivers sind IMMER befristet
    - conditions: text                # unter welchen Auflagen
    - renewable: boolean
  relations:
    - waivesStandard: ArchitectureStandard
    - grantedFor: Entity[] | Relation[]  # Waiver kann Entitäten oder Relationen abdecken
    - grantedBy: Role | ARB
    - documentedInADR: ADR
    - resultsFromDeviation: ComplianceDeviation
```

Zentrale Eigenschaft: **Waivers haben immer ein Ablaufdatum**. Unbefristete Waivers sind Architektur-Schuld, die unsichtbar wird. Das Metamodell erzwingt den Ablauf.

### 5.6 Architecture Decision Records (ADRs)

ADRs sind **eigene Entitäten**, die auf andere Entitäten referenzieren – nicht bloß Textfelder an Applications. Das ist der Kern der Arc42-ADR-Kultur.

```yaml
EntityType: ADR (ArchitectureDecisionRecord)
  properties:
    - id: string                      # z.B. "ADR-042"
    - title: string
    - status: proposed | accepted | deprecated | superseded | rejected
    - date: date
    - context: text                   # Welches Problem, welche Rahmenbedingungen
    - decision: text                  # Was wurde entschieden
    - alternatives: Alternative[]     # Was wurde erwogen und abgelehnt
    - consequences: {positive: text, negative: text, neutral: text}
    - origin: intentional | emergent  # siehe [§19.1 (Agile Skalierung)](../60-integrations/19-agile-skalierung.md) (SAFe)
    - decisionLevel: enterprise | domain | system | component
  relations:
    - affects: Entity[] | Relation[]  # n:m – kann Entitäten UND Relationen betreffen
    - decidedBy: Role | ARB
    - participants: Role[]
    - supersedes: ADR                 # falls diese ADR eine frühere ablöst
    - supersededBy: ADR               # Rückreferenz
    - relatesTo: ADR[]                # lose Verbindungen
    - basedOnPrinciple: ArchitecturePrinciple[]
    - referencesStandard: ArchitectureStandard[]
    - documentsWaiver: ArchitectureWaiver  # wenn die Entscheidung eine Waiver begründet

Alternative:
  - name: string
  - description: text
  - prosAndCons: text
  - reasonForRejection: text
```

### 5.7 ADRs pro Entität und Relation – die Cross-Reference-Mächtigkeit

Die Beziehung `ADR.affects` ist n:m und akzeptiert sowohl **Entitäten als auch Relationen** als Ziel (siehe Reifikation in [§2 (Meta-Metamodell)](../00-overview/02-meta-metamodell.md)). Das ist der Kern der Navigierbarkeit:

- Eine **Application** hat über Zeit mehrere ADRs angesammelt (Technologie-Wahl, Architektur-Muster, Datenspeicherung, …)
- Ein **Standard** wird durch ein ADR etabliert (die Entscheidung, diesen Standard einzuführen, ist selbst ein ADR)
- Ein **Plateau** wird durch ADRs geprägt (strategische Richtungs-Entscheidungen)
- Ein **Interface** (als Entity) kann ein ADR haben (synchron vs. asynchron, Protokoll-Wahl)
- Eine **Relation zwischen Entities** kann ein ADR haben, wenn die Entscheidung die Verbindung betrifft, nicht die Systeme – z.B. "Shop kommuniziert mit ERP künftig asynchron statt synchron" affektiert die Relation `Shop -[uses]-> ERP`, nicht Shop oder ERP selbst

Sichten, die daraus entstehen:
- **ADR-Historie einer Entität oder Relation**: alle Entscheidungen, die dieses Element geprägt haben, chronologisch
- **Impact einer ADR**: alle Entitäten und Relationen, die von dieser Entscheidung betroffen sind
- **Decision Trail**: Superseding-Kette zeigt, wie sich eine Entscheidung über Zeit entwickelt hat
- **Orphan ADRs**: ADRs, die keine Entitäten/Relationen mehr referenzieren (weil alle Betroffenen retired sind – Aufräum-Kandidaten)

### 5.8 Die Navigationskette

Die komplette normative Kette im Repository:

```
Goal / Driver
    ↓ motiviert
ArchitecturePrinciple
    ↓ verfeinert sich zu
ArchitectureStandard
    ↓ wird etabliert/geändert durch
ADR
    ↓ affects
Entity (Application, Interface, Technology, …)
    ↓ kann abweichen via
ComplianceDeviation
    ↓ wird genehmigt als
ArchitectureWaiver
    ↓ wird dokumentiert in
ADR
```

Diese Kette ist bidirektional navigierbar. Von jeder Entität aus kann man die normativen Wurzeln erfragen: "Welche Standards/Prinzipien gelten für diese Application, und welche ADRs haben diese Entscheidungen geformt?"

### 5.9 Compliance-Sichten

Mehrere Sichten werden durch dieses Modell automatisch möglich:

- **Standards-Compliance-Report**: pro Standard, wie viele Entitäten konform/abweichend sind
- **Active Waivers**: alle gültigen Waivers, sortiert nach Ablaufdatum (Erneuerungs-Backlog)
- **Expired Waivers**: Waivers, deren Ablaufdatum überschritten ist (Sofort-Handlungsbedarf)
- **Principle Impact**: welche Standards, ADRs und Entitäten hängen von welchem Prinzip ab
- **Decision Lineage**: vollständige ADR-Kette zu einer Entität
- **Governance Debt**: Summe offener Deviations × Severity (Technical-Debt-Analog)

### 5.10 Constraints

```yaml
constraint: waiver-has-expiry
  appliesTo: ArchitectureWaiver
  rule: "entity.expiresDate != null AND entity.expiresDate > entity.grantedDate"
  severity: error
  message: "Waiver ohne Ablaufdatum ist unsichtbare Architektur-Schuld"

constraint: active-standard-has-principle
  appliesTo: ArchitectureStandard
  when: "entity.status == 'active'"
  rule: "entity.implementsPrinciple.length >= 1"
  severity: warning
  message: "Standard ohne Prinzip-Bezug – warum existiert dieser Standard?"

constraint: waiver-documented-in-adr
  appliesTo: ArchitectureWaiver
  rule: "entity.documentedInADR != null"
  severity: warning
  message: "Waiver ohne ADR – Begründungs-Historie fehlt"

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

### 5.11 Workflow-Integration

Das Modell unterstützt folgende Workflows direkt:

**Neue Entität entsteht**: Tool prüft, welche Standards für diesen EntityType gelten, markiert potenzielle Deviations.

**Standard wird geändert**: Impact-Analyse zeigt alle betroffenen Entitäten; automatisch generierte Deviation-Kandidaten für Entitäten, die jetzt abweichen würden.

**Waiver läuft ab**: Tool benachrichtigt Owner und ARB rechtzeitig; wenn nicht verlängert, wird Deviation automatisch zu Violation.

**ADR wird erstellt**: Verknüpfung zu betroffenen Entitäten ist Pflicht (sonst warum die ADR?). Prüfung, ob bestehende ADRs überschrieben werden müssen.

**Architecture Review Board** (aus [§18.9 (PPM-Integration)](../60-integrations/18-ppm-integration.md)): ARB-Entscheidungen resultieren in ADRs; Waiver-Genehmigungen laufen über ADRs; Principle-/Standard-Änderungen werden durch ADRs dokumentiert.

### 5.12 Abgrenzung zu Compliance-Requirements (extern)

Wichtig: Die hier beschriebenen Standards sind **interne Architektur-Standards**. Externe regulatorische Compliance-Requirements (DSGVO, SOX, ISO 27001, DORA) werden separat als `ComplianceRequirement` modelliert (siehe PPM-Kapitel [§18 (PPM-Integration)](../60-integrations/18-ppm-integration.md)). Der Unterschied:

- `ArchitectureStandard`: von der Organisation selbst gesetzte technische/architektonische Normen
- `ComplianceRequirement`: von außen vorgegebene regulatorische Anforderungen

Beide können sich überschneiden: Ein `ComplianceRequirement` (z.B. "Daten müssen verschlüsselt sein") manifestiert sich oft als interner `ArchitectureStandard` (z.B. "TLS 1.3 ist Pflicht"). Die Relation `ArchitectureStandard.derivedFrom: ComplianceRequirement` macht das explizit.

---

← [Enterprise Continuum & TRM](04-enterprise-continuum-trm.md) · [🏠 Übersicht](../README.md) · [Kern-Entitätstypen](../20-entities/06-kern-entitaetstypen.md) →

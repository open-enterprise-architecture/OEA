## 9. Prozess-Architektur

Prozess-Modellierung im EA-Kontext ist **nicht** Prozess-Autoring. Dafür existieren spezialisierte BPM-Tools (Camunda, Signavio, BIC, ARIS). Dieses Tool modelliert Prozesse **abstrakt und notations-neutral**, mit BPMN und anderen Notationen als Import-/Export-Formate.

### 9.1 Modellierungs-Philosophie

**Das Metamodell kennt abstrakte Prozess-Konzepte**, nicht BPMN-Spezifika. Vorteile:
- Portabilität zu BPMN, EPK, Value Stream Maps, Future Standards
- Keine Kopplung an BPMN-Versions-Wechsel
- Fokus auf architektonisch relevante Eigenschaften (Ownership, Cross-References, KPIs)
- Detail-Prozess-Modellierung bleibt im Fach-BPM-Tool

**Bidirektionale Integration mit BPM-Tools**: Prozess-Modelle werden aus externen BPM-Tools importiert, im Repository referenziert und mit EA-Entitäten verknüpft. Änderungen am Prozess-Ablauf passieren im BPM-Tool, architektonische Verknüpfungen in OEA.

### 9.2 Prozess-Hierarchie (Process Landscape)

Die Process Landscape strukturiert alle Prozesse einer Organisation hierarchisch:

```yaml
EntityType: ValueChain
  properties:
    - name, description
    - type: core | supporting | management  # nach Porter
    - industry: string                       # z.B. "retail", "banking"
  relations:
    - contains: ProcessGroup[]
    - supports: Capability[]

EntityType: ProcessGroup (L2 / L3)
  properties:
    - name, description
    - level: integer                         # Hierarchie-Ebene (2–5)
    - pcfReference: string                   # z.B. APQC-PCF-Code
  relations:
    - parentGroup: ProcessGroup
    - contains: ProcessGroup[] | Process[]

EntityType: Process (L4)
  properties:
    - name, description
    - type: core | supporting | management
    - automationLevel: manual | partially-automated | fully-automated
    - triggers: text                         # was startet den Prozess
    - outcomes: text                         # was entsteht am Ende
    - frequency: string                      # z.B. "monthly", "on-demand"
    - complexity: low | medium | high
  relations:
    - belongsTo: ProcessGroup
    - decomposedInto: Activity[]
    - ownedBy: Role (Process Owner!)
    - managedBy: Role (Process Manager)
    - realizes: Capability[]
    - usedBy: BusinessFunction[]
    - measuredBy: KPI[]
    - externalDefinition: reference           # Link zu BPMN in BPM-Tool

EntityType: Activity (L5)
  properties:
    - name, description
    - type: task | decision | event | subprocess
    - automationPotential: low | medium | high
  relations:
    - belongsTo: Process
    - performedBy: Role[]
    - supportedByApplication: ApplicationService[]
    - readsData: DataEntity[]
    - writesData: DataEntity[]
    - triggers: InformationFlow[]
```

### 9.3 Process Classification Frameworks (PCF)

Prozess-Referenzmodelle werden als Continuum-Artefakte aus [§4 (Enterprise Continuum)](../10-foundations/04-enterprise-continuum-trm.md) modelliert:

```yaml
ReferenceArchitecture "APQC-PCF-v7.4"
  continuumLevel: industry
  contains: ProcessGroup-Blueprints (alle APQC-Kategorien)

ReferenceArchitecture "eTOM-v23"
  continuumLevel: industry
  industry: telco
  contains: ProcessGroup-Blueprints (Telco-spezifisch)
```

Organisationen können ihre eigenen Prozesse gegen diese Referenz-Taxonomien mappen – für Benchmarking und strukturelle Konsistenz:

```yaml
Process "Rechnungsstellung" erhält:
  - mapsToPCF: "APQC 9.2.1 - Invoice Customer"
```

### 9.4 Prozess-Performance (PPIs / KPIs)

Messgrößen als eigenständige Entität:

```yaml
EntityType: KPI
  properties:
    - name, description
    - formula: text
    - unit: string
    - targetValue, currentValue: number
    - measurementFrequency: string
    - trend: improving | stable | declining
    - direction: higher-better | lower-better | target-best
  relations:
    - measures: Process | Capability | BusinessService | Goal
    - owner: Role
    - dataSource: ApplicationComponent
```

Typische Prozess-KPIs:
- Zyklus-Zeit, Durchlaufzeit
- Fehlerrate, First-Time-Right-Quote
- Kosten pro Prozess-Instanz
- Kundenzufriedenheit (NPS, CES)

### 9.5 Cross-Layer-Verlinkungen (CRUD-Matrix)

Die klassische EA-Analyse: Welche Prozesse tun was mit welchen Daten?

```yaml
Relation: ProcessDataAccess
  - process: Process | Activity
  - dataEntity: DataEntity
  - accessType: create | read | update | delete
  - frequency: string
```

Daraus lässt sich automatisch eine **CRUD-Matrix** generieren: Prozesse × Datenobjekte mit C/R/U/D-Kennzeichnung. Zeigt u.a. **Datenhoheit** (wer erzeugt ein DataEntity?), **Read-only-Prozesse** und **potenzielle Konflikte** (zwei Prozesse, die dasselbe DataEntity unabhängig updaten).

### 9.6 BPMN-Integration

BPMN wird über einen Adapter eingebunden:

**Import**: BPMN-XML-Dateien werden geparst, Elemente (Pool, Lane, Task, Flow, DataObject, MessageFlow) werden auf abstrakte Prozess-Entitäten gemappt. Die BPMN-Datei wird als externalDefinition referenziert, nicht dupliziert.

**Export**: Aus dem abstrakten Prozess-Modell kann BPMN-XML generiert werden (bidirektional, wobei BPMN-spezifische Details wie Gateway-Bedingungen im BPM-Tool gepflegt bleiben).

**Annotation**: BPMN-Elemente werden mit EA-Referenzen annotiert (siehe [§6.3 (Kern-Entitätstypen)](06-kern-entitaetstypen.md) BPMN-Integration-Tabelle).

```yaml
EntityType: ProcessModel (eigenständige Entität)
  properties:
    - notation: BPMN-2.0 | EPK | VSM | custom
    - format: XML | JSON | proprietary
    - sourceFile: path | url
    - lastImported: datetime
  relations:
    - representsProcess: Process
    - maintainedIn: ExternalTool     # z.B. "Camunda Modeler"
```

### 9.7 Value Stream Mapping

Für produkt-orientierte Organisationen (Lean, SAFe) ist der **Value Stream** oft die zentrale Sicht – orthogonal zur Prozess-Hierarchie:

```yaml
EntityType: ValueStream
  properties:
    - name, description
    - triggerEvent: text             # was löst den Value Stream aus
    - valueProposition: text         # welcher Kundennutzen
    - totalLeadTime: duration
    - totalProcessTime: duration
  relations:
    - steps: ValueStreamStep[]       # sequenzielle Schritte
    - deliversToCustomer: Stakeholder
    - supportedBy: Process[]         # Prozesse, die Teile umsetzen
    - realizes: Capability[]

EntityType: ValueStreamStep
  properties:
    - name, description
    - sequence: integer
    - processTime, leadTime: duration
    - valueAdding: boolean           # WERTSCHÖPFEND ja/nein
  relations:
    - performedIn: Process | OrganizationUnit
    - handsOffTo: ValueStreamStep
```

**Wichtige Analyse**: Waste Identification – nicht-wertschöpfende Schritte (`valueAdding: false`) sind Optimierungs-Kandidaten. Die klassische Lean-Sicht ins OEA geholt.

### 9.8 Prozess-Ownership (vertieft aus [§8 (Organisation & Rollen)](08-organisation-rollen-personen.md))

Prozess-Ownership ist mehrschichtig:

**Process Owner** (strategisch):
- Verantwortet den Prozess über seinen Lebenszyklus
- Entscheidet über Prozess-Design und Änderungen
- Typisch: Linien-Management

**Process Manager** (operativ):
- Steuert laufende Prozess-Ausführung
- Verantwortet KPI-Erreichung
- Typisch: Team-Leader

**Process Architect** (methodisch):
- Standardisierung, Cross-Process-Konsistenz
- Teil der EA/BPM-Funktion

**Process Participant**:
- Führt Aktivitäten aus
- Intern oder extern (Kunde, Partner)

Alle vier als Relations am Process modellieren, mit eigenen Rollen:

```yaml
Process erhält:
  - processOwner: Role
  - processManager: Role
  - processArchitect: Role
  - participants: Role[]
```

### 9.9 Empfohlener Tool-Scope für Prozess-Modellierung

Das Tool sollte auf folgender Ebene Wertschöpfung bringen:

**Was das Tool tun sollte**:
- Prozess-Hierarchie pflegen (L1–L5)
- Cross-Layer-Verlinkungen (Process ↔ Capability, Application, Data, Role)
- CRUD-Matrix generieren
- KPI-Zuordnung und Tracking
- PCF-Mapping für Benchmarking
- BPMN-Import/-Export via Adapter
- Prozess-Ownership und RACI

**Was das Tool NICHT tun sollte**:
- BPMN-Diagramm-Editor mit Swimlanes, Gateways, Event-Subprozessen
- Process Simulation / Execution Engine
- Formular-Generierung aus Prozessen
- Workflow-Execution

Wer volles Prozess-Autoring braucht, nutzt Camunda/Signavio parallel und verlinkt.

### 9.10 Constraints

```yaml
constraint: process-has-owner
  appliesTo: Process
  rule: "entity.processOwner != null"
  severity: error
  message: "Prozess ohne Process Owner ist nicht governance-fähig"

constraint: process-has-kpi
  appliesTo: Process
  when: "entity.type == 'core'"
  rule: "entity.measuredBy.length >= 1"
  severity: warning
  message: "Kernprozess ohne KPIs nicht messbar"

constraint: activity-has-performer
  appliesTo: Activity
  rule: "entity.performedBy.length >= 1 OR entity.supportedByApplication.length >= 1"
  severity: warning

constraint: data-write-has-owner
  appliesTo: ProcessDataAccess
  when: "entity.accessType == 'create' OR entity.accessType == 'update'"
  rule: "process.processOwner has dataOwnership on dataEntity OR explicit delegation"
  severity: warning
  message: "Prozess schreibt Daten ohne klare Datenhoheit"
```

---

← [Organisation, Rollen & Personen](08-organisation-rollen-personen.md) · [🏠 Übersicht](../README.md) · [Cross-Cutting-Konzepte](10-cross-cutting.md) →

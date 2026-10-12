## 20. GRC-, DSGVO- und ISMS-Integration

Regulatorische Governance und Informationssicherheit sind zwei Disziplinen, die stark auf EA-Daten angewiesen sind – beide brauchen aktuelle, vollständige Inventare von Applications, Technologies, Data Components, Interfaces und Prozessen. Ohne integrierte Anbindung führt das unweigerlich zu parallelen Shadow-Inventuren, die mit der Zeit veralten. Dieses Kapitel beschreibt die Schnittstellen zu **GRC-Tools** (Governance, Risk, Compliance), **DSGVO-/Datenschutz-Tools** und **ISMS-Tools** (Information Security Management Systems).

### 20.1 Abgrenzung der drei Disziplinen

| Aspekt | GRC / Compliance | DSGVO / Datenschutz | ISMS |
|---|---|---|---|
| Primäre Frage | "Welche externen Anforderungen erfüllen wir?" | "Welche personenbezogenen Daten verarbeiten wir wie?" | "Welche Risiken bedrohen unsere Assets?" |
| Zielgruppe | Compliance Officer, Legal, Auditor | Datenschutzbeauftragte:r (DPO) | Information Security Officer (ISO/CISO) |
| Kernoutputs | Evidence-Packages, Audit-Reports, Regulatory Reports | Verarbeitungsverzeichnis, DSFA, Auskunftsantworten | Risikoregister, SoA, Maßnahmenplan |
| Denken in | Requirements → Controls → Evidence → Findings | Zweck → Datenkategorie → Rechtsgrundlage → Betroffene | Asset → Threat → Vulnerability → Risk → Treatment |
| Referenzrahmen | ISO 27001, NIST CSF, SOX, DORA, NIS2 | DSGVO, revDSG, LGPD, CCPA | ISO 27001/27005, BSI Grundschutz, TISAX |
| Tool-Beispiele | ServiceNow GRC, SAP GRC, MetricStream, Archer | OneTrust, TrustArc, Smart Global Governance | Verinice, HiScout, ibi iRIS, OpenPages |

Die Disziplinen überlappen sich fachlich, werden aber meist von unterschiedlichen Tools unterstützt. Manche Anbieter (ServiceNow, Archer, OpenPages) decken alle drei ab, viele Organisationen kombinieren aber spezialisierte Lösungen.

### 20.2 Rolle des EA-Repositorys

EA ist der **strukturelle Datenlieferant** für alle drei Disziplinen. Was EA liefert:

- Asset-Inventory (Applications, Technologies, Data Components)
- Klassifizierungen (Schutzbedarf, Datenkategorien)
- Datenflusskarten (welche Daten fließen wohin)
- Prozess-zu-System-Verknüpfung
- Dienstleister-Beziehungen (aus Contracts)
- Lebenszyklus- und Versionsinformationen

Was EA **nicht übernimmt**:

- Risikoregister mit quantitativer Bewertung und Risk Appetite
- Control-Wirksamkeits-Tests und Evidence-Collection
- Betroffenenanfragen-Workflow (Auskunft, Löschung, Portabilität)
- Datenschutz-Folgenabschätzungen (DSFA/DPIA) als formaler Workflow
- Statement of Applicability (SoA) als ISO-27001-Artefakt
- Audit-Vorbereitung und -Durchführung

Diese Funktionen bleiben in den spezialisierten Tools. Das OEA stellt die Asset-Basis zur Verfügung, auf der sie arbeiten.

### 20.3 Metamodell-Erweiterungen für DSGVO

Für DSGVO Artikel 30 (Verarbeitungsverzeichnis) werden die bestehenden EntityTypes ergänzt.

**DataEntity** erhält optional:

```yaml
DataEntity erweitert um:
  - isPersonalData: boolean
  - personalDataCategory: enum[standard, special-art-9, criminal-art-10]
  - dataSubjects: enum[employees, customers, prospects, suppliers, minors, patients, other][]
  - processingPurposes: ProcessingPurpose[]
  - retentionPeriod: duration
  - retentionJustification: text
  - legalBasis: enum[consent, contract, legal-obligation, vital-interests, public-task, legitimate-interest]
  - legalBasisDetails: text
```

**ApplicationComponent** und **Interface** erhalten:

```yaml
ApplicationComponent / Interface erweitert um:
  - processingLocations: Location[]       # wo wird verarbeitet
  - dataTransferOutsideRegion: boolean    # Drittland-Transfer?
  - transferMechanism: enum[adequacy-decision, standard-contractual-clauses, binding-corporate-rules, derogation, none]
  - transferDetails: text
```

**Neue EntityTypes**:

```yaml
EntityType: ProcessingPurpose
  properties:
    - name
    - description
    - legalBasis: enum[...]
    - retentionPeriod: duration
  relations:
    - appliesTo: DataEntity[]
    - realizedBy: BusinessProcess[]

EntityType: DataProcessor (Auftragsverarbeiter)
  basedOn: Vendor / OrganizationUnit (external)
  properties:
    - dpaSignedDate: date             # Auftragsverarbeitungsvertrag
    - dpaExpiresDate: date
    - subprocessorsAllowed: boolean
    - auditRights: text
    - certifications: string[]        # ISO 27001, SOC 2, etc.
  relations:
    - processesDataFor: OrganizationUnit
    - processesData: DataEntity[]
    - governedByContract: Contract

EntityType: DataTransfer
  properties:
    - sourceLocation: Location
    - targetLocation: Location
    - transferMechanism: enum[...]
    - frequency: string
    - dataVolume: string
  relations:
    - transfers: DataEntity[]
    - between: Application | DataProcessor
    - via: Interface
```

### 20.4 Metamodell-Erweiterungen für ISMS (ISO 27001/27005)

Das ISMS arbeitet mit einem etablierten Vokabular, das in OEA als Referenz-Schema verfügbar sein sollte:

**Primary vs. Supporting Assets**: Die ISO-27005-Terminologie unterscheidet:

- **Primary Assets**: das, was geschützt werden soll (Geschäftsprozesse, Informationen)
- **Supporting Assets**: das, worauf Primary Assets angewiesen sind (Systeme, Technologien, Personen, Standorte, Lieferanten)

Das OEA liefert beides ohne eigenes Asset-Konzept. BusinessProcess und InformationObject sind Primary Assets; ApplicationComponent, TechnologyComponent und Location sind Supporting Assets. Die ISO-27005-Kategorien werden als Stereotype oder Classification-Property an die EntityTypes angehängt:

```yaml
Entity erweitert um (optional):
  - isoAssetCategory: primary | supporting
  - iso27005Type: process | information | software | hardware | network | personnel | site | organization | service
```

**Bedrohungen und Schwachstellen** bleiben im ISMS-Tool gepflegt – aber OEA kann Referenzen dorthin halten:

```yaml
Entity erweitert um:
  - knownThreats: ThreatReference[]       # Referenz ins ISMS-Tool
  - openVulnerabilities: VulnerabilityReference[]
  - riskScore: number                      # aggregiert vom ISMS
  - lastRiskAssessment: date
```

### 20.5 Mastership pro Konzept

| Konzept | EA-Master | ISMS-Master | DSGVO-Tool-Master |
|---|---|---|---|
| Asset-Existenz und -Struktur | ✓ | | |
| CIA-Schutzbedarf | ✓ (oder shared) | ✓ (oder shared) | |
| Datenkategorie (personenbezogen/nicht) | ✓ | | |
| Zweck der Verarbeitung | | | ✓ |
| Rechtsgrundlage | | | ✓ |
| Rückbehalt / Löschfristen | ✓ | | |
| Threat-Katalog | | ✓ | |
| Risikobewertung (Wahrscheinlichkeit × Auswirkung) | | ✓ | |
| Control-Framework (ISO 27001 Annex A) | als Continuum-Artefakt (§4) | ✓ | |
| Control-Wirksamkeit und Evidence | | ✓ | ✓ (für DSGVO-Controls) |
| Datenschutz-Folgenabschätzung (DSFA) | | | ✓ |
| Betroffenenanfragen | | | ✓ |
| Statement of Applicability | | ✓ | |
| Auftragsverarbeiter-Verträge | Stammdaten (Contract) | | ✓ (für DPA-Pflege) |
| Drittlandtransfers | Struktur (Transfer-Beziehung) | | ✓ (für Rechtfertigung) |

**Pragmatische Regel**: Wenn eine Information strukturell ist (Was existiert? Wie hängt es zusammen?), lebt sie im EA. Wenn sie prozedural ist (Was wurde bewertet? Was wurde nachgewiesen?), lebt sie im spezialisierten Tool.

### 20.6 Integrations-Muster

**EA → GRC/ISMS/DSGVO-Tool (Push, regelmäßig)**:

- Asset-Inventory-Export im definierten Format
- Strukturänderungen als Event-Stream (siehe API-Kapitel)
- Klassifizierungsänderungen als Trigger für Neubewertung

**GRC/ISMS/DSGVO-Tool → EA (Pull oder Push)**:

- Risk-Scores als annotierte Properties
- Offene Findings als Verweise (nicht als volle Daten)
- Control-Framework-Referenzen (als Continuum-Artefakt)
- Regulatorische Scope-Zuordnung (KRITIS, NIS2, DORA)

**Bidirektionale Synchronisation**: Für Entitäten, die in beiden Welten gepflegt werden (z.B. Schutzbedarf), gilt das Property-Mastership-Prinzip aus [§17 (ITSM-Integration)](../60-integrations/17-itsm-integration.md). Property-Level-Mastership pro Entity-Typ konfigurierbar.

### 20.7 Austauschformate

Das Tool sollte mehrere Formate unterstützen, um verschiedene Zielsysteme bedienen zu können:

**Generische Formate**:
- OSCAL (Open Security Controls Assessment Language, NIST) – modernes JSON/YAML-Format für Compliance
- STIX/TAXII – für Threat-Intelligence-Austausch (eher ISMS-Fokus)
- JSON/YAML-basierte Custom-Formate über Adapter

**Tool-spezifische Formate**:
- Verinice: XML-basiertes Austauschformat, Open Source – guter Referenz-Adapter
- ServiceNow GRC: REST-API
- OneTrust: REST-API mit CSV-Import für Asset-Inventare

**DSGVO-spezifisch**:
- Verarbeitungsverzeichnis-Export im Format der jeweiligen Aufsichtsbehörde (variiert nach Land)
- GPDR-konformer Datenexport für Betroffenenauskünfte (strukturiert)

### 20.8 Adapter-Architektur

Analog zur ITSM-/PPM-Integration werden GRC-/ISMS-/DSGVO-Adapter als **Plug-in-Module** realisiert (siehe API-Architektur). Ein Adapter:

- Konsumiert die EA-API für Asset-Abruf
- Übersetzt in Zielsystem-Format
- Pflegt URN-Kreuzreferenzen beidseitig
- Reagiert auf Events (neue/geänderte Assets → Weiterleitung)
- Implementiert bidirektionale Property-Synchronisation dort, wo konfiguriert

**Empfohlene Referenz-Adapter** (in Prioritätsreihenfolge):

1. **Verinice-Adapter** – Open-Source ISMS, ideal als erster Anlaufpunkt für die Community
2. **Generischer CSV/JSON-Export** für Asset-Inventare – erlaubt manuelle Integration in beliebige Tools
3. **OSCAL-basierter Export** – zukunftsfähig, standardisiert
4. **ServiceNow GRC/IRM REST-Adapter** – hohe Enterprise-Relevanz

### 20.9 Property-Level-Autorisierung

Diese Integration zwingt zur Präzisierung der Zugriffskontrolle: Nicht jede:r EA-Nutzer:in darf alle Attribute aller Entitäten sehen. Personenbezogene-Daten-Kategorien und detaillierte Schutzbedarfe sind sensibel.

Das Tool implementiert deshalb **Property-Level-Autorisierung als Pflicht-Feature**:

- Pro Property kann eine Lese- und Schreibe-Berechtigung definiert werden
- Berechtigungen basieren auf Rollen (z.B. "DPO", "ISO", "Architekt")
- Properties ohne explizite Einschränkung sind für alle Lesenden sichtbar
- API-Response filtert automatisch nicht-berechtigte Properties heraus (ohne den Request abzulehnen)
- Audit-Log erfasst, wer wann welche geschützten Properties abgefragt hat

Beispiel-Konfiguration:

```yaml
propertyAuthorization:
  - entity: DataEntity
    property: personalDataCategory
    read: [DPO, ISO, Architekt]
    write: [DPO]
  - entity: DataEntity
    property: retentionPeriod
    read: [DPO, ISO, Architekt, FachOwner]
    write: [DPO]
  - entity: ApplicationComponent
    property: confidentialityProtectionNeed
    read: [ISO, Architekt, AppOwner]
    write: [ISO]
```

Details zur Implementierung siehe [§22 (API-Architektur)](../70-platform/21-api-architektur.md).

### 20.10 Audit-Trail-Anforderungen

GRC, DSGVO und ISMS bringen eine harte Compliance-Anforderung mit: **vollständiger Audit-Trail über Datenänderungen** ist nicht mehr nice-to-have, sondern Pflicht.

Das EA-Repository protokolliert für alle Änderungen:

- Wer (User/Role/API-Key)
- Wann (Zeitstempel)
- Was (Entity/Relation/Property, Alt- und Neuwert)
- Warum (optional: Commit-Message, Change-Reference)
- Wie (UI/API/Sync)

Der Audit-Trail ist:

- **Unveränderbar** (Append-Only)
- **Exportierbar** in standardisierten Formaten (JSON, CSV)
- **Aufbewahrungspflichtig** entsprechend regulatorischer Vorgaben (DSGVO mindestens Speicherdauer der Verarbeitung, ISO 27001 typischerweise 3+ Jahre)
- **Selbst zugriffsgeschützt** – Audit-Logs enthalten sensible Daten und dürfen nicht von jedem eingesehen werden

Technisch: Event-Stream aus dem API-Kapitel wird mit Audit-Flag ergänzt, dauerhaft persistiert, und kann in externe SIEM-Systeme (Splunk, Elastic) gestreamt werden.

### 20.11 Segregation of Duties

GRC-Prinzip: Wer Kontrollen entwirft, darf sie nicht selbst abnehmen. Wer Risiken bewertet, darf sie nicht selbst akzeptieren. Das wirkt sich auf EA-Workflows aus:

- **Standards-Entwurf vs. -Genehmigung**: Architekt schlägt Standard vor, ARB genehmigt (Rollen getrennt)
- **Waiver-Antrag vs. -Erteilung**: Application-Owner beantragt Ausnahme, ISO oder ARB erteilt
- **Schutzbedarfs-Erhebung vs. -Validierung**: FachOwner pflegt Schutzbedarf, ISO validiert stichprobenartig
- **Änderung vs. Review bei kritischen Properties**: Vier-Augen-Prinzip bei Änderungen personenbezogener Klassifikationen

Das Metamodell unterstützt dies durch:

```yaml
Constraint: segregation-of-duties
  appliesTo: ArchitectureWaiver
  rule: "entity.requestedBy != entity.grantedBy"
  severity: error

Constraint: four-eyes-personal-data-changes
  appliesTo: DataEntity (Property-Änderung: personalDataCategory)
  rule: "change requires approval by Role != author"
  severity: error
```

### 20.12 Neue Sichten

- **Verarbeitungsverzeichnis (Art. 30 DSGVO)**: automatisch generiert aus DataEntity mit isPersonalData=true, gruppiert nach ProcessingPurpose, mit allen Pflichtfeldern
- **Drittland-Transfer-Landkarte**: visualisiert DataTransfer-Relationen über Jurisdiktionsgrenzen hinweg, farblich markiert nach Transfer-Mechanismus
- **Schutzbedarfs-Heatmap**: Matrix von Applications × CIA-Werten, farblich kodiert
- **Auftragsverarbeiter-Register**: alle DataProcessor-Entitäten mit DPA-Status, ablaufende Verträge hervorgehoben
- **Asset-Compliance-Coverage**: pro Application/Technology, welche regulatorischen Scopes gelten (KRITIS, NIS2, DORA, PCI-DSS) und ob sie formal im ISMS/GRC erfasst sind
- **Risiko-Hotspots**: Applications mit hohen Risk-Scores aus dem ISMS, kombiniert mit hoher Business-Kritikalität
- **Offene-Findings-Durchstich**: Findings aus GRC-Tool verbunden mit EA-Entity, zeigt Remediation-Fortschritt

### 20.13 Constraints

```yaml
constraint: personal-data-has-legal-basis
  appliesTo: DataEntity
  when: "entity.isPersonalData == true"
  rule: "entity.legalBasis != null AND entity.processingPurposes.length >= 1"
  severity: error
  message: "Personenbezogene Daten ohne Rechtsgrundlage oder Zweck sind unzulässig"

constraint: personal-data-has-retention
  appliesTo: DataEntity
  when: "entity.isPersonalData == true"
  rule: "entity.retentionPeriod != null"
  severity: error
  message: "Personenbezogene Daten benötigen definierte Aufbewahrungsfrist"

constraint: third-country-transfer-justified
  appliesTo: DataTransfer
  when: "entity.sourceLocation.region != entity.targetLocation.region AND entity.targetLocation.jurisdictionalRegion NOT IN adequacyDecisionRegions"
  rule: "entity.transferMechanism != 'none'"
  severity: error
  message: "Drittlandtransfer ohne Rechtfertigungsmechanismus"

constraint: data-processor-has-dpa
  appliesTo: DataProcessor
  when: "entity.processesData.length >= 1"
  rule: "entity.dpaSignedDate != null AND entity.dpaExpiresDate > today()"
  severity: error
  message: "Auftragsverarbeiter ohne gültigen AVV"

constraint: protection-need-reviewed-annually
  appliesTo: ProtectionNeed
  rule: "entity.nextReview >= today() - 1 year"
  severity: warning
  message: "Schutzbedarf sollte jährlich überprüft werden"

constraint: high-protection-application-has-iso-controls
  appliesTo: ApplicationComponent
  when: "entity.protectionNeed.confidentiality in [high, very-high]"
  rule: "entity.assignedControls references ISO-27001-Annex-A-Controls"
  severity: warning
```

### 20.14 Praxisprinzipien

- **EA liefert Strukturdaten, nicht Bewertungen**. Risikobewertungen, Control-Wirksamkeit, DSFA bleiben in den spezialisierten Tools.
- **Keine Doppelpflege**. Wenn ein Schutzbedarf im ISMS gepflegt wird, wird er dort gepflegt und ins EA synchronisiert – oder umgekehrt, aber nicht beides parallel.
- **Audit-Trail von Anfang an**. Später nachzurüsten ist aufwändig und compliance-problematisch.
- **Property-Level-Autorisierung ist Grundlage**, nicht Zusatzfeature. Personenbezogene Daten und Schutzbedarfe sind sensibel und dürfen nicht für alle Nutzer sichtbar sein.
- **Segregation of Duties ist kein Workflow, sondern ein Constraint**. Prüfung bei jeder kritischen Aktion, nicht als optionaler Prozess.
- **Verinice als Referenz-Adapter**. Open Source, gut dokumentiert, für die Community ein wertvoller Ausgangspunkt.

---

← [Agile Skalierungs-Frameworks](19-agile-skalierung.md) · [🏠 Übersicht](../README.md) · [API-Architektur & Modularität](../70-platform/21-api-architektur.md) →

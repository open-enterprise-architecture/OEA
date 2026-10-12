## 2. Meta-Metamodell: Die drei Ebenen

Das Tool arbeitet mit drei Abstraktionsebenen – identisch zum OMG/MOF-Ansatz, aber bewusst schlank gehalten.

### Ebene M2 – Meta-Metamodell (das Fundament)

Definiert die **Sprache**, in der Schemata beschrieben werden. Fest eingebaut, wird durch Nutzer:innen nicht verändert.

| Konzept | Bedeutung |
|---|---|
| `EntityType` | Definiert einen Typ von Architekturobjekt (z.B. "Application", "BusinessService") |
| `RelationType` | Definiert eine **gerichtete** Beziehung mit Start und Ziel (z.B. "realizes", "uses", "belongsTo"). Start und Ziel dürfen jeweils `EntityType` **oder** `RelationType` sein. |
| `Property` | Attribut eines EntityType oder RelationType mit Datentyp und Kardinalität |
| `Constraint` | Regel (z.B. "eine Application muss genau einer Capability zugeordnet sein") |
| `Stereotype` | Erweiterung eines bestehenden EntityType ohne neuen Typ (UML-Profile-Prinzip) |
| `Lifecycle` | Zustandsmaschine, die ein EntityType durchlaufen kann |

### 2.1 Relationen als First-Class-Objekte (Reifikation)

Ein zentrales Design-Prinzip: **Relationen haben Identität**. Jede Relation-Instanz hat eine URN, kann Properties tragen, unterliegt Validierung und hat einen Lifecycle pro Plateau – wie eine Entität. Das ermöglicht:

- Relationen als Ziele von Architekturentscheidungen (z.B. ADR betrifft die Beziehung A→B)
- Waivers auf Relationen (z.B. "Ausnahme: diese Abhängigkeit darf temporär existieren")
- Versionierung von Beziehungen über Plateaus (Interface existiert in 2027, nicht in 2026)
- Reichhaltige Metadaten an Beziehungen (Protokoll, Frequenz, Datenvolumen, SLAs)

**Richtung**: Jede Relation ist gerichtet (Start → Ziel). Symmetrische Semantik wird über die Property `symmetric: true` am RelationType ausgedrückt – strukturell bleibt die Relation gerichtet, sodass Query-Engines und Visualisierungen einheitlich arbeiten.

**Reifikationstiefe**: Ein RelationType darf einen RelationType als Start oder Ziel haben ("Beziehung über Beziehung"). Um pathologische Rekursionen zu vermeiden, gilt per Default eine maximale Reifikations-Tiefe von **2**. Das bedeutet: Eine Relation kann auf eine andere Relation verweisen, aber nicht beliebig tief gestapelt werden. Der Wert ist pro Schema konfigurierbar.

**Opt-in-Charakter**: Die Reifikation ist ein *Opt-in*-Mechanismus. Die meisten Relationen sind triviale Kanten zwischen zwei Entitäten und bleiben es. Reifikation wird nur dort genutzt, wo Relationen fachlich eigenständig sind – mit Metadaten, Governance-Bezug oder eigener Lebensdauer.

**Typische Anwendungsfälle**:

| Fall | Beispiel |
|---|---|
| ADR betrifft Relation | `ADR-042 -[affects]-> Relation(Shop -[uses]-> ERP)` – die Entscheidung "Schnittstelle auf asynchron umstellen" bezieht sich auf die Verbindung, nicht auf eines der Systeme |
| Waiver auf Relation | `Waiver -[grantedFor]-> Relation(LegacyApp -[uses]-> DeprecatedTech)` – Ausnahme für eine konkrete Abhängigkeit |
| InformationFlow implementiert durch Interface | `Relation(BusinessFunc A -[flows-to]-> BusinessFunc B) -[implementedBy]-> Interface X` |
| Interface mit reichhaltigen Properties | Protokoll, Frequenz, Datenvolumen, Verschlüsselung als Relation-Properties |
| Compliance-Deviation auf Relation | `ComplianceDeviation -[deviatesFrom]-> Standard`, betrifft aber Relation "A sendet Klartext an B" |

**API-Auswirkungen**: Einfache Relationen bleiben als Sub-Ressourcen von Entitäten exponiert (`/entities/{urn}/relations`). Relationen mit eigener fachlicher Bedeutung sind zusätzlich direkt adressierbar (`/relations/{urn}`). Details in [§21 (API-Architektur)](../70-platform/21-api-architektur.md).

**Query-Auswirkungen**: Graph-Traversierungen unterscheiden "Kante" vs. "Knoten-Rolle einer Relation" nicht in der API – beide erscheinen als normale Graph-Elemente. Die Komplexität wird auf die Persistenzschicht abgeladen (siehe [§22 (Auswertbarkeit)](../70-platform/22-auswertbarkeit.md)).

### Ebene M1 – Schemata (die konfigurierbare Schicht)

Hier werden konkrete Modellsprachen als Schemata definiert:

- **Schema `togaf-content-metamodel`** – definiert Actor, Role, BusinessService, ApplicationComponent, Technology etc.
- **Schema `archimate-3.2`** – definiert die ArchiMate-Konzepte und -Layer
- **Schema `arc42`** – definiert Bausteine, Laufzeitszenarien, Lösungsstrategien, Querschnitts-Konzepte
- **Schema `bpmn-2.0`** – definiert Process, Task, Lane, DataObject, MessageFlow etc.
- **Schema `custom-<org>`** – Organisations-spezifische Erweiterungen

Schemata können sich **referenzieren und erweitern** (z.B. erbt Arc42 "Baustein" von ArchiMate "ApplicationComponent" mit zusätzlichen Properties).

### Ebene M0 – Instanzen (das Repository)

Die konkreten Modelldaten: "Anwendung SAP S/4HANA", "Geschäftsprozess Rechnungsstellung", "Schnittstelle OrderAPI zwischen Shop und ERP".

---

← [Einordnung und Zielsetzung](01-einordnung.md) · [🏠 Übersicht](../README.md) · [Framework-Verhältnis](../10-foundations/03-framework-verhaeltnis.md) →

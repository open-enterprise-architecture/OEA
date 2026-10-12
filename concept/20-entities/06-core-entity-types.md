## 6. Kern-Entitätstypen

### 6.1 TOGAF Content Metamodel Mapping

Die TOGAF-Architekturdomänen werden 1:1 als Layer abgebildet:

- **Business Architecture**: Actor, Role, OrganizationUnit, BusinessService, BusinessFunction, BusinessProcess, Capability, ValueStream, InformationObject
- **Data Architecture**: DataEntity, LogicalDataComponent, PhysicalDataComponent
- **Application Architecture**: ApplicationComponent, ApplicationService, ApplicationFunction, Interface
- **Technology Architecture**: TechnologyComponent, Platform, Node, CommunicationPath

### 6.1.1 Abgrenzung Application vs. Technology in der Praxis

Die TOGAF-Definitionen sind **strukturell**: ApplicationComponent kapselt Application Functionality, TechnologyComponent kapselt Technology Infrastructure. In der Praxis ist die Abgrenzung bei vielen Objekten trotzdem strittig – moderne Architekturen (Cloud-Native, Serverless, SaaS, Plattform-Produkte) verwischen die Grenzen. Das Tool muss mit dieser Unschärfe umgehen können, ohne jede Grenzfall-Debatte ins ARB eskalieren zu lassen.

**Warum die Abgrenzung schwierig ist**:
- Kausale Verflechtung: jede Technology existiert letztlich, um Applications zu ermöglichen
- Perspektiven-Abhängigkeit: dasselbe Objekt kann für das betreibende Team Application, für das nutzende Team Technology sein
- Plattform-Produkte (Kafka, Kubernetes, Keycloak, Control-M) spielen beide Rollen gleichzeitig

**Vier Entscheidungs-Prinzipien** als praktische Unterstützung:

1. **Business-Purpose-Test**: Existiert das Objekt aus einem eigenständigen fachlichen Grund, unabhängig davon, welche andere Applikation es benötigt? CRM hat fachlichen Zweck "Kundenbeziehung verwalten" → Application. Eine DB hat keinen eigenständigen Zweck, sie existiert für andere → Technology.

2. **Austauschbarkeits-Test**: Würde ein Austausch nur technisch sein oder auch fachlich? Oracle gegen PostgreSQL → nur technisch → Technology. SAP gegen Oracle Financials → auch fachliche Änderungen → Application.

3. **Ownership-Test**: Gibt es einen Product Owner mit fachlicher Verantwortung, der Features priorisiert? Ja → Application. Nein, nur Betriebs-Verantwortung → Technology.

4. **Katalogisierungs-Test**: Würde das Objekt in einem Business Application Catalog auftauchen, den das Management liest? Ja → Application. Nein → Technology.

Diese Prinzipien sind **Heuristiken, keine Algorithmen** – bei Grenzfällen werden sie unterschiedliche Antworten geben. Wichtig ist nicht, dass jedes Prinzip für sich eindeutig ist, sondern dass die Organisation sich auf ein Leitprinzip festlegt und konsistent anwendet.

**Organisations-Prinzip als Pflicht**: Jede Organisation, die das Tool einsetzt, formuliert ein **Architektur-Prinzip** (im Sinne von [§5 (Prinzipien & Standards)](../10-foundations/05-prinzipien-standards-adrs.md)), das die Abgrenzung verbindlich festlegt. Zwei legitime Varianten:

- **Variante A – "Plattform-Services sind Technology"**: Produkte, die primär anderen Applications technische Fähigkeiten bereitstellen (Messaging, DB, Identity, API-Gateway), werden als TechnologyComponents modelliert – auch bei eigenem Betriebsteam. Kafka, PostgreSQL, Keycloak, Kong sind Technology.

- **Variante B – "Produkte mit Product Owner sind Applications"**: Jedes Produkt mit fachlichem Product Owner wird als ApplicationComponent modelliert – auch bei technischem Charakter. Ein internes Identity-System mit Product Owner ist Application.

Beide Varianten sind legitim. Welche eine Organisation wählt, ist eine Governance-Entscheidung, die als ADR dokumentiert wird.

**Dual-Modellierung für Grenzfälle**: Wenn ein Objekt fachlich beide Rollen spielt (z.B. Control-M als Scheduler-Produkt *und* als Laufzeit-Plattform für Jobs anderer Applications), unterstützt das Metamodell drei Ansätze:

- **Beide Entities**: ApplicationComponent und TechnologyComponent werden gleichzeitig modelliert, verbunden über `realizes`-Relation. Maximal explizit, aber pflegeintensiv.
- **Primäre Klassifikation plus Stereotype**: Primär als Application modelliert, mit Stereotype `PlatformProduct`, der die Technology-Rolle kennzeichnet. Pragmatisch.
- **ApplicationService als Brücke**: Die Application exponiert einen ApplicationService, den andere Applications konsumieren. Die Technology-Natur bleibt im Hintergrund, die Schnittstelle ist explizit.

Welcher Ansatz zum Einsatz kommt, sollte ebenfalls im Organisations-Prinzip geregelt sein.

**Konsequenz fürs Tool**: Das Repository zwingt keine der Varianten auf – das Metamodell trägt alle. Aber es unterstützt die Durchsetzung der organisations-weiten Entscheidung über Constraints:

```yaml
constraint: platform-service-classification
  appliesTo: PhysicalApplicationComponent
  when: "entity has stereotype 'PlatformProduct'"
  rule: "organization.classificationPrinciple == 'platform-as-application'
         OR entity also modeled as TechnologyComponent"
  severity: warning
  message: "Plattform-Produkt abweichend vom Organisations-Prinzip klassifiziert"
```

So bleibt die Abgrenzung ein konsistentes organisatorisches Element – keine endlose Grundsatzdebatte pro Objekt.

#### Kognitive Realität: Menschen denken in Lösungen, nicht in Schichten

Das Logical/Physical-Schema ist analytisch wertvoll, aber kognitiv unnatürlich. Menschen denken in konkreten Lösungen: "Wir haben Salesforce." "Da läuft Confluence." Diese Aussagen vermischen automatisch fachliche Rolle und konkrete Implementierung – das ist keine Schlamperei, sondern wie Wahrnehmung funktioniert.

Ein erfolgreiches OEA muss diese Realität anerkennen und darf die analytische Sauberkeit **nicht erzwingen**. Daraus folgt eine zentrale Design-Entscheidung:

**Die Logical-Schicht ist optional, nicht obligatorisch.**

- Eine `PhysicalApplicationComponent` kann ohne zugehörigen `LogicalApplicationComponent` existieren – das Tool blockiert das nicht
- Eine Organisation kann mit reiner Physical-Modellierung beginnen ("Welche Systeme haben wir?") und die Logical-Schicht später bei Bedarf nachziehen ("Welche fachliche Rolle hat jedes System?")
- Schema-Constraints, die Logical-Bezug fordern, sind als `severity: hint` oder `warning` ausgelegt, nicht als `error`

Das ist konsistent mit dem Schema-Evolutions-Ansatz aus [§15 (Schema-Evolution)](../40-extensibility/15-schema-evolution.md): Anforderungen können scope-basiert eingeführt werden, statt globale Pflicht durchzusetzen.

#### Progressive Disclosure als UX-Strategie

Das Tool unterstützt einen schrittweisen Aufbau über drei Sichten:

**Sicht 1 – Inventar-Sicht (Default für Einsteiger und Fachbereiche)**: Flache Liste aller Produkte/Systeme, ohne explizite Ebenen-Unterscheidung. Application und Technology nebeneinander, visuell markiert (z.B. farblich), aber inhaltlich beide Einträge derselben Liste. Wer einsteigt, findet alles, was er kennt, ohne lernen zu müssen, was "Logical Application Component" bedeutet.

**Sicht 2 – Schichten-Sicht (für Architekten)**: Wer auf einen Eintrag klickt, sieht die strukturelle Einbettung – welche Logical Application dahinter steht, welche Technology Components darunter laufen, welche fachlichen Rollen erfüllt werden. Diese Sicht wird gefordert, wenn analytische Tiefe gebraucht wird.

**Sicht 3 – Bebauungsplan-Sicht (für strategische Entscheidungen)**: Hier wird die Logical/Physical-Trennung essentiell, weil Migrationen sichtbar werden (Logical bleibt stabil, Physical wechselt).

Die drei Sichten arbeiten auf demselben Repository, aber zeigen unterschiedliche Detailtiefe. Details zur Sichten-Definition in [§12 (Domain & Sichten)](../30-dynamics/12-domain-sichten.md).

#### Auto-Generierung Logical aus Physical

Um den Step-by-Step-Aufbau zu unterstützen, kann das Tool optional eine **automatische Logical-Stub-Generierung** anbieten: Wird eine PhysicalApplicationComponent ohne expliziten Logical-Bezug angelegt, generiert das Tool im Hintergrund einen Stub-Logical-Component ("`<Name> Logical`"), der später vom Nutzer angereichert oder mit anderen Logicals zusammengeführt werden kann.

Diese Auto-Generierung ist:
- Optional (per Schema-Konfiguration aktivierbar/deaktivierbar)
- Sichtbar markiert als "auto-generiert, pending review"
- Nicht semantisch geladen – der Stub erbt keine fachliche Bedeutung, nur den Namen

So entsteht von Anfang an eine vollständige Schichten-Struktur, ohne dass der Modellierer sie aktiv pflegen muss. Wenn später jemand die Logical-Schicht ernst nimmt, sind die Stubs Startpunkte, keine Blockaden.

### 6.2 Arc42-Mapping

Arc42 ist primär eine **Dokumentstruktur für Solution Design**, keine eigene Modellsprache. Das Mapping erfolgt über:

- **Bausteinsicht** → `ApplicationComponent` (hierarchisch via `contains`)
- **Laufzeitsicht** → `Scenario` mit Referenzen auf Bausteine
- **Verteilungssicht** → `DeploymentNode` mit `hosts`-Relationen
- **Querschnittliche Konzepte** → `ArchitecturalDecision` / `Concept`-Entitäten
- **Architekturentscheidungen** → `ADR`-Entität (mit Referenzen auf betroffene Entities)

Arc42-Dokumente werden **aus dem Repository generiert**, nicht separat gepflegt.

### 6.3 BPMN 2.0 Integration

BPMN-Elemente sind vollwertige Entitäten im Repository mit expliziten Cross-Layer-Referenzen:

| BPMN-Element | Referenziert EA-Entität |
|---|---|
| `Pool` | `OrganizationUnit` |
| `Lane` | `OrganizationUnit` oder `Role` |
| `Task` (User/Service/Manual) | `BusinessService` oder `ApplicationService` |
| `DataObject` / `DataStore` | `InformationObject` oder `DataEntity` |
| `MessageFlow` | `Interface` (fachlicher Informationsfluss) |
| `Event` (Message) | `Interface` |

So entsteht eine **durchgehende Traceability**: ein BPMN-Task weiß, welchen BusinessService er aufruft; der Service kennt seine realisierende Application; die Application kennt ihre Technologie. Diese Traversierbarkeit ist die Grundlage für Impact-Analysen.

---

← [Prinzipien, Standards & ADRs](../10-foundations/05-prinzipien-standards-adrs.md) · [🏠 Übersicht](../README.md) · [Motivation, Stakeholder & Ziele](07-motivation-stakeholder-ziele.md) →

## 21. API-Architektur und Modularität

Das Tool ist als **modulares System** konzipiert: ein schlanker Kern mit einer stabilen, versionierten API, an die fachliche Zusatzmodule und Integrationen andocken. Dieses Kapitel präzisiert die API-Schichten, das Modul-Konzept und die Abgrenzung Kern vs. Erweiterung.

### 21.1 Architektur-Schichten

```
┌──────────────────────────────────────────────────────┐
│  UI / CLI / Pipeline-Tools / Zusatzmodule            │  ← Konsumenten
├──────────────────────────────────────────────────────┤
│                    Public API                         │  ← Vertrag
│  - REST  - GraphQL  - gRPC (optional)                │
├──────────────────────────────────────────────────────┤
│  Query-Engine  │  Command-Engine  │  Event-Stream    │  ← Anwendungsschicht
│  (lesen)       │  (schreiben)     │  (reagieren)      │
├──────────────────────────────────────────────────────┤
│  Meta-Metamodell + Schema-Engine + Validierung       │  ← Domänenschicht
├──────────────────────────────────────────────────────┤
│  Persistenz (austauschbar)                           │  ← Infrastruktur
└──────────────────────────────────────────────────────┘
```

Die API ist der **harte Vertrag nach außen**. Alles oberhalb der API-Linie (UI, Module) kennt die Schichten darunter nicht.

### 21.2 Kern vs. Module

**Der Kern enthält**:
- Meta-Metamodell und Schema-Engine
- Entity- und Relation-Management (CRUD + Validierung)
- Plateau- und Lifecycle-Management
- Query- und Traversierungs-Engine (siehe [§22 (Auswertbarkeit)](22-auswertbarkeit.md))
- Event-Stream für Änderungsbenachrichtigungen
- Authentifizierung/Autorisierung
- Versionierung und History

**Module sind fachliche Erweiterungen**, die über die API kommunizieren:
- **BPMN-Modul** – Import/Export von BPMN-XML, Mapping auf abstrakte Prozess-Entitäten
- **ITSM-Konnektoren** – ServiceNow, BMC Helix, Jira SM (siehe [§17 (ITSM-Integration)](../60-integrations/17-itsm-integration.md))
- **PPM-Konnektoren** – Jira, OpenProject, ServiceNow SPM (siehe [§18 (PPM-Integration)](../60-integrations/18-ppm-integration.md))
- **Renderer** – konkrete Notation/Notationen noch zu entscheiden (siehe §21.2.1 Visualisierungs-Strategie unten)
- **Documentation-Generatoren** – Arc42-Dokumente, TOGAF Phase-Outputs, PDF-Reports
- **Importer/Exporter** – Excel, CSV, ArchiMate Open Exchange, gängige OEA-Formate
- **Discovery-Adapter** – Cloud-Discovery (AWS, Azure, GCP), CMDB-Discovery, API-Discovery
- **Skill-Frameworks** – SFIA, EuroCIO
- **Reference-Frameworks** – APQC-PCF, eTOM, SCOR

Kriterium für "Modul": Wenn es ohne den Kern keinen Sinn hat, aber auch nicht von allen Installationen gebraucht wird – dann ist es ein Modul.

#### 21.2.1 Visualisierungs-Strategie – bewusst offene Entscheidung

Die Frage, welche Diagramm-Notation(en) das Tool unterstützt, ist **bewusst noch nicht entschieden**. Sie soll nach Abschluss der Requirements-Engineering-Phase getroffen werden, wenn klar ist:

- Welche Personas welche Sichten brauchen
- Wie Modellierer im Alltag denken und arbeiten wollen
- Welcher Trade-off zwischen Wartbarkeit, Layout-Qualität und Lernkurve akzeptabel ist

##### Trade-off: textbasiert vs. grafisch

Beide Ansätze haben fundamentale Stärken und Schwächen, die nicht durch Tool-Wahl wegoptimiert werden können – nur abgewogen:

| Aspekt | Textbasierte DSL (PlantUML, Mermaid, D2, Graphviz, Structurizr) | Grafische Editoren (Archi, drawio, Sparx EA) |
|---|---|---|
| **Pipeline-Eignung** | ✓ ideal (Git-diffbar, PR-reviewbar, CI-generierbar) | ✗ binäre/XML-Dateien, schlecht diffbar |
| **Wartbarkeit über Zeit** | ✓ Refactoring per Find/Replace möglich | ⚠️ manuell pro Diagramm |
| **Layout-Kontrolle** | ⚠️ Auto-Layout, oft suboptimal | ✓ pixelgenau steuerbar |
| **Visuelle Qualität für Präsentationen** | ⚠️ funktional, selten beeindruckend | ✓ präzise und ästhetisch anpassbar |
| **Lernkurve** | ⚠️ DSL muss gelernt werden | ✓ visuelles Drag-and-Drop intuitiv |
| **Konsistenz über viele Diagramme** | ✓ DSL erzwingt Stilkonsistenz | ⚠️ leicht inkonsistent |
| **Generierbarkeit aus Modell** | ✓ trivial | ⚠️ aufwändig (Geometrie berechnen) |
| **Nachbearbeitung durch Stakeholder** | ✗ erfordert DSL-Kenntnisse | ✓ jeder kann anpassen |
| **Versionierung** | ✓ Text in Git | ⚠️ XML-Diff möglich, aber unübersichtlich |
| **Standardkonformität (ArchiMate)** | ⚠️ je nach Tool unterschiedlich | ✓ Archi ist Referenz-Implementierung |

Keine der beiden Welten ist universell überlegen. Eine **Use-Case-bezogene Kombination** ist denkbar und sollte nach Abschluss der Stakeholder- und Use-Case-Analyse evaluiert werden.

##### Use-Case-bezogene Renderer-Wahl (Skizze)

Anstelle einer einzigen "richtigen" Notation ist denkbar, dass das Tool **mehrere Backends** für unterschiedliche Diagramm-Typen unterstützt:

| Diagramm-Typ | Mögliches Backend | Begründung |
|---|---|---|
| Sequenzdiagramme | textbasiert | textuelle DSL ist hier unschlagbar (PlantUML stark) |
| ADR-eingebettete Skizzen | leichtgewichtig, inline-renderbar | Markdown-Preview reicht (Mermaid stark) |
| Bebauungspläne | textbasiert generiert + grafisch nachbearbeitbar | aus Modell generierbar, aber Präsentation visuell anspruchsvoll |
| ArchiMate-Sichten | grafischer Editor mit XML-Export | Standardkonformität wichtig |
| C4-Modell-Diagramme | textbasiert (Structurizr DSL) | spezialisierter Standard |
| Capability-Heatmaps | grafisch im Tool gerendert | Matrix-Typ, weder DSL noch Editor sinnvoll |
| Datenfluss-Diagramme | textbasiert (Graphviz/D2) | Auto-Layout funktioniert hier gut |

##### Was zu klären ist

Nach Abschluss der Requirements-Phase sind zu beantworten:

1. **Welche Personas brauchen welche Sichten?** (aus Use-Case-Katalog)
2. **Welche Notationen kennen die Zielnutzer bereits?** (Archi-Erfahrung? PlantUML? C4?)
3. **Wie wichtig ist Pipeline-Generierung vs. manuelle Pflege?** (CI-getriebene Doku vs. Workshop-Diagramme)
4. **Wie wichtig ist ArchiMate-Standard-Konformität?** (regulatorischer Druck? Audit-Anforderung?)
5. **Welche Bestandstools sollen weiterhin nutzbar sein?** (vorhandene Sparx-/Archi-Modelle?)

Diese Fragen werden als ADR adressiert, wenn die Antworten aus dem Requirements-Engineering vorliegen.

##### Übergangs-Empfehlung für die Konzeptarbeit

Bis die Entscheidung getroffen ist, gilt für die Konzept-Dokumentation:

- Diagramme werden **so notiert, wie sie für die Doku gerade am besten passen** (typisch: Mermaid für inline-Markdown, freier Text für Skizzen)
- Keine harte Bindung an eine konkrete Notation im Konzept – Beispiele bleiben deskriptiv ("Bebauungsplan mit drei Zeitspalten" statt PlantUML-Code)
- Wenn DSL-Beispiele nötig sind, werden sie als "exemplarisch, ersetzbar" gekennzeichnet

Das vermeidet, dass spätere Notations-Entscheidungen umfangreiche Konzept-Nacharbeiten erfordern.

### 21.3 API-Konventionen

**Versionierung**: Semantic Versioning auf API-Ebene. Pfad-Versionierung (`/api/v1/...`), keine Header-Tricks. Major-Breaking-Changes nur mit Deprecation-Periode von mindestens 12 Monaten und parallelem Betrieb.

**Ressourcen-Modell**: REST-Ressourcen folgen den EntityTypes aus dem Metamodell. Relations sind eigene Sub-Ressourcen (`/api/v1/entities/{id}/relations`) für Navigation.

**Format**: JSON als Default. YAML als Alternative (git-friendly). Content-Negotiation via `Accept`-Header.

**Identität**: URNs gemäß [§23 (Offene Punkte)](../90-backlog/23-offene-punkte.md)-Entscheidung in den offenen Punkten (`urn:ea:<org>:<type>:<id>`). URNs sind in Payloads First-Class, nicht numerische DB-IDs.

**Pagination, Filter, Sortierung**: Standard-Querystring-Konventionen (`?page=`, `?filter=`, `?sort=`). Konsistent über alle Ressourcen.

**Idempotenz für Schreiboperationen**: `Idempotency-Key`-Header für nicht-GET-Anfragen. Verhindert Doppel-Erstellung bei Netzwerkfehlern.

### 21.4 API-Endpunkte im Überblick

```
# Entity-Management
GET    /api/v1/entities?type=Application&...
POST   /api/v1/entities
GET    /api/v1/entities/{urn}
PATCH  /api/v1/entities/{urn}
DELETE /api/v1/entities/{urn}

# Relations
GET    /api/v1/entities/{urn}/relations
POST   /api/v1/relations
GET    /api/v1/relations/{id}

# Graph-Traversierung
POST   /api/v1/graph/traverse              ← siehe [§22 (Auswertbarkeit)](22-auswertbarkeit.md)
POST   /api/v1/graph/shortest-path
POST   /api/v1/graph/neighborhood

# Analytics / Aggregation
POST   /api/v1/analytics/query             ← siehe [§22 (Auswertbarkeit)](22-auswertbarkeit.md)
GET    /api/v1/analytics/views/{name}

# Schema und Metamodell
GET    /api/v1/schema
GET    /api/v1/schema/types
GET    /api/v1/schema/types/{name}

# Plateaus und temporale Sichten
GET    /api/v1/plateaus
GET    /api/v1/entities?asOfPlateau=target-2028
POST   /api/v1/plateaus/{a}/diff/{b}

# Validierung und Compliance
POST   /api/v1/validate
GET    /api/v1/compliance/deviations
GET    /api/v1/compliance/waivers

# Events
GET    /api/v1/events/stream               ← Server-Sent Events
POST   /api/v1/events/subscriptions        ← Webhooks

# Import/Export
POST   /api/v1/import
POST   /api/v1/export
```

### 21.5 Event-Stream als Integrations-Rückgrat

Module und externe Systeme müssen auf Repository-Änderungen reagieren können – dafür existiert ein Event-Stream.

```yaml
Event-Typen:
  - EntityCreated, EntityUpdated, EntityDeleted
  - RelationCreated, RelationDeleted
  - PlateauCreated, PlateauActivated
  - ValidationFailed, ComplianceViolationDetected
  - WaiverExpiring (3 Tage vor Ablauf)
  - SchemaVersionChanged
  - ADRStateChanged
```

Konsumenten:
- BPMN-Modul reagiert auf ApplicationComponent-Änderungen (potenzielle Prozess-Implikationen)
- ITSM-Konnektor synchronisiert Property-Änderungen in beide Richtungen
- Renderer invalidieren gecachte Diagramme bei betroffenen Entitäten
- Audit-Systeme loggen Änderungshistorie

**Transport-Optionen**: Server-Sent Events (einfach, HTTP), WebSocket (bidirektional), Webhooks (pull-based), Kafka/NATS (wenn vorhanden).

### 21.6 Modul-SDK

Um die Entwicklung von Modulen zu erleichtern, wird ein **Modul-SDK** bereitgestellt:

```yaml
Modul-SDK bietet:
  - Typisierte API-Clients (initial: Python, TypeScript)
  - Event-Subscription-Helpers
  - Schema-Introspection
  - Authentifizierungs-Helper
  - Test-Harness (Mock-Repository für Modul-Tests)
```

Das SDK ist selbst Open Source und nutzt ausschließlich die Public API – keine privilegierten Aufrufe. Was das SDK kann, kann jeder andere Konsument auch.

### 21.7 BPMN als exemplarisches Zusatzmodul

Das BPMN-Modul dient als **Referenz-Implementierung** für Modul-Entwicklung:

```yaml
BPMN-Modul Verantwortlichkeiten:
  - Import von BPMN-2.0-XML-Dateien
  - Mapping auf abstrakte Prozess-Entitäten ([§9 (Prozess-Architektur)](../20-entities/09-prozess-architektur.md))
  - Export von Prozess-Entitäten nach BPMN-2.0-XML
  - Bidirektionale Synchronisation mit externen BPM-Tools (Camunda, Signavio, BIC)
  - Rendering von BPMN-Diagrammen
  - Validierung gegen BPMN-2.0-Schema

BPMN-Modul nutzt (über API):
  - Entity-CRUD für Process, Activity, Flow, Pool, Lane
  - Schema-Introspection für Validierung
  - Events für Änderungsbenachrichtigung

BPMN-Modul registriert:
  - Neue Content-Types: application/bpmn+xml
  - Neue Viewer/Renderer
  - Neue Import/Export-Formate
```

Weitere Prozess-Notationen (EPK, VSM, DMN für Entscheidungslogik) können als analoge Module folgen, ohne den Kern zu ändern.

### 21.8 Sicherheit und Autorisierung

```yaml
Authentifizierung:
  - OAuth 2.0 / OIDC (Standard, inkl. SSO über vorhandene Identity-Provider-Session)
  - API-Keys für Maschinen-zu-Maschinen
  - Optionale Mutual TLS für höchste Sicherheitsanforderungen
  - Lokale Authentifizierung (optional, für Self-Hosting ohne externen IdP):
    - Passkey / WebAuthn (passwortlos, Standard für lokale Auth)
    - Username/Passwort mit TOTP als zweitem Faktor
    - Username/Passwort ohne zweiten Faktor (Minimal-Variante, nicht empfohlen)

Autorisierung:
  - Rollen-basiert (RBAC) als Standard
  - Attribut-basiert (ABAC) optional – für Domain-/Scope-spezifische Rechte
  - Permission-Level: read | write | admin pro Ressource
  - Besondere Permissions: schema-modify, plateau-create, waiver-grant
  - Property-Level-Autorisierung (Pflicht-Feature)
```

Rollen im Metamodell (siehe [§8.2 (Organisation & Rollen)](../20-entities/08-organisation-rollen-personen.md)) können direkt als Autorisierungs-Rollen genutzt werden – eine Person, die die Rolle "Enterprise Architect" innehat, bekommt auch die entsprechenden API-Rechte.

**Lokale Authentifizierung**: OIDC setzt einen vorhandenen Identity-Provider voraus. Für Single-User- und KMU-Self-Hosting ohne eigenes IdP-Team (siehe Stakeholder SH-03, SH-06) muss OEA auch ohne externen IdP betreibbar sein. Die lokalen Mechanismen sind bewusst gestuft: Passkey/WebAuthn als empfohlener Standard (passwortlos, phishing-resistent), Username/Passwort mit TOTP als zweitem Faktor für Umgebungen ohne WebAuthn-Unterstützung, und Username/Passwort ohne zweiten Faktor als bewusst riskante Minimal-Variante, die im Audit-Log entsprechend kenntlich gemacht wird. Alle lokalen Mechanismen sind optional und schließen sich nicht gegenseitig mit OIDC aus – eine OEA-Instanz kann beide Wege parallel anbieten.

**Property-Level-Autorisierung**: Über Entity-Level-Berechtigungen hinaus unterstützt die API granulare Zugriffskontrolle auf einzelne Properties. Das ist Pflicht-Feature, nicht optional, weil sensible Informationen (personenbezogene Daten, detaillierte Schutzbedarfe, Waiver-Begründungen) nicht für alle Nutzer sichtbar sein dürfen.

Mechanik:

```yaml
propertyAuthorization:
  - entity: DataEntity
    property: personalDataCategory
    read: [DPO, ISO, Architekt]
    write: [DPO]
  - entity: ArchitectureWaiver
    property: reason
    read: [ARB-Mitglied, Architekt, Auditor]
    write: [ARB-Mitglied]
```

API-Verhalten:

- Bei GET-Requests werden nicht-berechtigte Properties automatisch aus der Response entfernt (keine Fehlermeldung, nur Filterung)
- Bei PATCH-Requests führt Schreibversuch auf nicht-berechtigte Property zu HTTP 403
- Schema-Introspection (`/api/v1/schema/types/{name}`) zeigt nur die Properties, für die der anfragende Principal mindestens Leserecht hat
- Audit-Log erfasst, wer wann welche geschützten Properties abgefragt hat (siehe Audit-Trail unten)

**Audit-Trail (Pflicht-Feature)**: Alle Änderungen und sensible Lesevorgänge werden unveränderbar protokolliert:

```yaml
AuditLog-Eintrag:
  - timestamp
  - principal: User | ApiKey | Role
  - action: create | update | delete | read-sensitive
  - target: URN
  - property: string (bei granulären Änderungen)
  - oldValue, newValue (bei Änderungen; bei sensitiven Daten: Hash statt Klartext)
  - clientContext: UI | Module | Script
  - changeReason: string (optional, bei Commit-Messages oder Change-Tickets)
```

Eigenschaften des Audit-Trails:

- **Append-only**: Einträge sind nach Erzeugung unveränderbar
- **Separate Speicherung**: Audit-Daten liegen in separatem Store, damit Zugriffsrechte unabhängig vom Haupt-Repo konfigurierbar sind
- **Exportierbar**: JSON/CSV für externe SIEM-Integration (Splunk, Elastic)
- **Aufbewahrungspflichtig**: Konfigurierbar nach regulatorischen Vorgaben (DSGVO, ISO 27001 typisch 3+ Jahre)
- **Selbst zugriffsgeschützt**: Audit-Logs zu lesen erfordert eigene Permission (typisch "Auditor"-Rolle)

**Abgrenzung: Audit-Log, Change-Log und Entity Change History**

Drei verwandte, aber unterschiedliche Konzepte werden im Tool sauber getrennt:

| Konzept | Zweck | Eigenschaften | Konsumenten |
|---|---|---|---|
| **Audit-Log** | Forensik, regulatorischer Nachweis | Lückenlos, append-only, schreibt jede Änderung mit, kann "rauschen" | Auditoren, Compliance, SIEM |
| **Change-Log / Release-Notes** | Stakeholder-Kommunikation | Kuratiert, zusammengefasst, "wichtige" vs. "unwichtige" Änderungen, mit Release-Tags | Architekten, Fachbereiche, Management |
| **Entity Change History** | Verlauf eines konkreten Objekts | Pro Entität, alle Versionen mit Zeitstempel und Begründung | Architekten beim Pflegen einzelner Objekte |

Sie speisen sich aus derselben Quelle (jede Änderung erzeugt einen Audit-Eintrag), werden aber unterschiedlich gefiltert und aggregiert:

- **Audit-Log** ist die rohe, vollständige Wahrheit
- **Change-Log** ist die kuratierte Sicht für Außenstehende (siehe [§22 (Auswertbarkeit)](22-auswertbarkeit.md))
- **Entity Change History** ist die per-Objekt-Sicht in der UI / API (`GET /entities/{urn}/history`)

Details zur fachlichen Nutzung dieser Mechanismen in [§20 (GRC/DSGVO/ISMS-Integration)](../60-integrations/20-grc-dsgvo-isms-integration.md).

### 21.9 Dokumentation und Discoverability

**OpenAPI-Spezifikation** für die komplette API, generiert aus Code. Schwalbach-Tests gegen die Spec in CI.

**Schema-Introspection**: Die API kennt ihr eigenes Metamodell und stellt es zur Verfügung. Tools können die aktuell aktiven EntityTypes und Properties dynamisch entdecken.

**Changelog**: Jede API-Version hat einen maschinenlesbaren Changelog (z.B. OpenAPI-Diff), der Breaking Changes, Deprecations und Ergänzungen dokumentiert.

---

← [GRC-, DSGVO- und ISMS-Integration](../60-integrations/20-grc-dsgvo-isms-integration.md) · [🏠 Übersicht](../README.md) · [Auswertbarkeit & Query-Architektur](22-auswertbarkeit.md) →

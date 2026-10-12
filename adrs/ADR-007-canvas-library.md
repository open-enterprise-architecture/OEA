# ADR-007: Canvas-Bibliothek für interaktive Diagramm-Editierung

**Status**: accepted
**Datum**: 2026-06-26
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer (UC-05, US-045, REQ-040)
**Informiert**: –
**Aktualisiert**: 2026-06-26 — React Flow → **Vue Flow** infolge ADR-011 (Frontend-Framework: Vue 3)  
**Aktualisiert**: 2026-06-28 — verbliebene React-Artefakte bereinigt; EPL 2.0-Lizenzbedingungen für ELK.js explizit dokumentiert; ADR-008-Status auf accepted korrigiert

> **Zuvor zurückgestellt** (bis ADR-008 entschieden): [ADR-008](./ADR-008-gui-architektur-dual-track.md) (accepted) legt fest, dass der interaktive Diagramm-Canvas in der **Client App** läuft. Damit ist der Blockierungsgrund entfallen.

> **Update 2026-06-26**: [ADR-011](./ADR-011-frontend-framework.md) entscheidet das Frontend-Framework auf **Vue 3 + TypeScript**. Damit wird die ursprüngliche Wahl React Flow durch **Vue Flow** (`@vue-flow/core`, MIT) ersetzt — ein direkter Port von React Flow für Vue 3 mit identischen Konzepten (Nodes, Edges, Composables statt Hooks).

## Kontext und Problem

UC-05 (Architektur-Vision einer Änderungsinitiative beschreiben) erfordert, dass Solution Architekten Entitäten direkt in einem **interaktiven Diagramm** (Canvas) platzieren und benennen können. US-045 spezifiziert dabei eine besondere Verhaltensanforderung: Beim Eingeben des Namens einer neuen Entität auf dem Canvas muss eine Vorschlagsliste mit bestehenden Komponenten aus der IT-Landschaft erscheinen, damit der Architekt sofort erkennt, ob eine Komponente schon existiert und er statt eines `new`-Deltas ein `modified`- oder `retiring`-Delta anlegen sollte.

Dafür wird eine **Web-Canvas-Bibliothek** benötigt, die:
- Knoten (Entitäten) und Kanten (Relationen) interaktiv platzierbar und editierbar macht
- In-Place-Texteditierung direkt auf dem Canvas-Element unterstützt (kein separater Dialog)
- Sich in eine Vue 3/TypeScript-Webanwendung integriert
- State als sauberes JSON serialisiert (Git-taugliche Diffs, kein Binärformat)

§21.2.1 des Konzepts hat die Visualisierungsstrategie bewusst auf nach dem Requirements Engineering verschoben. Dieser ADR adressiert nur den **interaktiven Editier-Canvas** (Kernanforderung für UC-05 / Walking Skeleton), nicht die gesamte Visualisierungsstrategie (Rendering-Backends für read-only Diagramme, Notationsunterstützung etc.) – das bleibt ADR-008.

## Entscheidungstreiber

- **OSS-Kompatibilität**: OEA steht unter Apache 2.0; die Canvas-Bibliothek muss eine kompatible Open-Source-Lizenz haben (MIT, Apache 2.0, BSD)
- **Vue 3/TypeScript-native**: das Frontend wird als Vue 3-Anwendung entwickelt (ADR-011); die Bibliothek soll sich natürlich in dieses Ökosystem einfügen
- **In-Place-Editierung**: Name-Eingabe direkt auf dem Canvas-Knoten (US-045 AC1–AC3)
- **Serialisierbarkeit**: Diagramm-State als diff-bares JSON; kein proprietäres Binärformat (Concern SH-04: Git-nativer Workflow)
- **Custom Node Shapes**: verschiedene Entitätstypen (ApplicationComponent, Interface, BusinessProcess …) brauchen unterschiedliche visuelle Darstellungen
- **Aktive Community / Wartung**: Bibliothek muss langfristig gepflegt sein; keine toten Forks
- **Lizenzkosten**: keine pro-Seat- oder pro-Deployment-Gebühren (OSS-Prinzip)

## Betrachtete Optionen

### Option 1: Vue Flow (`@vue-flow/core`) ✓ GEWÄHLT

Vue Flow ist ein direkter Port von React Flow für Vue 3. MIT-lizenziert. Nodes sind Vue 3-Komponenten.

- **Pro**:
  - Beste Vue 3/TypeScript-Integration; Nodes sind plain Vue-Komponenten (SFCs)
  - Gleiche Konzepte wie React Flow (Nodes, Edges, Handles, MiniMap, Controls) — React-Flow-Dokumentation ist übertragbar
  - `useVueFlow()` Composable statt React-Hooks — idiomatisches Vue 3
  - In-Place-Editierung einfach implementierbar (Input-Element in Node-SFC)
  - State (Nodes, Edges) als JSON-Array; diff-freundlich
  - MIT-Lizenz, keine Einschränkungen
  - Aktiv gepflegt; GitHub: `bcakmakoglu/vue-flow`
- **Contra**:
  - Kleinere Community als React Flow (aber ähnliche API → React-Flow-Ressourcen nutzbar)
  - Ursprünglich für Flow/DAG-Diagramme optimiert; EA-spezifisches Layout muss selbst gebaut werden
  - Auto-Layout (hierarchisch, layered) braucht externe Bibliothek (ELK.js, dagre)
  - Für sehr grosse Graphen (>1000 Knoten) ggf. Performance-Optimierungen nötig

### Option 2: maxGraph (Community-Fork von mxGraph)

maxGraph ist der Apache 2.0-lizenzierte Community-Fork von mxGraph, auf dem draw.io / diagrams.net basiert. Sehr mächtige, battle-tested Bibliothek mit Jahrzehnten EA-Diagramm-Unterstützung.

- **Pro**:
  - Extrem feature-reich: Auto-Layout, vordefinierte EA-Shape-Bibliotheken, Gruppen, Swimlanes
  - Battle-tested in draw.io (Millionen von Nutzern)
  - Apache 2.0-Lizenz
  - Gut geeignet für komplexe architektonische Layouts (layered, orthogonal Routing)
- **Contra**:
  - Keine native React-Integration; erfordert Wrapper-Schicht
  - API ist konzeptionell älter (mxGraph-Erbe aus frühen 2000ern); TypeScript-Types unvollständig
  - Kleinere Community als React Flow; Dokumentation lückenhafter
  - Steilere Lernkurve; In-Place-Editierung ist konfigurierbar, aber nicht React-nativ
  - maxGraph ist junger Fork (seit 2022); Stabilität des Projekts noch im Aufbau

### Option 3: AntV X6

AntV X6 ist eine Apache 2.0-lizenzierte Graph-Editor-Bibliothek von Ant Group (Alibaba). Feature-reich, gute React-Integration, für interaktive Diagramm-Editierung konzipiert.

- **Pro**:
  - Apache 2.0-Lizenz
  - Starke Graph-Editierungs-Features (inklusive In-Place-Edit, Custom Shapes, Ports)
  - Gute React-Integration (@antv/x6-react-shape)
  - Aktiv gepflegt, professioneller Backing durch Ant Group
- **Contra**:
  - Primäre Dokumentation und Community auf Chinesisch; englische Dokumentation lückenhaft
  - Weniger Verbreitung im westlichen Open-Source-Ökosystem; weniger StackOverflow-/GitHub-Issues-Unterstützung
  - API hat zwischen v1 und v2 Breaking Changes eingeführt; langfristige Stabilität schwerer abschätzbar
  - TypeScript-Typen vollständig, aber API-Ergonomie weniger konsistent als React Flow

### Option 4: GoJS

Kommerzielle Canvas-Bibliothek mit professioneller EA-Diagramm-Unterstützung.

- **Pro**:
  - Sehr mächtig, ausgereifte EA-/UML-Unterstützung
  - Exzellente Dokumentation
- **Contra**:
  - Kommerzielle Lizenz (per-Developer-Gebühr); inkompatibel mit Apache 2.0 OSS-Projekt
  - Scheidet aus

## Entscheidung

Wir wählen **Option 1: Vue Flow** (`@vue-flow/core`), weil es die beste Integration in den Vue 3/TypeScript-Stack bietet (ADR-011), die In-Place-Editierung durch Vue-Komponenten als Nodes trivial realisierbar ist, der State als diff-bares JSON-Array vorliegt, und die Konzepte 1:1 von React Flow übernommen wurden (Portierungsaufwand minimal, Doku übertragbar).

Die EA-spezifischen Lücken (Auto-Layout, ArchiMate-Shape-Bibliothek) sind bekannte, lösbare Probleme:
- Auto-Layout wird über **ELK.js** (Eclipse Layout Kernel, EPL 2.0; per Bundling Apache 2.0-kompatibel verwendbar) nachgerüstet, sobald die Anforderung konkret wird
- Custom Shapes (ApplicationComponent, Interface, BusinessProcess etc.) werden als Vue-SFCs direkt im OEA-Frontend definiert — kein Third-Party-Shape-Set nötig

GoJS scheidet wegen kommerzieller Lizenz aus. maxGraph und AntV X6 sind technisch geeignet, haben aber keine native Vue 3-Integration. Vue Flow ist für den vorliegenden Stack die natürlichste Wahl.

## Konsequenzen

### Positive Konsequenzen

- US-045 (Neue Entität im Diagramm anlegen) ist implementierbar: Typeahead-Vorschlagsliste und In-Place-Edit sind über Vue-Node-SFCs direkt realisierbar
- Entwickler müssen keine neue Abstraktionsschicht erlernen — Vue Flow ist Vue 3
- Diagramm-State (Nodes, Edges, Positionen) ist als JSON in der Datenbank speicherbar und git-diff-freundlich

### Negative Konsequenzen / Trade-offs

- Auto-Layout für grosse EA-Diagramme (Bebauungsplan-Ansicht) ist nicht out-of-the-box; ELK.js-Integration ist eine eigene Aufgabe, wenn diese Ansicht gebaut wird
- Vue Flow ist nicht speziell für ArchiMate oder TOGAF-Notation gebaut; alle domänenspezifischen Shapes müssen im OEA-Frontend selbst gepflegt werden
- Performance bei sehr grossen Graphen (> 500 Knoten sichtbar) erfordert ggf. eigene Virtualisierungsoptimierungen; für Walking Skeleton kein Problem

### Folgeentscheidungen

- **ADR-008** (accepted): Vollständige Visualisierungsstrategie – welche Diagramm-Typen (ArchiMate Layer Views, C4, Sequenz, BPMN) rendert OEA, und mit welchen Backends (textbasiert vs. Canvas)? Vue Flow ist der Canvas-Baustein für die Client App (§21.2.1)
- **ELK.js-Integration**: wenn hierarchisches Auto-Layout für Bebauungsplan-Ansichten gebraucht wird; als separates Requirement zu spezifizieren
- **Shape-Bibliothek**: ArchiMate-konforme Node-Shapes (ApplicationComponent, Interface, BusinessProcess, DataObject …) als Vue-SFCs – Designaufgabe für UI-Designerin (SH-UI-Designer-Agent)

## Bezüge

**Konzept-Kapitel**:
- [§21.2.1 Visualisierungs-Strategie](../concept/70-platform/21-api-architektur.md)

**Verwandte ADRs**:
- [ADR-008](./ADR-008-gui-architektur-dual-track.md) (accepted): Client App + Web Portal; Vue Flow läuft im Client-App-Kontext (Electron)

**Use Cases / Requirements**:
- [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../requirements/use-cases/UC-05-architektur-vision-beschreiben.md)
- [REQ-040: EntityDeltas einer Solution erfassen](../requirements/req/REQ-040-entity-deltas-erfassen.md)
- [US-045: Neue Entität im Diagramm als Delta anlegen](../requirements/user-stories/US-045-delta-neue-entitaet-diagramm.md)

## Notizen

Vue Flow: `@vue-flow/core` (MIT). GitHub: `bcakmakoglu/vue-flow`. Setzt Vue 3.x voraus. Vor Implementierung aktuelle Stable-Version prüfen.

### ELK.js – EPL 2.0 Lizenzbedingungen

ELK.js (Eclipse Layout Kernel) steht unter **EPL 2.0** (Eclipse Public License 2.0). EPL 2.0 ist ein schwaches Copyleft: es gilt nur für den EPL-lizenzierten Code selbst, nicht für das gesamte Projekt. Für OEA gelten folgende Bedingungen:

| Bedingung | OEA-Situation | Massnahme |
|---|---|---|
| **Quellcode-Verfügbarkeit** | ELK.js wird unverändert als npm-Paket eingebunden | Kein Handlungsbedarf: upstream-Repo (`eclipse/elk`) ist öffentlich; Link im NOTICE-File genügt |
| **Copyleft-Ansteckung** | EPL 2.0 ist ein „weak copyleft" — tritt nur bei Modifikation oder direkter Weitergabe der EPL-Datei auf | OEA modifiziert ELK.js nicht → kein Copyleft auf OEA-Eigencode |
| **NOTICE-File-Pflicht** | Distribution des Electron-Bundles (`.dmg`, `.exe`, `.AppImage`) enthält ELK.js | NOTICE-File muss enthalten: `ELK.js (Eclipse Layout Kernel) – Eclipse Public License 2.0 – https://github.com/eclipse/elk` |
| **Kompatibilität mit Apache 2.0** | EPL 2.0 enthält eine „Secondary License"-Klausel, die Kombination mit Apache 2.0 im gleichen Produkt erlaubt | Kein Konflikt; OEA-Projekt-Lizenz (Apache 2.0) bleibt unberührt |

**Fazit**: ELK.js ist in OEA unter EPL 2.0 nutzbar. Einzige Pflicht bei Distribution: NOTICE-File-Eintrag. Wenn ELK.js zu einem späteren Zeitpunkt durch eine MIT/Apache-2.0-Alternative ersetzt wird (z.B. `elkjs`-Wrapper mit anderem Build), entfällt die Bedingung.

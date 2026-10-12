# ADR-008: GUI-Architektur – Client App + Web Portal (Dual-Track-Delivery)

**Status**: accepted
**Datum**: 2026-06-26
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer (UC-05, UC-06, US-045), Business Engineer (Viewpoint-BO, Catalog-BO)
**Informiert**: –
**Aktualisiert**: 2026-06-28 — React-SPA-Referenzen auf Vue 3 SPA aktualisiert (infolge ADR-011); ADR-007-Status von deferred auf accepted korrigiert (Vue Flow gewählt)

## Kontext und Problem

OEA muss zwei grundlegend verschiedene Nutzergruppen bedienen:

- **Modellierungs-Nutzer** (Solution Architekten, Lead Enterprise Architekten, Business Engineers): Sie legen Entitäten an, zeichnen Diagramme, konfigurieren das Metamodell und beschreiben Architektur-Visionen. Für diese Gruppe ist die GUI das primäre Arbeitswerkzeug – täglich, über Stunden, mit hoher Interaktionsdichte.
- **Konsumierungs-Nutzer** (CIO, Operator, Domänen-Betrachter): Sie lesen Kataloge, betrachten Diagramme und konsultieren das Repository bei Bedarf. Ihr Zugang soll niedrigschwellig sein: kein Download, kein Login-Aufwand, einfach ein Link.

Ein einziges GUI-Fahrzeug kann beide Gruppen nicht gleichzeitig optimal bedienen: Ein Web Portal ist immer durch Browser-APIs limitiert (Dateisystem, nativer IPC, GPU-Direktzugriff, Memory-Ceiling); eine dedizierte Client App hingegen kann beliebig tief in die Plattform integrieren.

Zusätzlich ist die Canvas-Bibliothekswahl (ADR-007, deferred) davon abhängig, in welchem Laufzeit-Kontext der interaktive Diagramm-Editor ausgeführt wird. Im Browser gelten andere Einschränkungen (DOM-Performance-Grenze, kein nativer Kontext-Menü-Zugriff) als in einem Electron- oder Tauri-Prozess.

## Entscheidungstreiber

- **Modellierungs-Flexibilität**: Komplexe Diagramm-Editierung (Drag & Drop, In-Place-Edit, Auto-Layout, grosse Graphen) profitiert massiv von nativer Plattform-Integration; Browser-Grenzen limitieren das Nutzererlebnis
- **Zugangs-Niedrigschwelligkeit**: Stakeholder, die nur lesen, sollen keinen Installer ausführen müssen; ein URL genügt
- **Canvas-Bibliothek** (ADR-007): Die Wahl der Canvas-Bibliothek unterscheidet sich je nach Laufzeitkontext; die Entscheidung zwischen Web und nativ muss zuerst fallen
- **Offline-Fähigkeit**: Architekten arbeiten teils ohne Serververbindung (lokale Git-Repos, Vor-Ort-Workshops); eine Client App kann offline funktionieren, ein Web Portal nicht
- **Datei-System-Zugriff**: Client App kann lokale Artefakte lesen/schreiben (z.B. Modell-Export als YAML/JSON direkt ins Git-Repo)
- **Entwicklungskosten**: Zwei separate Delivery-Fahrzeuge erhöhen den Pflege-Aufwand; Shared-Component-Strategie (Monorepo, gemeinsame Business-Logik) mildert das
- **OSS-Distribution**: OEA ist Open Source; die Installations-Hürde für eine Client App muss gering sein (keine proprietary Store-Abhängigkeit; direkter Download)

## Betrachtete Optionen

### Option 1: Web-only SPA (ein einziges Web-Frontend für alle Nutzer)

Eine einzelne Vue 3 SPA bedient sowohl Modellierungs- als auch Konsumierungs-Nutzer. Der interaktive Diagramm-Editor läuft im Browser.

- **Pro**:
  - Einzige Codebase für das Frontend; geringster Pflege-Aufwand
  - Deployment: eine URL; kein Installer
  - Kein plattformspezifisches Packaging nötig
- **Contra**:
  - Browser limitiert Canvas-Performance: DOM-Rendering bei grossen EA-Diagrammen (>300 Knoten) reagiert langsam; kein direkter GPU-Zugriff
  - Kein nativer Dateisystem-Zugriff (File System Access API ist experimentell und browserabhängig)
  - Kein Offline-Betrieb ohne Service Worker (hoher Implementierungsaufwand)
  - Modellierungs-Nutzer müssen sich mit einer Oberfläche abfinden, die für Betrachter designt ist (Kompromiss in beide Richtungen)
  - Größte Browser-Inkompatibilitäts-Risiken bei Canvas-Features

### Option 2: Client App (Electron oder Tauri) + Web Portal (SPA) – zwei Delivery-Fahrzeuge

- **Client App**: Vollständiger Modellierungs-Umfang; Zielgruppe SH-03 (Kurt), SH-04 (Michael), SH-07 (Sabine); läuft als installierte Anwendung
- **Web Portal**: Read-only-Publikation; Zielgruppe SH-01 (Franz), SH-02 (Lukas, Konsumierung), SH-05 (CIO), SH-06 (Max); URL-basierter Zugang ohne Installation

- **Pro**:
  - Client App ist maximal flexibel: nativer Speicher, GPU, Dateisystem, Offline, unbegrenzte Fenster/Panels
  - Web Portal bleibt simpel: statisches Rendering, kein komplexer Canvas nötig
  - Canvas-Bibliothek (ADR-007) kann für den Client-App-Kontext optimiert gewählt werden
  - Trennung der Komplexität: Publikations-Code bleibt wartbar; Modellierungs-Code kann wachsen ohne das Portal zu belasten
  - Shared-Component-Library möglich: UI-Atome (Buttons, Tabellen, Typografie) einmal entwickeln, in beiden Apps nutzen
  - Saubere Stakeholder-Segmentierung entspricht der realen Nutzung
- **Contra**:
  - Zwei Deployment-Artefakte (Client-App-Installer + Web-Portal-Deployment) zu pflegen
  - Feature-Parity-Risiko: gleiche Business-Logik (z.B. Katalog-Abfrage) muss in beiden Apps funktionieren → erfordert konsequente API-Trennung (alles über Backend-API, kein Business-Logic-Duplikat im Frontend)
  - Distributions-Aufwand: Client App braucht Signierprozess und plattformspezifische Bundles (macOS, Windows, Linux)
  - UX-Inkonsistenz zwischen den zwei GUIs muss aktiv verhindert werden (Shared Design System)

### Option 3: Progressive Web App (PWA) + Web Portal

Ein PWA versucht, die Lücke zwischen Web und nativer App durch Browser-APIs (Cache, Background Sync, Installation als „App") zu schliessen.

- **Pro**:
  - Eine Codebase; Installations-Möglichkeit über Browser-Mechanismus
  - Begrenzte Offline-Fähigkeit über Service Worker
- **Contra**:
  - PWA-Installation ist kein vollständiger Ersatz für eine native App: kein Dateisystem-Zugriff, kein IPC, kein nativer Kontextmenü-Hook
  - Canvas-Performance bleibt durch Browser-Grenzen limitiert
  - Kein konsistenter plattformübergreifender PWA-Support (insbesondere auf macOS Safari)
  - Offline-Sync mit einem OEA-Backend ist komplexer als bei einer nativen App
  - Löst das Kernproblem (Canvas-Flexibilität) nicht

### Option 4: Native App pro Plattform (macOS / Windows / Linux) + Web Portal

Vollständig native Apps ohne Web-Technologie-Basis.

- **Pro**:
  - Maximale Plattform-Integration und Performance
- **Contra**:
  - Drei separate Codebasen (macOS, Windows, Linux) für dasselbe Werkzeug; massiver Mehraufwand
  - Kein gemeinsamer Code mit dem Web Portal möglich
  - Open-Source-Community-Beiträge werden durch Plattform-spezifisches Know-how-Requirement stark eingeschränkt
  - Scheidet für ein OSS-Projekt dieser Grösse aus

## Entscheidung

Wir wählen **Option 2: Client App + Web Portal (Dual-Track-Delivery)**.

**Client App** (Technologie-Auswahl noch offen, ADR-009 ausstehend) für:
- Solution Architekten (SH-04), Lead Enterprise Architekten (SH-03), Business Engineers (SH-07)
- Interaktive Diagramm-Editierung, Metamodell-Konfiguration, Solution-Management, Katalog-Konfiguration
- Alle Aktionen, die schreibend auf das Repository zugreifen

**Web Portal** (Vue 3 SPA) für:
- CIO (SH-05), Operator (SH-06), Domänen-Betrachter (SH-01, SH-02 in Konsumierungs-Rolle)
- Read-only: Kataloge browsen, Diagramme anschauen, Architektur-Berichte konsumieren
- URL-basierter Zugang ohne Installations-Anforderung

Die Entscheidung folgt dem Prinzip: **jedes Fahrzeug wird für seinen primären Anwendungsfall optimiert, nicht auf einen Kompromiss hin gebaut.**

Business-Logik lebt ausschliesslich im Backend (API-First); beide GUIs sind reine Präsentationsschichten. Dadurch entsteht kein Code-Duplikat.

## Konsequenzen

### Positive Konsequenzen

- **ADR-007 (Canvas-Bibliothek) entschieden**: Da der interaktive Diagramm-Editor in der Client App läuft, wurde Vue Flow als Canvas-Bibliothek gewählt (ADR-007 `accepted`)
- **Web Portal bleibt schlank**: Kein Canvas-Framework im Portal nötig; read-only Diagramm-Rendering kann über ein leichtes SVG-Renderer-Backend erfolgen
- **Klare Feature-Ownership**: jede Story weiß, in welches Fahrzeug sie gehört
- **Offline-Betrieb** für Architekten möglich (Client App kann lokal cachen)
- **Maximale Canvas-Flexibilität** für interaktive Diagramme im Client-App-Kontext

### Negative Konsequenzen / Trade-offs

- **Zwei Distributions-Artefakte**: Client App braucht Packaging (`.dmg`, `.exe`, `.AppImage`), Signierung und ggf. Auto-Update-Mechanismus; zusätzlicher DevOps-Aufwand
- **Shared-Component-Strategie nötig**: Design-System und Business-Component-Library (Tabellen, Filter, Navigationsbaum) müssen von Beginn an in einem gemeinsamen Paket gehalten werden, sonst droht Auseinanderdriften
- **UX-Inkonsistenz-Risiko**: Web Portal und Client App müssen dieselbe Gestaltungssprache sprechen; erfordert aktive Pflege des Design Systems
- **Client-App-Framework-Wahl** (ADR-009) hängt noch aus: Electron (grösser, ausgereifter) vs. Tauri (klein, Rust-Backend, aktive Community)

### Feature-Verteilung (initiale Zuordnung)

| Feature | Client App | Web Portal |
|---|---|---|
| Interaktive Diagramm-Editierung (Canvas) | ✓ | – |
| Diagramme anzeigen (read-only) | ✓ | ✓ |
| Katalog anlegen und konfigurieren | ✓ | – |
| Katalog browsen und filtern | ✓ | ✓ |
| Navigationsbaum verwalten | ✓ | – |
| Navigationsbaum browsen | ✓ | ✓ |
| Solution / EntityDeltas erfassen | ✓ | – |
| Metamodell konfigurieren | ✓ | – |
| Architektur-Repository konsumieren | ✓ | ✓ |
| Login / Auth (UC-01) | ✓ | ✓ |

### Folgeentscheidungen

- **ADR-009** (ausstehend): Client-App-Framework – Electron vs. Tauri; inkl. Auto-Update-Strategie, Packaging, Signierung
- **ADR-007** (deferred → zu öffnen): Canvas-Bibliothek; kann jetzt für Client-App-Kontext entschieden werden
- **ADR-010** (optional): Shared-Component-Library und Design-System-Strategie (Monorepo-Struktur, gemeinsame npm-Pakete)
- **Diagramm-Rendering im Web Portal**: leichtes SVG-basiertes Read-only-Rendering (kein Canvas-Framework) – eigenes REQ zu spezifizieren

## Bezüge

**Konzept-Kapitel**:
- [§21.2.1 Visualisierungs-Strategie](../concept/70-platform/21-api-architektur.md)

**Verwandte ADRs**:
- [ADR-007](./ADR-007-canvas-bibliothek.md): Canvas-Bibliothek (accepted; Vue Flow gewählt; diese Entscheidung hat die Wahl ermöglicht)
- ADR-009 (ausstehend): Client-App-Framework-Wahl

**Use Cases / Requirements**:
- [UC-05: Architektur-Vision beschreiben](../requirements/use-cases/UC-05-architektur-vision-beschreiben.md) – Solution-Editierung in Client App; Read-only-Diff im Web Portal
- [UC-06: Katalog anlegen und verwenden](../requirements/use-cases/UC-06-katalog-anlegen-und-verwenden.md) – Katalog-Konfiguration in Client App; Browsing in beiden
- [US-045: Neue Entität im Diagramm als Delta anlegen](../requirements/user-stories/US-045-delta-neue-entitaet-diagramm.md) – Client-App-Feature; blockiert bis ADR-007 entschieden

## Notizen

Der Begriff „Client App" bezeichnet bewusst keinen spezifischen Framework-Ansatz. Electron und Tauri sind die wahrscheinlichsten Kandidaten (beide packen eine Webview mit nativen Plattformzugriffen), aber die Wahl ist ADR-009 vorbehalten. Andere Ansätze (z.B. .NET MAUI, Flutter Desktop) scheiden wegen des JavaScript/TypeScript-Ökosystem-Fits aus.

Das Web Portal kann langfristig als Einstieg für neue OEA-Instanzen dienen: Wer OEA erst kennenlernen will, schaut sich das Portal an. Wer produktiv arbeitet, installiert die Client App.

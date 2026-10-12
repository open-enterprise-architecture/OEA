---
id: UC-07
title: Dashboard anlegen, konfigurieren und im Web Portal verwenden
status: draft
priority: should
target_release: v1.0
complexity: large
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
primary_actor: SH-03
secondary_actors:
  - SH-01
  - SH-02
  - SH-04
  - SH-05
  - SH-06
  - SH-07
references:
  business_objects:
    - dashboard
    - catalog
    - metamodel-configuration
    - person
    - role
  concept:
    - concept/70-platform/21-api-architektur.md
  related_use_cases:
    - UC-01
    - UC-04
    - UC-06
---

# UC-07: Dashboard anlegen, konfigurieren und im Web Portal verwenden

## Diagramm

```plantuml
@startuml UC-07-Dashboard
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Dashboard-Manager)" as Kurt
actor "Sabine\n(Dashboard-Manager)" as Sabine
actor "CIO" as CIO
actor "Franz / Lukas /\nMichael / Max\n(Betrachter)" as Viewers

rectangle "OEA – Dashboard" {
  usecase "Dashboard anlegen" as UC_Create
  usecase "Widget konfigurieren\n(KPI, Chart, Tabelle)" as UC_Widget
  usecase "DataSource zuordnen\n(Katalog /\nPropertyAggregation)" as UC_DS
  usecase "Dashboard\nveröffentlichen" as UC_Publish
  usecase "Dashboard anzeigen\n(Web Portal)" as UC_View
  usecase "Drilldown / Filter\n(interaktiv)" as UC_Drill
}

Kurt --> UC_Create
Kurt --> UC_Widget
Kurt --> UC_DS
Kurt --> UC_Publish
Sabine --> UC_Create
CIO --> UC_View
Viewers --> UC_View

UC_Create ..> UC_Widget : <<include>>
UC_Widget ..> UC_DS : <<include>>
UC_Create ..> UC_Publish : <<include>>
UC_View ..> UC_Drill : <<extend>>
@enduml
```

## Goal in Context

Entscheidungsträger auf C-Level (CIO, Bereichsleitungen) benötigen verdichtete, aktuelle Kennzahlen aus dem Architektur-Repository – ohne Rohdaten in Katalogen zu lesen oder EA-Werkzeugkenntnisse zu besitzen. Gleichzeitig wollen Enterprise Architekten Investitionskosten-Prognosen, Domänenverteilungen und Portfolio-Metriken auf einen Blick sehen, ohne jedes Mal manuell Abfragen zusammenzustellen.

OEA löst das mit **Dashboards**: konfigurierbaren Ansichten im Web Portal, die KPI-Karten, Charts und Tabellen aus Repository-Daten zusammenstellen. Datenbasis sind entweder [Kataloge](../use-cases/UC-06-katalog-anlegen-und-verwenden.md) (tabellarische Abfragen) oder direkte PropertyAggregationen über EntityTypes mit ihren [PropertyDefinitions](../../business-objects/metamodel-configuration.md).

Dieser Use Case deckt zwei komplementäre Rollen ab:

- **Dashboard-Manager** (SH-03, SH-07): legt Dashboards an, konfiguriert Widgets und DataSources, veröffentlicht als `scope=instance`
- **Dashboard-Betrachter** (alle): öffnet ein bestehendes Dashboard im Web Portal; interaktive Elemente (Drilldown, Datumsfilter) wenn vorhanden

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Weitere Akteure**:
- [CIO](../../business-analysis/stakeholders/SH-05-cio-konzern.md) – primärer Konsument von C-Level-Dashboards; benötigt Investitionskosten-Übersicht und Domänen-Portfolio
- [Franz – Junior Domain Architekt](../../business-analysis/stakeholders/SH-01-franz-junior-domain-architekt.md) – liest Dashboards zur Orientierung
- [Lukas – Senior Data Architekt](../../business-analysis/stakeholders/SH-02-lukas-senior-data-architekt.md) – verfolgt datenbezogene Metriken
- [Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) – erstellt persönliche Dashboards für Solution-Vorbereitung
- [Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md) – liest Compliance-Kennzahlen
- [Sabine – Senior Business Engineer](../../business-analysis/stakeholders/SH-07-sabine-business-engineer.md) – legt fachdomänen-spezifische Dashboards an

**Story Dashboard-Manager (Kurt)**:
Als Lead Enterprise Architekt möchte ich für den CIO ein C-Level-Dashboard mit Investitionskosten-Prognosen pro Plateau und Domänen-Verteilung anlegen, damit der CIO jederzeit im Web Portal den aktuellen Stand des IT-Portfolios einsehen kann – ohne OEA selbst bedienen zu müssen.

**Story Dashboard-Betrachter (CIO)**:
Als CIO möchte ich ein übersichtliches Dashboard mit den wichtigsten Architektur-Kennzahlen im Web Portal aufrufen, damit ich Budgetentscheide auf Basis aktueller Daten treffen kann – in fünf Minuten, ohne EA-Vorkenntnisse.

## Trigger

- Kurt will dem CIO eine aktuelle Investitionskosten-Auswertung über alle Plateaus bereitstellen
- Kurt will die Applikations-Domänen-Verteilung für den nächsten Architecture Review visualisieren
- Michael braucht einen persönlichen Überblick über die von einer Solution betroffenen Systeme
- Der CIO möchte das IT-Portfolio-Dashboard im wöchentlichen Management-Meeting öffnen
- Sabine will Business-Capability-Metriken für einen fachlichen Stakeholder aufbereiten

## Vorbedingungen (Pre-Conditions)

- [ ] Akteur ist eingeloggt (UC-01) und hat Leseberechtigung auf das Repository
- [ ] Für PropertyAggregation-basierte Widgets: Die MetamodelConfiguration enthält EntityTypes mit [PropertyDefinitions](../../business-objects/metamodel-configuration.md) (definiert via UC-04); die relevanten Entitäts-Instanzen haben die Eigenschaftswerte befüllt
- [ ] Für CatalogQuery-basierte Widgets: Mindestens ein [Katalog](../../business-objects/catalog.md) existiert (UC-06)
- [ ] **Für Dashboard-Manager-Aktionen zusätzlich**: Akteur hat die Rolle „Enterprise Architekt" oder eine Rolle mit Dashboard-Schreibberechtigung

## Nachbedingungen (Post-Conditions)

### Bei Erfolg (Dashboard anlegen)

- Ein [Dashboard](../../business-objects/dashboard.md)-Objekt existiert mit allen konfigurierten Widgets und DataSources
- Bei `scope=instance`: Dashboard erscheint im Web Portal für alle Nutzer der Instanz unter dem Dashboard-Menüpunkt
- Bei `scope=personal`: Dashboard ist nur für den Ersteller sichtbar

### Bei Erfolg (Dashboard anzeigen)

- Der Akteur sieht die konfigurierten Widgets mit live berechneten Daten aus dem Repository
- KPI-Karten, Charts und Tabellen spiegeln den aktuellen Datenbankstand wider

### Bei Misserfolg

- Kein Dashboard-Objekt persistiert; Fehlermeldung mit konkretem Hinweis auf die fehlerhafte DataSource oder Konfiguration

## Hauptablauf (Basic Flow)

*Standardfall: Kurt legt das Investitionskosten-Dashboard für den CIO an*

1. **Kurt**: navigiert im Web Portal zu „Dashboards" im Hauptmenü
2. **System**: zeigt eine Übersicht aller vorhandenen Dashboards (`scope=instance` + eigene `personal`), geordnet nach Name
3. **Kurt**: klickt „Neues Dashboard anlegen"
4. **System**: öffnet Konfigurationsformular: Name, Beschreibung, Scope (`instance` / `personal`)
5. **Kurt**: füllt aus: Name = „IT-Investitionsplanung 2026–2030", Scope = `instance`; bestätigt
6. **System**: erstellt das leere Dashboard und öffnet den **Widget-Editor** mit einem 12-Spalten-Raster
7. **Kurt**: klickt „Widget hinzufügen" → wählt Typ `KPI`
8. **System**: öffnet den Widget-Konfigurator: Titel, DataSource-Typ (CatalogQuery / PropertyAggregation), DataSource-Parameter
9. **Kurt**: konfiguriert KPI-Widget:
   - Titel: „Gesamte Investitionskosten (Prognose)"
   - DataSource: `PropertyAggregation` → EntityType: `plateau`, Property: `investitionskostenPrognose`, Aggregation: `sum`
   - Unit: „Mio. CHF", comparisonMode: `none`
10. **Kurt**: platziert das KPI-Widget oben links (Spalten 1–3, Zeile 1); bestätigt
11. **Kurt**: klickt erneut „Widget hinzufügen" → wählt Typ `Chart`
12. **System**: öffnet Chart-Konfigurator: ChartType, DataSource, X-Achse, Y-Achse
13. **Kurt**: konfiguriert Bar-Chart:
    - Titel: „Investitionskosten pro Plateau"
    - ChartType: `bar`
    - DataSource: `PropertyAggregation` → EntityType: `plateau`, Property: `investitionskostenPrognose`, Aggregation: `sum`, GroupBy: `name`
    - X-Achse: Plateau-Name; Y-Achse: Betrag in CHF
14. **Kurt**: platziert das Chart-Widget rechts davon (Spalten 4–12, Zeilen 1–3)
15. **Kurt**: klickt „Widget hinzufügen" → wählt Typ `Chart` → konfiguriert Pie-Chart:
    - Titel: „Applikationen nach Architekturdomäne"
    - ChartType: `pie`
    - DataSource: `PropertyAggregation` → EntityType: `application-component`, Property: `id`, Aggregation: `count`, GroupBy: `architectureDomainId`
16. **Kurt**: platziert das Pie-Chart-Widget (Spalten 1–6, Zeilen 4–6)
17. **Kurt**: klickt „Speichern"
18. **System**: validiert alle DataSources (Typen, Properties, Katalog-Referenzen existieren); berechnet die Daten live
19. **System**: zeigt das fertige Dashboard mit den berechneten Werten; zeigt Hinweise für Widgets mit fehlenden Property-Werten
20. **System**: veröffentlicht das Dashboard für alle Nutzer der Instanz im Web Portal

## Alternative Abläufe (Alternative Flows)

**A1 – Dashboard anzeigen / betrachten (alle Akteure, Web Portal)**:

*Der CIO öffnet das IT-Investitionskosten-Dashboard im Management-Meeting*

1. **CIO**: öffnet das Web Portal im Browser (kein Login erforderlich, sofern öffentliche Instanz; sonst UC-01)
2. **System**: zeigt die Dashboard-Übersicht
3. **CIO**: klickt „IT-Investitionsplanung 2026–2030"
4. **System**: rendert alle Widgets mit aktuellen Daten; zeigt Ladeanimation während Berechnung
5. **CIO**: betrachtet KPI und Bar-Chart; fährt mit der Maus über einen Balken → Tooltip zeigt genauen Wert
6. **System**: zeigt Tooltip mit Detailwert; keine weiteren Interaktionen im Standard-Betrachter-Modus

**A2 – Persönliches Dashboard anlegen (alle Akteure)**:

1. Akteur legt ein neues Dashboard an (wie Hauptablauf Schritte 3–5); wählt `scope=personal`
2. **System**: erstellt das Dashboard; es ist ausschliesslich für den Akteur sichtbar
3. Akteur kann den persönlichen Dashboard später auf `scope=instance` hochstufen, sofern er die Berechtigung hat

**A3 – Table-Widget mit Katalog als DataSource konfigurieren**:

*Kurt möchte die Top-10-teuersten Applikationen direkt als Tabelle im Dashboard einbetten*

1. Kurt wählt Widget-Typ `Table`
2. **System**: zeigt DataSource-Auswahl: nur `CatalogQuery` verfügbar für Table-Widgets
3. Kurt wählt einen bestehenden Katalog „Application Inventory" (UC-06) als DataSource; wählt optional eine SavedView
4. **System**: zeigt eine Vorschau der ersten Zeilen
5. Kurt konfiguriert `pageSize=10` und platziert das Widget
6. **System**: rendert die Tabelle live mit den Katalogergebnissen, inkl. Paginierung

**A4 – Bestehendes Dashboard bearbeiten (Dashboard-Manager)**:

1. Kurt öffnet ein bestehendes Dashboard; klickt „Bearbeiten"
2. **System**: öffnet den Widget-Editor mit den bestehenden Widgets
3. Kurt passt Widgets an (DataSource ändern, Titel umbenennen, Layout anpassen), fügt neue Widgets hinzu oder entfernt vorhandene
4. Kurt speichert; **System** validiert und aktualisiert sofort für alle Betrachter

**A5 – Text-Widget hinzufügen (Dashboard-Manager)**:

1. Kurt wählt Widget-Typ `Text`
2. **System**: öffnet Markdown-Editor
3. Kurt schreibt einen Erklärungs-Text (z.B. Methodische Hinweise zur Kosten-Schätzung) in Markdown
4. Kurt platziert und speichert; das Widget erscheint als formatierter Text ohne Datenbindung

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – PropertyDefinition auf EntityType nicht gefunden**:
- Bedingung: Ein Widget referenziert `propertyName=investitionskostenPrognose` auf EntityType `plateau`, aber dieses Property existiert in der aktuellen MetamodelConfiguration nicht (z.B. nach einer Metamodell-Änderung via UC-04)
- Erwartete Reaktion: Das Widget zeigt einen Fehlerhinweis „Property ‹investitionskostenPrognose› auf EntityType ‹plateau› nicht gefunden. Bitte Dashboard-Konfiguration prüfen." Andere Widgets werden weiterhin gerendert
- Wiederaufnahme: Dashboard-Manager öffnet den Widget-Editor und wählt ein gültiges Property

**E2 – Referenzierter Katalog nicht mehr vorhanden**:
- Bedingung: Ein Table-Widget referenziert einen Katalog, der zwischenzeitlich gelöscht wurde
- Erwartete Reaktion: Das Widget zeigt „Katalog ‹Application Inventory› nicht gefunden." Hinweis auf UC-06
- Wiederaufnahme: Dashboard-Manager wählt einen anderen Katalog oder legt den Katalog neu an

**E3 – Fehlende Berechtigung für instance-Dashboard**:
- Bedingung: Akteur ohne Dashboard-Schreibberechtigung versucht, ein `scope=instance`-Dashboard anzulegen
- Erwartete Reaktion: Scope-Auswahl zeigt nur `personal`; direkte API-Aufrufe mit `scope=instance` werden mit 403 abgelehnt
- Wiederaufnahme: Akteur legt ein persönliches Dashboard an oder wendet sich an einen Dashboard-Manager

**E4 – Keine Daten für Aggregation**:
- Bedingung: Der EntityType `plateau` enthält keine Entitäten, oder keine der Plateau-Instanzen hat den Property-Wert `investitionskostenPrognose` befüllt
- Erwartete Reaktion: KPI-Widget zeigt „– (keine Daten)"; Chart-Widget zeigt leeres Diagramm mit Hinweis „Keine Daten vorhanden. Property ‹investitionskostenPrognose› auf den vorhandenen Entitäten pflegen."
- Wiederaufnahme: kein Block; Widget zeigt Daten sobald Properties befüllt sind

## Datenfluss

| Schritt | Daten | Richtung | Bemerkung |
|---|---|---|---|
| 4–17 | Dashboard-Konfiguration (name, scope, widgets, dataSource-Definitionen, gridPositions) | Manager → System | Persistiert als Dashboard-Objekt |
| 18 | DataSource-Validierung (EntityType + PropertyName, Katalog-Referenz) | System → System | Serverseitig; keine Persistenz |
| 19 | Aggregierte Metrik-Werte, Chart-Datenpunkte, Tabellenzeilen | System → Akteur | Live-Berechnung auf Basis Repository-Stand; nicht persistiert |
| A1 Schritt 5 | Tooltip-Daten (Einzelwert) | System → CIO | On-Demand beim Hover; nicht persistiert |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [dashboard](../../business-objects/dashboard.md) | create, read, update | Kern-Objekt; enthält alle Widget- und DataSource-Definitionen |
| [catalog](../../business-objects/catalog.md) | read | DataSource für Table-Widgets (CatalogQueryDataSource) |
| [metamodel-configuration](../../business-objects/metamodel-configuration.md) | read | Liefert verfügbare EntityTypes und PropertyDefinitions für Konfigurator-Dropdowns |
| [person](../../business-objects/person.md) | read | Authentifizierung; `createdBy` des Dashboards |
| [role](../../business-objects/role.md) | read | Berechtigungsprüfung: Lesen vs. Anlegen/Bearbeiten |

## Akzeptanzkriterien

### Dashboard anlegen und konfigurieren

- [ ] Dashboard mit beliebigem Namen und Scope (`instance` / `personal`) anlegbar
- [ ] Alle vier Widget-Typen (KPI, Chart, Table, Text) hinzufügbar und konfigurierbar
- [ ] KPI-Widget: DataSource `PropertyAggregation` mit allen Aggregationsfunktionen (sum, count, avg, min, max) wählbar; Unit und comparisonMode konfigurierbar
- [ ] Chart-Widget: alle ChartTypes (bar, line, pie, donut, area, treemap) wählbar; X- und Y-Achsen konfigurierbar; Legende und DataLabels togglebar
- [ ] Table-Widget: bestehendem Katalog als DataSource wählbar; SavedView auswählbar; pageSize konfigurierbar
- [ ] Widget-Positionierung im 12-Spalten-Raster per Drag-and-Drop
- [ ] System validiert alle DataSources beim Speichern; ungültige Referenzen erzeugen eine Fehlermeldung

### Datenrendering

- [ ] Widgets rendern live berechnete Daten aus dem aktuellen Repository-Stand
- [ ] PropertyAggregation: `sum`, `count`, `avg`, `min`, `max` über Entitäten eines Typs mit korrekter Groupierung
- [ ] PropertyAggregation mit `groupBy=architectureDomainId` liefert eine Reihe pro Domain
- [ ] Pie- / Donut-Chart mit einer Y-Achse korrekt gerendert
- [ ] Table-Widget rendert dieselben Zeilen wie der referenzierte Katalog (mit SavedView-Filter)

### Berechtigungen und Sichtbarkeit

- [ ] Dashboards mit `scope=instance` sind für alle eingeloggten Nutzer im Web Portal sichtbar
- [ ] Dashboards mit `scope=personal` sind ausschliesslich für den Ersteller sichtbar; keine Datenlecks
- [ ] Anlegen und Bearbeiten von `scope=instance`-Dashboards erfordert Dashboard-Schreibberechtigung; Direktaufruf der API ohne Berechtigung: 403

### Fehlerbehandlung

- [ ] E1: Widget mit ungültiger PropertyDefinition zeigt isolierten Fehlerhinweis; übrige Widgets bleiben funktionsfähig
- [ ] E2: Widget mit fehlendem Katalog zeigt isolierten Fehlerhinweis
- [ ] E4: Widget ohne Datenbasis zeigt „Keine Daten"-Meldung mit Hinweis auf fehlende Property-Werte

## Nicht im Scope

- **Dashboard löschen / archivieren**: Admin-Funktion; separater UC oder Admin-Panel
- **Drilldown / Detailansicht aus Widget**: Klick auf Chart-Element öffnet gefilterten Katalog oder Entity-Detail (Erweiterungskandidat v1.x)
- **PDF-/PNG-Export von Dashboards**: Reporting-Export ist separater UC; Dashboard selbst ist live
- **Berechtigungen pro Dashboard** (bestimmte Nutzer ausschliessen): aktuell instance=alle / personal=Ersteller; feingranulare Berechtigungen TBD
- **Echtzeit-Updates** (automatisches Refresh alle N Sekunden): v1.0 = manueller Reload; Echtzeit ist Erweiterungskandidat
- **Eingebettetes Diagramm** (Architektur-Notation im Dashboard): Dashboard zeigt Business-Metriken in Standard-Charts; Architektur-Diagramme bleiben im Canvas/Viewpoint
- **Berechnung von Formeln über mehrere Properties** (z.B. Kosten/Entität-Anzahl): v1.0 nur direkte Property-Aggregation; abgeleitete Metriken sind Erweiterungskandidat
- **Globale Filter mit Datumsbereich** (z.B. „nur Plateaus ab 2027"): v1.0 nur globale PropertyAggregation-Filter; datumsseitige Einschränkungen TBD

## Konzept-Bezüge

- [§21.2.1 Visualisierungs-Strategie](../../concept/70-platform/21-api-architektur.md) – Web Portal als Publikations- und Reporting-Plattform

## Realisierungs-Hinweise

- Dashboard-Daten werden bei jedem Aufruf live berechnet; keine Caching-Schicht in v1.0 (Performance-NFR ist noch zu definieren)
- PropertyAggregation-Queries: serverseitig als Aggregations-API auf dem Entity-Store; keine Client-seitige Berechnung
- CatalogQueryDataSource übernimmt die Abfrage-Logik des referenzierten Katalogs vollständig (inkl. Joins, SavedView-Filter); das Dashboard delegiert an die Katalog-API
- Rendering im Web Portal: Standard-Chart-Bibliothek (Auswahl noch offen, vermutlich recharts oder chart.js); kein Canvas/React Flow benötigt (reine SVG/Canvas-Charts)
- Grid-Layout: CSS Grid oder react-grid-layout als Grundlage; Drag-and-Drop nur im Edit-Modus der Client App; im Web Portal nur Anzeige

## Realisierende Bestandteile

- Requirements: [REQ-051](../req/REQ-051-dashboard-anlegen.md), [REQ-052](../req/REQ-052-widget-konfigurieren.md), [REQ-053](../req/REQ-053-property-aggregation-datasource.md), [REQ-054](../req/REQ-054-catalog-query-datasource.md), [REQ-055](../req/REQ-055-dashboard-daten-abrufen.md), [REQ-056](../req/REQ-056-dashboard-zugriff-sichtbarkeit.md), [REQ-057](../req/REQ-057-widget-grid-layout.md)
- User Stories: [US-055](../user-stories/US-055-dashboard-anlegen.md), [US-056](../user-stories/US-056-kpi-widget-konfigurieren.md), [US-057](../user-stories/US-057-chart-widget-konfigurieren.md), [US-058](../user-stories/US-058-table-widget-konfigurieren.md), [US-059](../user-stories/US-059-text-widget-hinzufuegen.md), [US-060](../user-stories/US-060-widget-layout-anpassen.md), [US-061](../user-stories/US-061-dashboard-im-webportal-anzeigen.md), [US-062](../user-stories/US-062-persoenliches-dashboard.md)
- ADRs: –
- Test Cases: noch keine
- Implementation: noch keine

## Offene Fragen

- [ ] Sollen Dashboards auch in der Client App (Electron) konfigurierbar sein, oder ausschliesslich im Web Portal? Der Anwendungsfall (C-Level-Reporting) spricht für Web Portal als Primärort; Konfiguration könnte dennoch in der Client App angeboten werden.
- [ ] Braucht der CIO ein Login ins Web Portal, oder sind Dashboards öffentlich lesbar (URL-basierter Zugang ohne Auth)? Das hängt von der Datenschutz-Klassifikation der Architektur-Daten ab (ADR-001–005 offen).
- [ ] Welche Chart-Bibliothek wird für das Web Portal verwendet? Keine eigene ADR nötig (kein strategischer Entscheid), aber zu klären vor Implementierung.
- [ ] Unterstützt v1.0 auch negative Zahlen in PropertyAggregation (z.B. Einsparungen)?

## Notizen

Der Unterschied zu [UC-06 (Katalog)](./UC-06-katalog-anlegen-und-verwenden.md) ist grundlegend: Ein Katalog ist eine **Zeilenansicht** mit konfigurierbaren Spalten – jede Entität ist eine Zeile. Ein Dashboard ist eine **Verdichtungsansicht** – Daten werden aggregiert, gruppiert und als Kennzahlen oder Charts dargestellt. Beide Konzepte sind komplementär: ein Dashboard kann einen Katalog als DataSource für eine eingebettete Tabelle nutzen, aber auch direkt PropertyAggregations auf EntityTypes berechnen.

Die Verbindung zu den [PropertyDefinitions](../../business-objects/metamodel-configuration.md) (v0.6.0) ist die Schlüssel-Voraussetzung für aussagekräftige Dashboards: Erst wenn Eigenschaften wie `investitionskostenPrognose`, `betriebskostenPrognose` oder `lifecycleStatus` als typisierte, validierte Properties am EntityType definiert und in den Entitäts-Instanzen befüllt sind, können Dashboards sinnvolle Aggregationen liefern. Dashboard und Metamodell-Pflege (UC-04) sind eng verknüpft.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; Hauptablauf Investitionskosten-Dashboard, A1–A5, Akzeptanzkriterien, Nicht-im-Scope |

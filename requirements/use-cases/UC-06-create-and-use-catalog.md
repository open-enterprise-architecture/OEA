---
id: UC-06
title: Architektur-Katalog anlegen, konfigurieren und verwenden
status: draft
priority: must
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
    - catalog
    - tree-node
    - metamodel-configuration
    - person
    - role
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
    - concept/20-entities/12-domain-sichten.md
  related_use_cases:
    - UC-01
    - UC-04
    - UC-05
---

# UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden

## Diagramm

```plantuml
@startuml UC-06-Katalog
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Katalog-Manager)" as Kurt
actor "Sabine\n(Katalog-Manager)" as Sabine
actor "Franz / Lukas /\nMichael / CIO / Max\n(Besucher)" as Visitors

rectangle "OEA – Architektur-Katalog" {
  usecase "Katalog anlegen" as UC_Create
  usecase "Spalten konfigurieren" as UC_Cols
  usecase "Join-Definition\nkonfigurieren" as UC_Join
  usecase "Saved View speichern" as UC_SavedView
  usecase "Katalog öffnen &\nabfragen" as UC_Query
  usecase "Filter setzen &\nspeichern" as UC_Filter
  usecase "Join-Modus wechseln\n(Laufzeit)" as UC_JoinRT
}

Kurt --> UC_Create
Kurt --> UC_Cols
Kurt --> UC_Join
Kurt --> UC_SavedView
Kurt --> UC_Query
Sabine --> UC_Create
Sabine --> UC_Cols
Visitors --> UC_Query
Visitors --> UC_Filter
Visitors --> UC_JoinRT

UC_Create ..> UC_Cols : <<include>>
UC_Create ..> UC_Join : <<include>>
UC_Query ..> UC_Filter : <<extend>>
UC_Query ..> UC_JoinRT : <<extend>>
@enduml
```

## Goal in Context

Das Architecture-Repository enthält im laufenden Betrieb hunderte Entitäten unterschiedlicher Typen mit ihren Attributen und Verbindungen. Ohne strukturierte Sichten sind diese Daten schwer konsumierbar: für Compliance-Audits, Reporting an die Geschäftsleitung, die Vorbereitung von Architecture Reviews oder die tägliche Wartung der Landschaft.

OEA löst dieses Problem mit **Katalogen**: benannten, wiederverwendbaren Tabellenansichten auf das Repository, die Entitäten gezielt filtern, Attribute als Spalten konfigurieren und verwandte Entitäten über Connections joinen – analog zu parametrisierten SQL-Abfragen, aber ohne SQL-Kenntnisse.

Dieser Use Case deckt zwei komplementäre Rollen ab:

- **Katalog-Manager** (SH-03, SH-07): legt Kataloge an, konfiguriert Spalten, Joins, gespeicherte Filter und gespeicherte Views, platziert den Katalog im Navigationsbaum
- **Katalog-Besucher** (alle): öffnet einen bestehenden Katalog, filtert und sortiert, wechselt den Join-Modus und speichert eigene Filter-Kombinationen

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Weitere Akteure (Nutzung als Besucher)**:
- [Franz – Junior Domain Architekt](../../business-analysis/stakeholders/SH-01-franz-junior-domain-architekt.md) – liest Kataloge, um die Landschaft zu verstehen
- [Lukas – Senior Data Architekt](../../business-analysis/stakeholders/SH-02-lukas-senior-data-architekt.md) – nutzt datenzentrierte Kataloge für datenarchitektonische Analysen
- [Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) – nutzt Kataloge zur Ausgangsbasis-Analyse vor der Solution-Erstellung
- [CIO](../../business-analysis/stakeholders/SH-05-cio-konzern.md) – konsumiert Executive-Kataloge als Entscheidungsgrundlage
- [Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md) – nutzt Compliance-Kataloge für Audit-Vorbereitung
- [Sabine – Senior Business Engineer](../../business-analysis/stakeholders/SH-07-sabine-business-engineer.md) – legt domänspezifische Kataloge für Business-Analyse an

**Story Katalog-Manager (Kurt)**:
Als Lead Enterprise Architekt möchte ich wiederverwendbare Tabellenansichten auf das Architecture-Repository anlegen, damit alle Beteiligten konsistente, vorgefilterte Sichten auf die für sie relevanten Entitäten haben – ohne jedes Mal neu filtern und konfigurieren zu müssen.

**Story Katalog-Besucher (alle)**:
Als Nutzer des Architecture-Repositorys möchte ich einen bestehenden Katalog öffnen, live filtern und den Join-Modus anpassen – damit ich gezielt die Informationen finde, die ich für meine aktuelle Aufgabe brauche.

## Trigger

- Kurt möchte eine standardisierte Application-Inventory-Sicht für alle EA-Nutzer bereitstellen
- Sabine braucht eine domänspezifische Sicht auf Geschäftsprozesse und ihre Schnittstellen
- Franz öffnet einen Katalog, um sich die bestehende IT-Landschaft zu erschliessen
- Michael sucht vor der Erstellung einer Solution nach betroffenen Systemen (komplementär zu UC-05 / REQ-039)
- Max bereitet einen Compliance-Audit vor und braucht eine filterbare Systemübersicht

## Vorbedingungen (Pre-Conditions)

- [ ] Akteur ist eingeloggt (UC-01) und hat eine Rolle mit Leseberechtigung auf das Repository
- [ ] Die MetamodelConfiguration der Instanz enthält mindestens einen EntityType (UC-04)
- [ ] Das Repository enthält mindestens eine Entity (für sinnvolle Ergebnisse; Katalog ist auch bei leerem Repository anlegbar)
- [ ] **Für Katalog-Manager-Aktionen zusätzlich**: Akteur hat die Rolle „Enterprise Architekt" oder eine Rolle mit Schreibberechtigung für Kataloge

## Nachbedingungen (Post-Conditions)

### Bei Erfolg (Katalog anlegen)

- Ein [Catalog](../../business-objects/catalog.md)-Objekt existiert mit allen konfigurierten Spalten, Joins und SavedFilters
- Der Katalog ist, sofern `scope=instance`, für alle Nutzer der Instanz sichtbar
- Optional: der Katalog ist einem [TreeNode](../../business-objects/tree-node.md) im Navigationsbaum hinzugefügt

### Bei Erfolg (Katalog besuchen)

- Der Akteur sieht die Entitäten des Catalogs mit den konfigurierten Spalten und aktiven Filtern
- Optional gespeicherte Filter / Views sind persistiert und beim nächsten Öffnen verfügbar

### Bei Misserfolg

- Kein Katalog-Objekt wurde persistiert; Fehlermeldung mit konkretem Hinweis

## Hauptablauf (Basic Flow)

*Standardfall: Kurt legt einen neuen Katalog „Application Inventory" an und konfiguriert ihn für alle Nutzer*

1. **Kurt**: navigiert zu „Kataloge" im Hauptmenü oder im Navigationsbaum
2. **System**: zeigt eine Übersicht aller vorhandenen Kataloge (scope=instance + eigene personal), geordnet nach Name
3. **Kurt**: klickt „Neuen Katalog anlegen"
4. **System**: öffnet Konfigurationsformular: Name, Beschreibung, primärer Entitätstyp (Dropdown aus MetamodelConfiguration), Scope (instance / personal), Default-Join-Modus (rows / aggregate)
5. **Kurt**: wählt `ApplicationComponent` als primären Typ; setzt Name = „Application Inventory", Scope = `instance`, Default-Join-Modus = `aggregate`
6. **System**: zeigt den **Spalten-Konfigurator**: alle Attribute des gewählten EntityTypes als verfügbare Spalten (inkl. Built-in-Attribute: name, status, description, createdAt, sowie alle Custom-Attribute aus dem Metamodell)
7. **Kurt**: wählt Spalten aus, ordnet sie per Drag-and-Drop, setzt optional abweichende Spalten-Labels (z.B. `name` → „Applikationsname")
8. **Kurt**: klickt „Join hinzufügen"
9. **System**: öffnet Join-Konfigurator: Connection-Typ (Dropdown: alle Connection-EntityTypes, die `ApplicationComponent` als Source haben), Zielentitätstyp, Join-Richtung, Spalten der Zielentität, Default-Join-Modus dieses Joins
10. **Kurt**: wählt Connection-Typ `DataFlow` → Zielentität `Interface`, Spalten: name + protocol, Join-Modus: `aggregate`
11. **Kurt**: speichert den Katalog
12. **System**: persistiert den Catalog; zeigt die **Live-Katalog-Ansicht** mit den Entitäten aus dem Repository
13. **Kurt**: legt einen SavedFilter an: klickt „Filter speichern" → Name „Nur aktive Systeme", Expression: `status eq 'active'`
14. **Kurt**: legt eine SavedView an: klickt „Ansicht speichern" → Name „Kompaktansicht", wählt nur Spalten name + status, aktiviert Filter „Nur aktive Systeme", isDefault = true
15. **Kurt**: platziert den Katalog im Navigationsbaum: zieht ihn in den Ordner „IT-Landschaft" oder wählt über das Kontextmenü „In Baum einhängen"
16. **System**: erstellt einen [TreeNodeItem](../../business-objects/tree-node.md)-Eintrag im gewählten Ordner; der Katalog erscheint im Navigationsbaum

## Alternative Abläufe (Alternative Flows)

**A1 – Katalog als Besucher öffnen und nutzen (alle Akteure)**:

*Franz öffnet den Katalog „Application Inventory", um sich die bestehende IT-Landschaft zu erschliessen*

1. **Franz**: öffnet den Katalog über den Navigationsbaum oder die Katalog-Übersicht
2. **System**: zeigt die Standard-Ansicht (isDefault-SavedView, sofern definiert): Entitäten als Tabellenzeilen, Spalten gemäss Konfiguration
3. **Franz**: sortiert die Tabelle nach einer Spalte (Klick auf Spalten-Header)
4. **Franz**: aktiviert einen vorhandenen SavedFilter „Nur aktive Systeme" per Schnellauswahl
5. **System**: filtert die Ergebnisse live; zeigt nur Entitäten die der Filterbedingung entsprechen
6. **Franz**: setzt einen ad-hoc-Filter zusätzlich (z.B. `layer = 'application'`) ohne ihn zu speichern
7. **System**: wendet auch den Ad-hoc-Filter an; Ergebnisliste aktualisiert sich
8. **Franz**: wechselt für den Join „Schnittstellen" den Modus von `aggregate` zu `rows`
9. **System**: stellt die Tabelle auf Zeile-pro-Interface um; die Hauptentität wiederholt sich für jede Schnittstelle

**A2 – Ad-hoc-Filter speichern (alle Akteure)**:

1. Besucher hat einen Ad-hoc-Filter gesetzt (wie in A1, Schritt 6)
2. Besucher klickt „Filter speichern"
3. **System**: fragt nach Name und ob der Filter `scope=personal` (nur für mich) oder, wenn Besucher Katalog-Manager-Berechtigung hat, `scope=instance` (für alle)
4. Besucher bestätigt; der Filter erscheint ab sofort in der Schnellauswahl

**A3 – Bestehenden Katalog bearbeiten (Katalog-Manager)**:

1. Kurt öffnet den Katalog; klickt „Konfigurieren"
2. **System**: öffnet den Konfigurations-Editor (identisch mit dem Erstellungsformular, vorausgefüllt)
3. Kurt ändert Spalten, fügt einen weiteren Join hinzu oder entfernt einen
4. Kurt speichert; **System** wendet die Änderungen sofort auf alle Besucher-Sichten an
5. Alle bestehenden SavedViews bleiben erhalten; Spalten-Referenzen die nicht mehr existieren werden aus SavedViews entfernt und der Manager informiert

**A4 – Persönlichen Katalog anlegen (alle Akteure)**:

1. Akteur legt einen neuen Katalog an (wie Hauptablauf Schritt 3–11); wählt `scope=personal`
2. **System**: erstellt den Katalog; er ist nur für den Akteur selbst sichtbar
3. Akteur kann den persönlichen Katalog später auf `scope=instance` hochstufen (falls er die Berechtigung hat)

**A5 – Spalten eines Joins konfigurieren (Detail)**:

1. Beim Konfigurieren eines Joins (Hauptablauf Schritt 9–10) wählt Kurt welche Attribute der Zielentität als Spalten erscheinen sollen
2. Im `aggregate`-Modus: die Ziel-Attribute werden komma-separiert oder als Unter-Tabelle in einer Zelle dargestellt
3. Im `rows`-Modus: jedes Ziel-Attribut wird zur eigenen Spalte; die primäre Entität wiederholt sich pro Ergebniszeile

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – Primärer Entitätstyp nicht mehr im Metamodell vorhanden**:
- Bedingung: Der EntityType eines bestehenden Catalogs wurde aus der MetamodelConfiguration entfernt (UC-04)
- Erwartete Reaktion: Beim Öffnen des Catalogs erscheint eine Warnung „Primärer Typ ‹ApplicationComponent› nicht mehr im Metamodell. Der Katalog kann keine Daten laden." Mit Hinweis auf UC-04
- Wiederaufnahme: Katalog-Manager muss den Katalog manuell auf einen gültigen Typ migrieren oder löschen

**E2 – Kein EntityType-Attribut für gewählte FilterExpression**:
- Bedingung: Ein SavedFilter referenziert ein Attribut, das aus dem EntityType entfernt wurde
- Erwartete Reaktion: SavedFilter wird mit Warnung-Icon markiert; der Filter kann nicht ausgeführt werden; Manager wird informiert
- Wiederaufnahme: Manager passt den SavedFilter an

**E3 – Fehlende Berechtigung für Katalog-Manager-Aktionen**:
- Bedingung: Besucher ohne Schreibberechtigung versucht, einen Katalog anzulegen oder zu bearbeiten
- Erwartete Reaktion: Schaltfläche „Neuen Katalog anlegen" / „Konfigurieren" nicht sichtbar oder deaktiviert; bei direktem API-Zugriff: 403 Forbidden
- Wiederaufnahme: Besucher wendet sich an einen Katalog-Manager

**E4 – Leeres Repository (kein Ergebnis)**:
- Bedingung: Der primäre Entitätstyp hat keine Entitäten im Repository
- Erwartete Reaktion: Tabelle mit 0 Zeilen; informativer Hinweis „Keine Einträge gefunden. Entitäten des Typs ‹ApplicationComponent› können in einer Solution als Delta erfasst werden (UC-05)."
- Wiederaufnahme: kein Block; Katalog bleibt nutzbar sobald Entitäten vorhanden sind

## Datenfluss

| Schritt | Daten | Richtung | Bemerkung |
|---|---|---|---|
| 4–11 | Catalog-Konfiguration (name, primaryEntityType, columns, joinDefinitions, defaultJoinMode, scope) | Manager → System | Persistiert als Catalog-Objekt |
| 12 | Entitätsliste mit Attributwerten und Join-Ergebnissen | System → Akteur | Live-Abfrage auf Repository; nicht persistiert |
| 13 | SavedFilter (name, expressions) | Manager → System | Persistiert im Catalog-Objekt |
| 14 | SavedView (name, columnOrder, activeFilterIds, joinModeOverrides, isDefault) | Manager → System | Persistiert im Catalog-Objekt |
| 15–16 | TreeNodeItem (catalogId, parentNodeId, sortOrder) | Manager → System | Persistiert im TreeNode |
| A1 Schritt 8 | Join-Modus-Override (rows/aggregate) | Besucher → System | Laufzeit-Override; optional persistent als SavedView |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [catalog](../../business-objects/catalog.md) | create, read, update | Kern-Objekt; enthält ColumnConfig, JoinDefinitions, SavedFilters, SavedViews |
| [tree-node](../../business-objects/tree-node.md) | update | Katalog wird per TreeNodeItem in Baum eingehängt |
| [metamodel-configuration](../../business-objects/metamodel-configuration.md) | read | Liefert verfügbare EntityTypes, Attribute, Connection-Typen für Konfiguration |
| [person](../../business-objects/person.md) | read | Authentifizierung; `createdBy`, `lastModifiedBy` des Catalogs |
| [role](../../business-objects/role.md) | read | Berechtigungsprüfung: Lesen vs. Konfigurieren |

## Akzeptanzkriterien

### Katalog anlegen und konfigurieren

- [ ] Katalog mit beliebigem EntityType als primärer Entität anlegbar; alle Attribute des Typs sind als Spalten verfügbar
- [ ] Spalten können ausgewählt, geordnet und mit eigenem Label versehen werden
- [ ] Mind. ein Join über einen Connection-EntityType (`isConnection=true`) ist konfigurierbar; Richtung (outbound/inbound/both) ist wählbar
- [ ] Default-Join-Modus (`rows` / `aggregate`) ist auf Katalog-Ebene und pro JoinDefinition konfigurierbar

### Filter und Views

- [ ] Ad-hoc-Filter können gesetzt und als benannte SavedFilter gespeichert werden
- [ ] SavedFilters unterstützen mind. die Operatoren: eq, neq, contains, in, isNull, isNotNull
- [ ] SavedFilters können kombiniert werden (AND / OR)
- [ ] SavedViews speichern Spaltenreihenfolge + aktive Filter + Join-Modus-Overrides
- [ ] Eine SavedView kann als Default markiert werden (isDefault=true); nur eine pro Katalog
- [ ] Besucher ohne Manager-Berechtigung können persönliche SavedFilters und SavedViews anlegen

### Katalog besuchen

- [ ] Alle Akteure können Kataloge mit scope=instance öffnen
- [ ] Spalten sind per Klick auf den Header sortierbar (auf- und absteigend)
- [ ] Join-Modus ist pro Join zur Laufzeit zwischen `rows` und `aggregate` umschaltbar
- [ ] Wechsel des Join-Modus aktualisiert die Tabelle ohne Seiten-Reload
- [ ] Im `aggregate`-Modus erscheinen alle verknüpften Objekte in einer Zelle; im `rows`-Modus entsteht eine Zeile pro Kombination

### Navigationsbaum

- [ ] Katalog kann einem TreeNode (Ordner) im Navigationsbaum zugewiesen werden
- [ ] Derselbe Katalog kann in mehreren TreeNodes verlinkt werden (Soft-Reference)
- [ ] Katalog ist über den Navigationsbaum direkt öffenbar

### Sicherheit

- [ ] Anlegen, Bearbeiten und Löschen von Katalogen erfordert die Rolle „Enterprise Architekt" oder eine vergleichbare Schreibberechtigung
- [ ] Kataloge mit scope=personal sind nur für den Ersteller sichtbar; kein Datenleck auf andere Nutzer
- [ ] E3: Direkte API-Aufrufe ohne Berechtigung werden mit 403 abgelehnt

## Nicht im Scope

- **Katalog löschen / archivieren**: Admin-Funktion; separater UC oder Admin-Panel
- **Export (PDF, CSV) des Katalog-Inhalts**: separater Export-UC; der Katalog selbst ist die persistierbare Abfrage
- **Berechtigungen pro Katalog** (z.B. bestimmte Nutzer ausschliessen): Berechtigungskonzept noch offen; aktuell instance = alle, personal = nur Ersteller
- **Aggregationsfunktionen im `aggregate`-Modus** (z.B. COUNT, SUM): aktuell nur List-Aggregation; numerische Aggregation ist ein Erweiterungskandidat
- **Volltext-Suche über mehrere Kataloge**: Repository-weite Suche ist ein eigener UC
- **Pivot-Ansicht** (Spalten und Zeilen tauschen): nicht im Scope v1.0
- **Versionierung von Katalog-Konfigurationen**: TBD
- **Plateaus und Go-Live-Übergänge**: künftiger UC (TBD)

## Konzept-Bezüge

- [§6 Kern-Entitätstypen](../../concept/20-entities/06-kern-entitaetstypen.md) – EntityTypes und ihre Attribute die in Katalogen abgefragt werden
- [§12 Domain-Sichten](../../concept/20-entities/12-domain-sichten.md) – Kataloge als tabellarische Ergänzung zu graphischen Domain-Sichten

## Realisierungs-Hinweise

- Katalog-Abfrage: serverseitig als parametrisiertes Query-Objekt realisiert; die Join-Logik folgt der Connection-Semantik des Repositorys (nicht SQL-JOIN auf DB-Ebene, sondern Traversierung des Entity-Graphen)
- `aggregate`-Modus: alle verbundenen Entitäten werden serverseitig pro Haupt-Entität gesammelt und als Array geliefert; das Frontend rendert als komma-separierte Liste oder eingebettete Tabelle
- `rows`-Modus: das Backend expandiert die Liste; eine Haupt-Entität mit N verbundenen Entitäten ergibt N Zeilen im Response
- Laufzeit-Filter werden als Query-Parameter übergeben; sie verändern die gespeicherte Katalog-Konfiguration nicht
- SavedViews und SavedFilters sind im Catalog-Objekt eingebettet (kein eigenes Repository); bei scope=personal-Katalogen gilt das für alle darin gespeicherten Views/Filter

## Realisierende Bestandteile

- Requirements: [REQ-043](../req/REQ-043-katalog-anlegen.md), [REQ-044](../req/REQ-044-spalten-konfigurieren.md), [REQ-045](../req/REQ-045-join-definition-konfigurieren.md), [REQ-046](../req/REQ-046-katalog-abfrage.md), [REQ-047](../req/REQ-047-filter-setzen-und-speichern.md), [REQ-048](../req/REQ-048-saved-view.md), [REQ-049](../req/REQ-049-join-modus-laufzeit.md), [REQ-050](../req/REQ-050-katalog-navigationsbaum.md)
- User Stories: [US-046](../user-stories/US-046-katalog-anlegen.md), [US-047](../user-stories/US-047-spalten-konfigurieren.md), [US-048](../user-stories/US-048-join-hinzufuegen.md), [US-049](../user-stories/US-049-katalog-daten-anzeigen.md), [US-050](../user-stories/US-050-join-modus-wechseln.md), [US-051](../user-stories/US-051-ad-hoc-filter.md), [US-052](../user-stories/US-052-saved-filter.md), [US-053](../user-stories/US-053-saved-view.md), [US-054](../user-stories/US-054-katalog-navigationsbaum.md)
- ADRs: –
- Test Cases: noch keine
- Implementation: noch keine

## Offene Fragen

- [ ] Können Joins mehrschichtig sein (z.B. ApplicationComponent → DataFlow → Interface → UsedBy → Role)? Multi-Hop-Joins würden den Konfigurator erheblich komplexer machen. V1.0: max. ein Join-Level (nur direkte Verbindungen).
- [ ] Sollen Kataloge eine maximale Ergebnismenge erzwingen (Paginierung Pflicht), oder sind unbegrenzte Exporte erlaubt? Relevant für grosse Repositories.
- [ ] Können SavedViews auch Join-Modus-Overrides für alle Joins gleichzeitig setzen (globaler Override), oder nur pro Join?
- [ ] Braucht der CIO (SH-05) einfache Chart/KPI-Widgets (z.B. „Anzahl Applikationen = 47") zusätzlich zur Tabelle? Das wäre ein separates „Dashboard"-Konzept.

## Notizen

UC-06 unterscheidet bewusst zwischen zwei Nutzungsrollen: dem **Katalog-Manager** (SH-03, SH-07), der Kataloge als wiederverwendbare, geteilte Abfragen für das Team anlegt, und dem **Katalog-Besucher** (alle Akteure), der diese Abfragen konsumiert und zur Laufzeit anpasst. Diese Trennung ermöglicht, dass die Komplexität der Konfiguration vom EA-Experten getragen wird, während alle anderen Stakeholder eine einfache, vorkonfigurierte Sicht erhalten.

Die Join-Logik (rows vs. aggregate) ist das zentrale Differenzierungsmerkmal gegenüber einfachen Listen-Views. Sie erlaubt es, verwandte Entitäten kontextabhängig entweder flach (rows: analytisch, gut für Export und Pivot) oder kompakt (aggregate: gut für Überblick und Dashboards) darzustellen – und das am selben Katalog, ohne neue Konfiguration.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; Hauptablauf Katalog-Manager, A1–A5 Besucher-Flows, alle 7 Stakeholder als Akteure |

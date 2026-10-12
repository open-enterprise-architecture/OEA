---
id: REQ-046
title: Katalog-Abfrage ausführen (Live-Daten)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-046: Katalog-Abfrage ausführen (Live-Daten)

## Aussage

Das System MUSS für einen [Catalog](../../business-objects/catalog.md) auf Abruf eine Live-Abfrage gegen das Architecture-Repository ausführen und die Entitäten des `primaryEntityType` als paginierte Liste zurückliefern. Für jede Entität MÜSSEN die Werte der konfigurierten `columns` (Attribute) sowie die Ergebnisse aller `joinDefinitions` enthalten sein. Join-Ergebnisse MÜSSEN je nach aktivem `joinMode` entweder als Zeilen-Expansion (`rows`) oder als aggregierter Eintrag (`aggregate`) dargestellt werden. Aktive Filter (SavedFilter oder Ad-hoc-Filter) MÜSSEN auf die primäre Entität und auf Join-Ergebnisse anwendbar sein.

## Begründung

Der Catalog ist eine gespeicherte Abfrage-Konfiguration; der eigentliche Mehrwert entsteht erst, wenn das System Live-Daten liefert. Die Ergebnisse müssen immer den aktuellen Zustand des Repositorys widerspiegeln, nicht einen gecachten Snapshot. Die Unterstützung beider Join-Modi (`rows` / `aggregate`) direkt im Abfrage-Endpunkt ist nötig, damit das Frontend ohne eigene Aggregationslogik auskommt.

## Kontext

Die Abfrage-Logik ist ein **Read-Only**-Operation auf dem Entity-Repository. Der Catalog-Endpunkt ist ein Query-Endpunkt, kein Mutations-Endpunkt. Das Repository enthält Entitäten aus dem aggregierten Ist-Stand (alle `implemented`-Solutions im Projekt-Modus) bzw. aus dem aktuellen Plateau (im Plateau-Modus). Join-Traversal nutzt die gespeicherten Connection-Instanzen im Repository.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben (Query-Parameter)**:

| Parameter | Typ | Optional | Beschreibung |
|---|---|---|---|
| page | integer | optional | Seite (0-basiert), Default: 0 |
| pageSize | integer | optional | Einträge pro Seite, Default: 50, Max: 500 |
| sortBy | string | optional | attributeName der primären Entität; Default: `name` asc |
| sortDir | enum | optional | `asc` oder `desc`; Default: `asc` |
| joinMode | object | optional | `{ joinDefinitionId: "rows" | "aggregate", ... }` – überschreibt Catalog-/JoinDefinition-Defaults für diesen Aufruf |
| filter | object | optional | Ad-hoc-FilterExpressions als JSON; ergänzend zu aktiven SavedFilters |
| savedFilterIds | string[] | optional | IDs von SavedFilters, die zusätzlich angewendet werden sollen |

**Verarbeitung**:

1. Alle Entitäten des `primaryEntityType` aus dem Repository laden
2. Aktive Filter (savedFilterIds + ad-hoc `filter`) als AND-Kombination anwenden (zwischen Filter-Sets AND; innerhalb eines SavedFilters laut dessen `logicalOperator`)
3. Für jede gefilterte Entität: konfigurierte `columns`-Attribute auflösen; unbekannte oder fehlende Attributwerte als `null` zurückgeben
4. Für jede JoinDefinition: Connection-Graph traversieren; Ergebnisse nach aktivem joinMode strukturieren:
   - `rows`: jede Kombination (primäre Entität × Join-Ergebnis) wird als eigene Zeile geliefert; primäre Entität mit Attributen wiederholt sich
   - `aggregate`: primäre Entität einmal; Join-Ergebnisse als Array im Feld `joinResults[joinName]`
5. Sortierung auf `primäre Entität.sortBy` anwenden (nach Filterung, vor Paginierung)
6. Paginierung anwenden; Response enthält `totalCount`, `page`, `pageSize`, `data[]`

**Ausgaben**:

```json
{
  "totalCount": 42,
  "page": 0,
  "pageSize": 50,
  "data": [
    {
      "id": "uuid",
      "columns": { "name": "CRM-System", "status": "active" },
      "joinResults": {
        "Schnittstellen": [
          { "name": "REST-API", "protocol": "REST" },
          { "name": "SFTP-Export", "protocol": "SFTP" }
        ]
      }
    }
  ]
}
```

Im `rows`-Modus für denselben Catalog:

```json
{
  "data": [
    { "columns": { "name": "CRM-System", "status": "active" }, "joinResults": { "Schnittstellen": { "name": "REST-API", "protocol": "REST" } } },
    { "columns": { "name": "CRM-System", "status": "active" }, "joinResults": { "Schnittstellen": { "name": "SFTP-Export", "protocol": "SFTP" } } }
  ]
}
```

## Akzeptanzkriterien

**AC1** (Happy Path, aggregate-Modus):
- Gegeben: Catalog „Application Inventory", primaryEntityType=ApplicationComponent, 2 Spalten (name, status), 1 Join (DataFlow→Interface, aggregate); Repository enthält CRM-System (status=active) mit 2 Interfaces
- Wenn: `GET /api/v1/catalogs/{id}/query`
- Dann: Response enthält 1 Zeile für CRM-System; `joinResults.Schnittstellen` ist Array mit 2 Einträgen

**AC2** (rows-Modus):
- Gegeben: wie AC1
- Wenn: `GET /api/v1/catalogs/{id}/query?joinMode[join-id]=rows`
- Dann: Response enthält 2 Zeilen (je Interface eine); `columns.name` = „CRM-System" in beiden Zeilen

**AC3** (Filter):
- Gegeben: Repository enthält 5 ApplicationComponents, davon 2 mit status=active
- Wenn: `GET /api/v1/catalogs/{id}/query?filter[status][eq]=active`
- Dann: `totalCount=2`; nur die 2 aktiven Entitäten im data-Array

**AC4** (Entität ohne Join-Ergebnisse):
- Gegeben: ERP-Core (ApplicationComponent) hat keine DataFlow-Connections
- Wenn: Abfrage im aggregate-Modus
- Dann: ERP-Core erscheint in der Antwort mit `joinResults.Schnittstellen = []`; wird NICHT aus der Ergebnismenge ausgeschlossen (LEFT-JOIN-Semantik)

**AC5** (Sortierung und Paginierung):
- Gegeben: 60 ApplicationComponents
- Wenn: `GET /api/v1/catalogs/{id}/query?page=1&pageSize=20&sortBy=name&sortDir=asc`
- Dann: `totalCount=60`; `data` enthält Einträge 21–40 alphabetisch nach Name

**AC6** (Leeres Repository):
- Gegeben: primaryEntityType hat keine Entitäten im Repository
- Wenn: Abfrage
- Dann: HTTP 200 mit `totalCount=0`, `data=[]`; kein Fehler

## Abhängigkeiten

- Blockiert durch: REQ-043 (Catalog muss existieren), REQ-044 (Spalten konfiguriert), REQ-039 (Repository-Datenbasis im Projekt-Modus)
- Ermöglicht: REQ-049 (Join-Modus-Wechsel nutzt diesen Endpunkt mit joinMode-Override)

## Realisierungs-Hinweise

- Endpoint: `GET /api/v1/catalogs/{id}/query`
- LEFT-JOIN-Semantik: Entitäten ohne Join-Treffer werden trotzdem zurückgeliefert (nicht gefiltert)
- Filterausdruck-Serialisierung als Query-Parameter: `filter[attributeName][operator]=value`; für `in`-Operator als CSV oder Array
- Performance: bei grossen Repositories Indexed-Lookup auf EntityType + FilterAttributes notwendig; NFR noch nicht definiert (folgt)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; rows/aggregate Modi, LEFT-JOIN-Semantik, Paginierung |

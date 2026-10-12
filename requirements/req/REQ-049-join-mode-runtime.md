---
id: REQ-049
title: Join-Modus eines Katalogs zur Laufzeit umschalten
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
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-049: Join-Modus eines Katalogs zur Laufzeit umschalten

## Aussage

Das System MUSS allen Katalog-Besuchern ermöglichen, den Join-Modus (`rows` | `aggregate`) pro JoinDefinition zur Laufzeit zu ändern, ohne die gespeicherte Catalog-Konfiguration zu verändern. Die Tabelle MUSS nach dem Umschalten ohne Seiten-Neuladen aktualisiert werden. Der Laufzeit-Override wird als Parameter an den Abfrage-Endpunkt übergeben (REQ-046 `joinMode`-Parameter). Der Besucher KANN den aktuellen Modus optional als SavedView persistieren (REQ-048).

## Begründung

Der optimale Join-Modus hängt vom Analyse-Kontext ab: Der Enterprise Architekt möchte eine kompakte Übersicht (aggregate), um viele Applikationen auf einen Blick zu sehen; der Data Architekt möchte alle Schnittstellen-Zeilen einzeln (rows), um einen CSV-Export zu erstellen oder die Tabelle nach Interface-Attributen zu sortieren. Beide Bedürfnisse soll derselbe Catalog erfüllen, ohne zwei separate Konfigurationen zu benötigen.

## Kontext

Der Laufzeit-Override betrifft ausschliesslich die aktuelle Abfrage und Session des Besuchers. Andere Besucher, die denselben Catalog gleichzeitig öffnen, sind nicht betroffen. Der Override wird im Frontend-State gehalten (nicht auf dem Server persistiert), bis der Besucher explizit eine SavedView anlegt.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben (Query-Parameter bei REQ-046-Endpunkt)**:

| Parameter | Typ | Beschreibung |
|---|---|---|
| `joinMode[{joinDefinitionId}]` | enum `rows\|aggregate` | Überschreibt den Modus für diese JoinDefinition für diesen Aufruf |

Beispiel: `GET /api/v1/catalogs/{id}/query?joinMode[abc-123]=rows&joinMode[def-456]=aggregate`

**Verarbeitung**:

1. Für jede JoinDefinition im Catalog: falls `joinMode[joinDefinitionId]` im Request-Parameter vorhanden, diesen Modus für die Abfrage verwenden (überschreibt `JoinDefinition.defaultJoinMode` und `Catalog.defaultJoinMode`)
2. Nicht überschriebene JoinDefinitions verwenden ihren konfigurierten Default
3. Catalog-Konfiguration (gespeicherter `defaultJoinMode`) BLEIBT UNVERÄNDERT

**Ausgaben**:

- HTTP 200 OK mit Abfrage-Response gemäss REQ-046; Join-Ergebnisse entsprechend dem gewählten Modus strukturiert
- HTTP 400 Bad Request wenn `joinDefinitionId` unbekannt

## Akzeptanzkriterien

**AC1** (rows-Modus via Parameter):
- Gegeben: Catalog „Application Inventory" mit JoinDefinition id=join-1 (DataFlow→Interface), defaultJoinMode=aggregate; CRM-System hat 2 Interfaces
- Wenn: `GET /api/v1/catalogs/{id}/query?joinMode[join-1]=rows`
- Dann: Response enthält 2 Zeilen für CRM-System (je Interface eine); Catalog.joinDefinitions[0].defaultJoinMode bleibt `aggregate` (unverändert)

**AC2** (aggregate-Modus, gespeicherter Default greift):
- Gegeben: wie AC1
- Wenn: `GET /api/v1/catalogs/{id}/query` (ohne joinMode-Parameter)
- Dann: Response enthält 1 Zeile für CRM-System mit joinResults.Schnittstellen als Array

**AC3** (Keine Mutation am Catalog):
- Nach AC1: `GET /api/v1/catalogs/{id}` (Catalog-Konfiguration abrufen)
- Dann: `joinDefinitions[0].defaultJoinMode = "aggregate"` (unverändert)

**AC4** (Unbekannte joinDefinitionId):
- Wenn: `joinMode[nonexistent-id]=rows`
- Dann: HTTP 400 „JoinDefinition ‹nonexistent-id› nicht gefunden"

**AC5** (Mehrere Joins gleichzeitig überschreiben):
- Gegeben: Catalog hat 2 JoinDefinitions (join-1 defaultMode=aggregate, join-2 defaultMode=aggregate)
- Wenn: `?joinMode[join-1]=rows&joinMode[join-2]=aggregate`
- Dann: join-1 liefert Zeilen-Expansion; join-2 bleibt aggregiert

## Abhängigkeiten

- Blockiert durch: REQ-045 (JoinDefinitions müssen konfiguriert sein), REQ-046 (Abfrage-Endpunkt)
- Zusammenhang: REQ-048 (Laufzeit-Override kann als SavedView gespeichert werden)

## Realisierungs-Hinweise

- Kein eigener Endpunkt nötig; der Parameter wird direkt an REQ-046-Endpunkt übergeben
- Frontend: Toggle-Control pro JoinDefinition (Label: „Zeilen" / „Kompakt" oder „rows" / „aggregate"); ändert den Query-Parameter und löst einen neuen Fetch aus
- Kein Server-Side-Session-State nötig; der Override lebt im Frontend-State

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |

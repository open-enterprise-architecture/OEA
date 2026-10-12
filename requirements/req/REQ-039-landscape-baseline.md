---
id: REQ-039
title: Aktuelle IT-Landschaft als Solution-Ausgangsbasis anzeigen
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - solution
    - architecture
  business_rules: []
  stakeholders:
    - SH-04
  concept:
    - concept/20-entities/11-temporales-modell.md
    - concept/20-entities/12-domain-sichten.md
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-039: Aktuelle IT-Landschaft als Solution-Ausgangsbasis anzeigen

## Aussage

Das System MUSS innerhalb einer Solution den aktuellen Stand der IT-Landschaft als durchsuchbare und filterbare Ausgangsbasis anzeigen; im **Projekt-Modus** MUSS dieser Stand aus der Aggregation aller Entitäten von Solutions mit status=`implemented` berechnet werden, im **Plateau-Modus** aus den Entitäten des `fromPlateau` der Solution.

## Begründung

Der Solution Architekt kann nur dann sinnvoll Änderungs-Deltas erfassen, wenn er sieht, was bereits existiert. Ohne diese Ausgangsbasis müsste er alle Entitäten aus dem Gedächtnis oder externen Dokumenten kennen – ein zuverlässiges Scope-Management ist dann nicht möglich. Die Ausgangsbasis ist gleichzeitig die Grundlage der Diff-Ansicht (REQ-041) und der Konflikt-Warnung (REQ-042).

## Kontext

Die Ausgangsbasis ist kontextgebunden: sie erscheint nur im Rahmen einer konkreten Solution und dient ausschliesslich als Referenzpunkt für die Delta-Erfassung. Sie wird nicht separat persistiert, sondern bei Bedarf berechnet.

**Berechnungsregeln (Projekt-Modus)**: Pro `entityId` wird die zuletzt in einer `implemented`-Solution gesetzte State-Information verwendet. Wenn mehrere `implemented`-Solutions dieselbe Entität `modified` haben, gilt die zuletzt implementierte Version (sortiert nach dem Go-Live-Zeitstempel der Solution). `retiring`-Deltas in `implemented`-Solutions entfernen die Entität aus der Ausgangsbasis.

**Berechnungsregeln (Plateau-Modus)**: Entitäten werden direkt aus dem verknüpften `fromPlateau` gelesen (inkl. aller geerbten Entitäten, falls das Plateau selbst von einem vorherigen Plateau erbt).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Solution-ID (Kontext; die Solution, in der der Architekt arbeitet)
- Optional: Suchbegriff (Freitext, Name der Entität)
- Optional: Filter nach EntityType (Dropdown aus verfügbaren Typen)

**Verarbeitung**:
- Modus ermitteln: `solution.fromPlateauId == null` → Projekt-Modus; sonst Plateau-Modus
- Projekt-Modus: Alle EntityDeltas aus Solutions mit status=`implemented` aggregieren; Endergebnis pro entityId bestimmen (latest-wins für `modified`; `retiring` entfernt die Entität)
- Plateau-Modus: Entitäten des `fromPlateau` laden (inkl. Plateau-Erbschaft)
- Ergebnis nach Suchbegriff und EntityType-Filter einschränken (client- oder serverseitig)
- Sonderfall leere Ausgangsbasis: Hinweismeldung ausgeben; `modified`- und `retiring`-Optionen in der Delta-Erfassung deaktivieren

**Ausgaben**:
- Geordnete, paginierte Entitätsliste: EntityType, Name, relevante Properties (Kurzansicht)
- Ggf. Hinweismeldung „Keine realisierten Entitäten vorhanden – du kannst ausschliesslich neue Entitäten anlegen"

**Fehlerfälle**:
- Solution-ID unbekannt oder keine Berechtigung → 403/404

## Akzeptanzkriterien

**AC1**:
- Gegeben: Zwei implemented-Solutions existieren; Solution A hat Entity `CRM-Legacy` (new), Solution B hat Entity `ERP-Core` (new)
- Wenn: Michael eine neue Solution öffnet (Projekt-Modus) und die Ausgangsbasis lädt
- Dann: werden `CRM-Legacy` und `ERP-Core` in der Liste angezeigt; keine anderen Entitäten

**AC2**:
- Gegeben: Solution A (implemented) hat Entity `CRM-Legacy` (modified: name geändert auf „CRM-Legacy-v2"); Solution B (implemented, neueren Datums) hat dieselbe Entity erneut modified
- Wenn: die Ausgangsbasis berechnet wird
- Dann: zeigt sie den Stand aus Solution B (neuere Implementation gewinnt)

**AC3**:
- Gegeben: Solution A (implemented) hat Entity `CRM-Legacy` als retiring markiert
- Wenn: die Ausgangsbasis berechnet wird
- Dann: ist `CRM-Legacy` **nicht** in der Ausgangsbasis (retiring aus implemented-Solution entfernt die Entität)

**AC4**:
- Gegeben: Plateau P0 (baseline) mit Entitäten `ERP-Core` und `CRM-Legacy`; Solution mit `fromPlateauId=P0` (Plateau-Modus)
- Wenn: Michael die Ausgangsbasis lädt
- Dann: werden genau die Entitäten von P0 angezeigt (inkl. ggf. geerbter Entitäten)

**AC5**:
- Gegeben: Kein implemented-Solutions vorhanden (leere Landschaft, Projekt-Modus)
- Wenn: Michael die Ausgangsbasis lädt
- Dann: erscheint der Hinweis „Keine realisierten Entitäten vorhanden"; `modified`- und `retiring`-Optionen sind deaktiviert

**AC6**:
- Gegeben: Ausgangsbasis mit 50 Entitäten verschiedener Typen
- Wenn: Michael nach `ApplicationComponent` filtert
- Dann: werden nur Entitäten vom Typ `ApplicationComponent` angezeigt

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Aggregation aus mehreren implemented-Solutions (inkl. Überschneidungen, retiring); Plateau-Modus-Variante; leere Landschaft; Filter
- [x] Mess-Werkzeug: Backend-Test-Suite (Aggregationslogik als Unit-Test); E2E-Test für UI-Anzeige
- [x] Bestanden-Kriterium: AC1–AC6 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-038 (Solution muss existieren); Solutions mit status=`implemented` (Projekt-Modus) oder Plateau (Plateau-Modus) als Datenquelle
- **Folgewirkungen**: REQ-040 (EntityDeltas erfassen) nutzt die Ausgangsbasis als Referenzpunkt; REQ-041 (Diff-Ansicht) baut auf ihr auf; REQ-042 (Konflikt-Warnung) prüft ebenfalls gegen diese Datenbasis

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Ausgangsbasis muss Michael den aktuellen Stand manuell kennen – Scope-Fehler (vergessene Entitäten, falsch angenommene Zustände) sind wahrscheinlich, schwerwiegend
- Risiko 2: Falsche Aggregationslogik (z.B. retirete Entitäten bleiben sichtbar) erzeugt eine inkorrekte Basis für Deltas und Diff-Ansicht

## Trade-offs

- Aggregation on-the-fly vs. materialized view: On-the-fly ist korrekt aber ggf. langsam bei vielen Solutions. Für MVP akzeptabel; bei Performance-Problemen → Materialized View oder Cache (dann REQ-Ergänzung nötig)

## Realisierungs-Hinweise

- `GET /api/v1/solutions/{id}/landscape` (berechnet Ausgangsbasis on-the-fly)
- Pagination empfohlen: Cursor-based, da Entitätslisten potentiell gross werden können
- Aggregations-Query: `JOIN entity_deltas ON solution.status='implemented' GROUP BY entity_id ORDER BY solution.implemented_at DESC` – `retiring`-Deltas als Tombstone behandeln

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft aus UC-05 |

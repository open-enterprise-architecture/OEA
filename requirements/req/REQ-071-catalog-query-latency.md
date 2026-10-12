---
id: REQ-071
title: Katalog-Abfrage-Latenz
type: non-functional
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
    - entity
  business_rules: []
  stakeholders:
    - SH-03
    - SH-01
    - SH-07
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-071: Katalog-Abfrage-Latenz

## Aussage

Das System MUSS Katalog-Live-Abfragen (REQ-046) innerhalb eines definierten Zeitbudgets abschliessen — auch bei mehreren konfigurierten Joins und aktivierten Filtern.

## Begründung

Der Katalog ist die primäre Arbeitsoberfläche für alle EA-Nutzer (SH-03, SH-01, SH-07). Jede Interaktion — Filter setzen, Join-Modus wechseln, Seite blättern — löst eine Live-Abfrage aus. Überschreitet die Antwortzeit 1 Sekunde, bricht die wahrgenommene Direktheit der Abfrage weg; Nutzer verlieren den Arbeitsfluss. Dies ist der frequenteste und damit latenzkritischste Lesepfad im System nach dem Login.

## Kontext

Gilt für den serverseitigen Anteil der Katalog-Abfrage (`GET /api/v1/catalogs/{id}/data`): Entity-Lade-, Join-Traversal-, Filter- und Aggregations-Zeit. Netzwerkzeit Client↔Server ist nicht Teil dieses Requirements.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: performance

**Messbare Zielwerte**:

| Metrik | Zielwert | Schwellwert (warning) | Schwellwert (critical) | Scope |
|---|---|---|---|---|
| p95 Katalog-Abfrage ohne Join | < 200ms | 400ms | 800ms | 10.000 primäre Entitäten, keine Filter |
| p95 Katalog-Abfrage mit 3 Joins | < 500ms | 800ms | 1.500ms | 10.000 primäre Entitäten, je Join max. 5 Ergebnisse pro Zeile |
| p95 Katalog-Abfrage mit Filter + 3 Joins | < 600ms | 1.000ms | 2.000ms | 10.000 primäre Entitäten, 1 SavedFilter mit 2 Bedingungen |
| p95 Paginierter Aufruf (Seite 2+) | < 200ms | 400ms | 800ms | gleicher Scope; Cursor-basierte Paginierung vorausgesetzt |

## Akzeptanzkriterien

**AC1** (Ohne Join):
- Gegeben: Repository mit 10.000 ApplicationComponent-Entitäten; Katalog ohne Joins; kein Filter
- Wenn: 95% der Abfragen gemessen
- Dann: Serververarbeitungszeit < 200ms

**AC2** (3 Joins, aggregate-Modus):
- Gegeben: Repository mit 10.000 ApplicationComponent-Entitäten; Katalog mit 3 JoinDefinitions; je Entität im Schnitt 5 Join-Ergebnisse
- Wenn: 95% der Abfragen gemessen
- Dann: Serververarbeitungszeit < 500ms

**AC3** (Filter + 3 Joins):
- Gegeben: wie AC2 + 1 SavedFilter mit 2 AND-verknüpften Bedingungen
- Wenn: 95% der Abfragen gemessen
- Dann: Serververarbeitungszeit < 600ms

## Verifikationsmethode

- [x] Methode: test (automatisiert, Lasttest)
- [x] Test-Setup: synthetisches Repository mit 10.000 Entitäten + 3 JoinDefinitions + SavedFilter
- [x] Mess-Werkzeug: Last-/Performance-Test-Tool (Wahl: Solution-Design)
- [x] Bestanden-Kriterium: p95-Werte gemäss Tabelle
- [ ] In CI integriert: ja, als Performance-Gate nach Walking-Skeleton

## Abhängigkeiten

- **Voraussetzungen**: REQ-043 (Catalog), REQ-046 (Katalog-Abfrage)
- **Konflikte**: REQ-065 (n-Connection-Traversal darf Latenz nicht übermässig erhöhen; Join auf Connection-Primaries muss gleiche SLA einhalten)

## Risiken bei Nichterfüllung

- Nutzer empfinden Katalog als träge; Akzeptanz sinkt; Rückkehr zu Excel-Inventaren

## Trade-offs

- Index-Strategie auf `entityTypeId`, `sourceEntityId`, `targetEntityId` zwingend; Query-Plan muss sorgfältig geprüft werden
- Caching von Join-Ergebnissen kontraindiziert (Live-Daten-Versprechen per REQ-046); stattdessen: DB-Query-Optimierung und Cursor-Paginierung

## Realisierungs-Hinweise

- Datenbankindizes: `(entityTypeId)`, `(sourceEntityId)`, `(targetEntityId)`, `(entityTypeId, name)` zwingend
- Cursor-Paginierung (keyset) statt OFFSET für stabile Performance auf Seite N

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

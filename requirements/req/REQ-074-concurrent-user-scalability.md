---
id: REQ-074
title: Gleichzeitige Nutzer und Skalierbarkeit
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
    - UC-06
  business_objects:
    - entity
    - catalog
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-074: Gleichzeitige Nutzer und Skalierbarkeit

## Aussage

Das System MUSS unter Last von **50 gleichzeitigen aktiven Nutzern** die in REQ-008, REQ-071 und REQ-072 definierten Latenzziele einhalten, ohne Degradierung der p95-Werte um mehr als den Faktor 2. Das System SOLL ein Architecture-Repository mit bis zu **500.000 ArchitectureEntities** (Elemente und Connections kombiniert) ohne funktionale Einschränkungen unterstützen.

## Begründung

OEA wird in Konzernen mit grossen EA-Teams eingesetzt (SH-05: Konzern mit gemischtem OEA-Stack) und muss bei gleichzeitiger Nutzung durch mehrere Domänen-Architekten stabil bleiben. Gleichzeitig muss die Datenmenge skalieren: ein vollständig modellierter Konzern-IT-Bebauungsplan (alle Applikationen, Schnittstellen, Technologien, Personas, Capabilities) erreicht leicht 50.000–200.000 Entitäten.

## Kontext

„Gleichzeitige aktive Nutzer" bedeutet: Nutzer, die innerhalb desselben 60-Sekunden-Fensters mindestens eine API-Anfrage absetzen. Lesende Zugriffe (Katalog, Diagramme) dominieren; schreibende Zugriffe (EntityDeltas, Metamodell-Änderungen) sind seltener. Der Zielwert von 50 Nutzern deckt KMU bis mittlere Konzerne ab; für grosse Konzerne (> 200 Nutzer) ist horizontale Skalierung (mehrere API-Instanzen, Load-Balancer) Deployment-Verantwortung des Betreibers.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: scalability

**Messbare Zielwerte**:

| Metrik | Zielwert | Schwellwert (warning) | Scope |
|---|---|---|---|
| Max. gleichzeitige aktive Nutzer ohne Latenz-Degradierung > 2× | 50 Nutzer | 30 Nutzer | Standard-Deployment (1 API-Instanz, 1 DB-Instanz) |
| Max. ArchitectureEntities im Repository | 500.000 | | alle Typen kombiniert (Elemente + Connections) |
| Katalog-Abfrage-p95 bei 50 Nutzern | ≤ 2× REQ-071-Zielwert | | 50 parallele Requests |
| Login-p95 bei 50 Nutzern | ≤ 2× REQ-008-Zielwert | | 50 parallele Login-Vorgänge |
| Write-Throughput (EntityDelta speichern) | ≥ 20 Schreibvorgänge/s | | bei 50 aktiven Nutzern |

## Akzeptanzkriterien

**AC1** (50 gleichzeitige Nutzer — Katalog):
- Gegeben: 50 virtuelle Nutzer senden gleichzeitig `GET /api/v1/catalogs/{id}/data`; Repository mit 10.000 Entitäten
- Wenn: Lasttest läuft 5 Minuten
- Dann: p95-Latenz ≤ 2 × 500ms = 1.000ms (REQ-071-Zielwert × 2); kein HTTP 5xx; kein Timeout

**AC2** (50 gleichzeitige Nutzer — Login):
- Gegeben: 50 virtuelle Nutzer senden gleichzeitig OIDC-Token-Einlösung
- Wenn: Lasttest 1 Minute
- Dann: p95-Login-Latenz ≤ 2 × 300ms = 600ms (REQ-008-Zielwert × 2)

**AC3** (500.000 Entitäten — Funktionalität):
- Gegeben: Repository mit 500.000 ArchitectureEntities (verschiedene EntityTypes)
- Wenn: Standard-Katalog-Abfrage, Diagramm-Öffnen, Lineage-Query
- Dann: Keine Timeouts; keine OOM-Fehler; korrekte Ergebnisse

**AC4** (500.000 Entitäten — Katalog-Latenz):
- Gegeben: 500.000 Entitäten; Katalog für primaryEntityType mit 50.000 Instanzen
- Wenn: Katalog-Abfrage, erste Seite (50 Einträge), kein Join
- Dann: p95 < 500ms (durch Cursor-Paginierung ist das unabhängig von Gesamtgrösse)

## Verifikationsmethode

- [x] Methode: test (automatisiert, Lasttest)
- [x] Test-Setup: k6 / Locust Lasttest; synthetisches Repository mit 10.000–500.000 Entitäten; 50 virtuelle Nutzer
- [x] Mess-Werkzeug: k6 oder Locust (HTTP-Lasttest); DB-Monitoring (Query-Plan, Lock-Contention)
- [x] Bestanden-Kriterium: p95-Werte gemäss Tabelle; 0 HTTP 5xx; kein OOM
- [ ] In CI integriert: nein (zu ressourcenintensiv für Standard-CI); als Release-Gate vor Major-Version

## Abhängigkeiten

- **Voraussetzungen**: REQ-008 (Login-Latenz), REQ-071 (Katalog-Latenz), REQ-072 (Canvas-Latenz) — diese NFRs definieren den Einzel-Nutzer-Baseline
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Konzern-Rollout scheitert: parallele Nutzung durch mehrere EA-Teams führt zu inakzeptablen Antwortzeiten
- Grosse EA-Modelle (Konzern mit tausenden Applikationen) können nicht vollständig erfasst werden

## Trade-offs

- 500.000 Entitäten sind mit relationale DB (Postgres) + korrekten Indizes erreichbar; kein NoSQL nötig
- Horizontale Skalierung (mehrere API-Instanzen) für > 50 Nutzer: Session-Management muss zustandslos sein (JWT, kein serverseitiger Session-State) — dies ist Designentscheidung für Walking-Skeleton
- Connection-Pool-Konfiguration (max. DB-Connections) muss auf 50 Nutzer ausgelegt sein: ~10–15 Connections/Instanz

## Realisierungs-Hinweise

- API-Server muss zustandslos sein (kein In-Memory-Session-State) → horizontale Skalierung ohne Sticky Sessions
- DB-Connection-Pool: HikariCP oder pgBouncer empfohlen
- 500.000 Entitäten: Tabellen-Partitionierung nach `entityTypeId` prüfen ab 1M Einträgen

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

---
id: REQ-131
title: Graph-Traversal-Latenz (ABAC connection-scoped)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - entity
    - metamodel-configuration
    - role
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
  concept:
    - concept/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-131: Graph-Traversal-Latenz (ABAC connection-scoped)

## Aussage

Der Graph-Traversal für die `connection-scoped`-Sichtbarkeitsprüfung (REQ-127) DARF die Antwortzeit einer Entity-Abfrage um maximal **200 ms** (p95) erhöhen, gemessen als zusätzliche Latenz gegenüber einer identischen Abfrage ohne `connection-scoped`-Properties. Dies gilt für Entitäten mit bis zu **500 direkt verbundenen Scope-Entitäten** pro Nutzer über den konfigurierten `scopingConnectionType`.

Das Traversal-Ergebnis MUSS serverseitig gecacht werden (TTL ≤ 60 Sekunden pro Nutzer und ConnectionType). Der Cache MUSS bei Änderungen an relevanten Connections (Anlage, Löschung) für den betroffenen Nutzer invalidiert werden.

## Begründung

Der `connection-scoped`-Modus erfordert zur Laufzeit einen Graph-Traversal, der ohne Caching bei grossen Graphen die Gesamtlatenz der Entity-API signifikant erhöhen kann. Das Ziel von 200 ms zusätzliche Latenz ist ein konservativer Wert, der bei typischer EA-Repository-Grösse (≤ 500k Entitäten, ≤ 500 Scope-Verbindungen pro Nutzer) mit einer Tiefe-1-Traversal zuverlässig erreichbar ist. REQ-127 definiert die funktionale Anforderung; dieses REQ setzt den verbindlichen Performance-Rahmen.

## Kontext

**Scope**: Tiefe-1-Traversal (Nutzer → direkt verbundene Entitäten via `scopingConnectionType`); Tiefe-2+-Traversal ist als zukünftige Erweiterung vorgesehen und hat eigene NFR-Definition

**Datenmenge**: bis zu 500k Gesamtentitäten; bis zu 500 direkt verbundene Scope-Entitäten pro Nutzer

**Cache**: serverseitig, per Nutzer-ID + ConnectionType-ID; TTL 60 Sekunden; Invalidierung bei Connection-Änderung

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: performance

**Messbare Zielwerte**:

| Metrik | Zielwert | Scope |
|---|---|---|
| Zusätzliche Latenz durch Traversal (p95) | ≤ 200 ms | Entity-Abfrage mit connection-scoped Properties, Cache kalt |
| Zusätzliche Latenz durch Traversal (p95) | ≤ 10 ms | Entity-Abfrage mit connection-scoped Properties, Cache warm |
| Cache-Hit-Rate nach Warm-up | ≥ 90 % | Normalnutzung (Nutzer liest mehrere Entities hintereinander) |
| Cache-Invalidierung nach Connection-Änderung | ≤ 1 Sekunde | Konsistenz nach Write |
| Scope: Anzahl direkt verbundener Entitäten | ≤ 500 | Gültigkeitsbereich des Zielwerts |

## Akzeptanzkriterien

**AC1** (Kalt-Traversal ≤ 200 ms):
- Gegeben: Nutzer hat 500 direkt verbundene Scope-Entitäten; Cache leer
- Wenn: Entity-Abfrage mit einem `connection-scoped` Property ausgeführt
- Dann: p95-Latenz des Traversal-Anteils ≤ 200 ms (gemessen via APM-Trace)

**AC2** (Warm-Cache ≤ 10 ms):
- Gegeben: Traversal-Ergebnis ist im Cache (TTL noch nicht abgelaufen)
- Wenn: Weitere Entity-Abfrage desselben Nutzers ausgeführt
- Dann: Traversal-Anteil ≤ 10 ms (Cache-Lookup)

**AC3** (Cache-Invalidierung):
- Gegeben: Nutzer hat gecachtes Traversal-Ergebnis
- Wenn: Neue `DomainAssignment`-Connection für diesen Nutzer angelegt wird
- Dann: Spätestens nach 1 Sekunde liefert die nächste Entity-Abfrage das aktualisierte Traversal-Ergebnis

**AC4** (Lasttest):
- Gegeben: 50 gleichzeitige Nutzer (REQ-074) mit je 500 Scope-Verbindungen
- Wenn: Alle Nutzer gleichzeitig Entity-Abfragen mit connection-scoped Properties senden
- Dann: p95-Latenz (gesamt, inkl. Traversal) ≤ REQ-071-Zielwert + 200 ms

## Verifikationsmethode

- [x] Methode: load-test (k6 oder Gatling; synthetischer Graph mit 500k Entities, 500 Scope-Connections pro Nutzer)
- [x] Methode: apm-trace (OpenTelemetry-Span für Traversal-Anteil isolieren)
- [ ] In CI integriert: nein (Lasttest in dedizierten Performance-Gates)

## Abhängigkeiten

- **Voraussetzungen**: REQ-127 (connection-scoped Enforcement implementiert), REQ-074 (Skalierbarkeits-NFR)
- **Folgewirkungen**: Cache-Invalidierungs-Mechanismus muss bei Connection-Write-Operationen (UC-04, UC-17) mitgedacht werden

## Risiken bei Nichterfüllung

- Spürbare Latenz-Degradierung bei Entitäts-Detailseiten im Katalog (UC-06) wenn viele `connection-scoped` Properties konfiguriert sind
- Domänen-Architekten (SH-04) erleben träge Oberfläche in ihrer Hauptarbeit

## Trade-offs

- TTL 60 Sekunden: kurz genug für Konsistenz, lang genug für effektives Caching; bei höheren Konsistenz-Anforderungen auf 10–30 Sekunden senkbar (Performance-Kosten steigen)
- Tiefe-1-Traversal ist für v1.0 ausreichend; tiefere Hierarchien (Domäne → Sub-Domäne → Entität) erfordern Graph-DB oder materialisierten Pfadindex

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

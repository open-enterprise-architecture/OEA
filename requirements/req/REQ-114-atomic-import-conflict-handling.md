---
id: REQ-114
title: Atomarer Paket-Import mit Konflikt-Handling
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-18
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
    - trm-category
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-114: Atomarer Paket-Import mit Konflikt-Handling

## Aussage

Der Paket-Import MUSS als atomare Transaktion ausgeführt werden: entweder werden alle validen Bausteine importiert oder keiner (Rollback bei Fehler). Wenn das Paket Bausteine mit einer ID enthält, die bereits im System existiert, MUSS das System diese Konflikte als Liste anzeigen und zwischen „Überspringen" (default) und „Aktualisieren" wählen lassen. „Aktualisieren" darf nur für Bausteine mit `scope=imported` vom selben `sourcePackage` zulässig sein — nicht für `scope=organization`-Bausteine. Ein Zyklus im ABB-Verfeinerungs-Graphen innerhalb des Pakets MUSS den Import vollständig abbrechen.

## Begründung

Partieller Import erzeugt inkonsistente Referenzen zwischen Bausteinen. Konflikt-Handling schützt organisationsspezifische Anpassungen vor unbeabsichtigter Überschreibung durch Paket-Updates.

## Akzeptanzkriterien

**AC1** (Zyklus-Abbruch):
- Wenn: das importierte Paket einen Zyklus im ABB-Verfeinerungs-Graphen enthält
- Dann: bricht der Import ab; kein Baustein wird importiert; die Fehlermeldung beschreibt den Zyklus

**AC2** (Konflikt-Liste):
- Wenn: das Paket 3 Bausteine mit bereits vorhandenen IDs enthält
- Dann: zeigt das System die Konflikt-Liste; der Default ist „Überspringen" für alle Konflikte

**AC3** (Rollback bei DB-Fehler):
- Wenn: nach dem Import von 20 von 50 Bausteinen ein Datenbankfehler auftritt
- Dann: wird ein vollständiger Rollback ausgeführt; 0 Bausteine sind importiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-112 (built-in Pakete), REQ-113 (Datei-Upload), REQ-111 (Scope-Schutz)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- Transaktion: Datenbankebene (BEGIN/COMMIT/ROLLBACK); kein zweiphasiges Commit notwendig bei Single-DB
- Zyklus-Erkennung: topologische Sortierung des ABB-Graphen im Paket vor Transaktion-Beginn
- „Aktualisieren" für fremde `scope=organization`-Bausteine: im Backend per Guard blockiert, nicht nur UI

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

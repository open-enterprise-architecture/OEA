---
id: REQ-112
title: Eingebettete Continuum-Pakete aktivieren
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

# REQ-112: Eingebettete Continuum-Pakete aktivieren

## Aussage

Das System MUSS eine Liste von eingebetteten Continuum-Paketen anbieten (mindestens: „TOGAF 10 – Technical Reference Model"). Jedes Paket MUSS vor dem Import eine Vorschau anzeigen: Anzahl ABBs, SBBs, Patterns, Reference Architectures und TRM-Kategorien sowie die wichtigsten Kategorie-Namen. Die Aktivierung MUSS per Klick auf „Importieren" ausführbar sein — ohne Datei-Upload.

## Begründung

Manuelle Nachbildung des TOGAF TRM wäre für Betreiber prohibitiv aufwendig. Built-in-Pakete senken die Einstiegshürde auf einen Klick und ermöglichen sofortigen produktiven Betrieb mit einem branchenweit anerkannten Standard-Framework.

## Akzeptanzkriterien

**AC1** (Paket-Vorschau):
- Wenn: die Paketliste geöffnet wird
- Dann: ist „TOGAF 10 TRM" mit Vorschau (47 ABBs, 38 TRM-Kategorien, 12 Patterns) sichtbar

**AC2** (Import per Klick):
- Wenn: auf „Importieren" geklickt wird
- Dann: wird der Import atomar ausgeführt; ein Import-Protokoll zeigt das Ergebnis

**AC3** (Bereits importiertes Paket):
- Wenn: ein Paket bereits importiert wurde
- Dann: zeigt die UI ein Badge „Bereits importiert" und einen Hinweis auf Konflikt-Handling

## Abhängigkeiten

- **Voraussetzungen**: REQ-111 (Scope-Schutz; importierte Bausteine erhalten `scope=built-in`)
- **Folgewirkungen**: REQ-114 (atomare Import-Transaktion gilt auch für built-in Pakete)

## Realisierungs-Hinweise

- Built-in-Pakete als JSON-Dateien im Servercode eingebettet (kein externer Download)
- Import-Endpunkt: `POST /api/continuum/packages/{packageId}/activate`
- Import-Protokoll: Anzahl importierter Bausteine je Typ + etwaige übersprungene Konflikte

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

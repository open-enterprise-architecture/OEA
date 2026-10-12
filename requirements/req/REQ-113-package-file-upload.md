---
id: REQ-113
title: Continuum-Paket per Datei-Upload importieren
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

# REQ-113: Continuum-Paket per Datei-Upload importieren

## Aussage

Das System MUSS den Upload von Continuum-Paketen als `.json`- oder `.yaml`-Datei ermöglichen. Nach dem Upload MUSS das System das Paket sofort gegen das OEA-Continuum-Paket-Schema validieren und bei Schema-Verletzungen HTTP 422 mit einer Fehlerliste (Zeile, Feld, Fehlerbeschreibung) zurückgeben — ohne einen einzigen Baustein zu importieren. Bei erfolgreicher Validierung MUSS eine Vorschau angezeigt werden, bevor der Import bestätigt wird.

## Begründung

Schema-Fehler müssen vor dem Import sichtbar sein, damit der Betreiber die Datei korrigieren kann. Eine Vorschau vor dem Commit verhindert versehentliche Imports und gibt Transparenz über den Umfang des Imports.

## Akzeptanzkriterien

**AC1** (Valide Datei – Vorschau):
- Wenn: eine valide Paket-Datei hochgeladen wird
- Dann: zeigt die UI eine Vorschau mit Anzahl und Art der enthaltenen Bausteine

**AC2** (Schema-Verletzung):
- Wenn: eine Datei mit fehlendem Pflichtfeld hochgeladen wird
- Dann: antwortet das System mit HTTP 422 und einer Fehlerliste (Zeile, Feld, Fehlerbeschreibung); kein Baustein wird importiert

**AC3** (Import nach Bestätigung):
- Wenn: die Vorschau bestätigt wird
- Dann: wird der Import ausgeführt; alle importierten Bausteine haben `scope=imported`

## Abhängigkeiten

- **Voraussetzungen**: REQ-111 (Scope-Schutz), REQ-114 (atomare Import-Transaktion)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- Schema-Validierung: JSON Schema oder Zod auf dem Server; YAML per Parser zu JSON konvertieren vor Validierung
- Fehlerliste: Array aus `{line, field, message}` im 422-Response-Body
- Datei-Upload: Multipart-Form-Upload; max. Dateigröße konfigurierbar (default: 10 MB)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

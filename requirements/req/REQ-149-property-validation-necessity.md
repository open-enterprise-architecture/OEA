---
id: REQ-149
title: Eigenschafts-Validierung nach Notwendigkeit
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-06
  adrs:
    - ADR-022
supersedes: []
superseded_by: []
---

# REQ-149: Eigenschafts-Validierung nach Notwendigkeit

## Aussage

Das System **MUSS** beim Speichern einer Entität die Property-Werte gegen die aktiven Metatyp-Property-Mappings validieren und bei `necessity=mandatory` einen Fehler, bei `necessity=warning` eine Warnung zurückgeben; `necessity=optional` erzeugt keinen Hinweis.

## Begründung

Ohne Validierung ist die Notwendigkeitsangabe im Metamodell wirkungslos. Architekten sehen nicht, welche Properties fehlen. Das Metamodell verliert seine steuernde Wirkung.

## Kontext

Die aktive Notwendigkeit wird per "neuestes valid_from ≤ heute"-Query ermittelt (ADR-022). Validierung greift beim PUT/PATCH/POST von Entitäten und beim Batch-Write.

## Typ-spezifische Felder

**Eingaben**:
- Entity-Write (POST/PUT/PATCH/Batch)
- Aktive Mappings für den MetaTyp der Entität

**Verarbeitung**:
- Für jede `mandatory`-Property: fehlt der Wert → Fehler (422)
- Für jede `warning`-Property: fehlt der Wert → Warnung in Response-Body
- `optional`-Properties: kein Feedback bei Fehlen

**Ausgaben**:
- 422 Unprocessable Entity bei mandatory-Verletzung; Body mit `errors[]` (ValidationError-Schema)
- 200/201 mit `warnings[]` bei warning-Verletzung

**Fehlerfälle**:
- MetaTyp ohne aktives Mapping für eine Property → property wird ignoriert

## Akzeptanzkriterien

**AC1**:
- Gegeben: MetaTyp M hat Property `version` mit necessity=mandatory
- Wenn: POST /entities mit MetaTyp M ohne Property `version`
- Dann: 422; errors[0].field = "properties", errors[0].code = "MANDATORY_MISSING"

**AC2**:
- Gegeben: MetaTyp M hat Property `owner` mit necessity=warning
- Wenn: POST /entities mit MetaTyp M ohne Property `owner`
- Dann: 201 Created; Response enthält warnings[0].field = "properties[owner]"

**AC3**:
- Gegeben: POST /entities/batch mit dryRun=true
- Wenn: mehrere Entitäten, einige mit mandatory-Verletzung
- Dann: ValidationResult.valid=false; alle Fehler pro Item aufgelistet; keine Persistierung

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Integration-Test; Metamodell mit mandatory/warning-Properties
- [ ] Bestanden-Kriterium: Fehler-Codes korrekt; Warnings im Response-Body vorhanden
- [ ] In CI integriert: ja

## Abhängigkeiten

- **Voraussetzungen**: REQ-148 (Stichtag-Mechanismus)
- **Folgewirkungen**: US-138

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-022 |

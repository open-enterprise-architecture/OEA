---
id: REQ-148
title: Property-Mapping Stichtag änderbar
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-04
  adrs:
    - ADR-022
supersedes: []
superseded_by: []
---

# REQ-148: Property-Mapping Stichtag änderbar

## Aussage

Das System **MUSS** es ermöglichen, die Notwendigkeit (mandatory/warning/optional) einer Property-Zuordnung zu einem MetaTyp per Stichtag zu ändern, ohne bestehende Werte zu löschen oder historische Zuordnungen zu überschreiben.

## Begründung

Metamodelle entwickeln sich. Eine Property, die heute optional ist, kann ab einem bestimmten Datum mandatory werden. Ohne Stichtag-Mechanismus müssten entweder alle historischen Zuordnungen geändert werden (Verlust der Nachvollziehbarkeit) oder es entstehen Ad-hoc-Lösungen.

## Kontext

`metatype_property_mappings.valid_from` ist der Stichtag. Das System ermittelt die aktive Zuordnung nach "neuester valid_from, der ≤ heute ist". Ältere Einträge bleiben erhalten (History). (ADR-022)

## Typ-spezifische Felder

**Eingaben**:
- Bestehendes Mapping (metatype_id, property_def_id)
- Neues `valid_from`, neue `necessity`

**Verarbeitung**:
- Neuer Datensatz in `metatype_property_mappings` mit neuem valid_from anlegen
- Bestehende Einträge bleiben unverändert
- UNIQUE(metatype_id, property_def_id, valid_from) verhindert Duplikate

**Ausgaben**:
- 201 Created; neuer Mapping-Eintrag

**Fehlerfälle**:
- valid_from bereits vorhanden für dieses Paar → 409 Conflict

## Akzeptanzkriterien

**AC1**:
- Gegeben: Property P ist für MetaTyp M seit 2026-01-01 als `optional` gemappt
- Wenn: Admin legt neues Mapping P→M mit valid_from=2026-07-01, necessity=mandatory an
- Dann: Ab 2026-07-01 gilt mandatory; Entitäten die P fehlt, erhalten Warning/Error bei Validierung

**AC2**:
- Gegeben: Ein Mapping-Eintrag für valid_from=2026-01-01 existiert
- Wenn: Admin versucht, denselben valid_from nochmals einzutragen
- Dann: 409 Conflict

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Integration-Test gegen H2-DB
- [ ] Bestanden-Kriterium: POST /metamodel/metatypes/{id}/property-mappings erzeugt zweiten Eintrag; GET liefert neueste Zuordnung
- [ ] In CI integriert: ja

## Abhängigkeiten

- **Voraussetzungen**: REQ-149 (Validierung nach necessity)
- **Folgewirkungen**: US-137

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-022 |

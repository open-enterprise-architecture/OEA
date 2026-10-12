# US-137: Property-Mapping Stichtag ändern

**ID**: US-137
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Metamodell-Administrator möchte ich die Notwendigkeit einer Property-Zuordnung zu einem MetaTyp per Stichtag ändern können, damit Metamodell-Entwicklungen nachvollziehbar und rückwirkend korrekt bleiben.

## Bezug

**Use Case**: [UC-04: Metamodell konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-03: Kurt – Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-enterprise-architect.md)
**Requirements**: REQ-148

## Akzeptanzkriterien

**AC1**:
- Gegeben: Property `version` ist für MetaTyp `ApplicationComponent` seit 2026-01-01 als `optional` gemappt
- Wenn: Admin legt neues Mapping mit `valid_from=2026-07-01`, `necessity=mandatory` an via `POST /metamodel/metatypes/{id}/property-mappings`
- Dann: Beide Einträge in DB vorhanden; GET liefert für heute (≥ 2026-07-01) `mandatory`

**AC2**:
- Gegeben: Ein Stichtag-Eintrag für (metatype, property, valid_from) existiert
- Wenn: Admin versucht denselben valid_from erneut anzulegen
- Dann: 409 Conflict; kein Duplikat

## Technische Hinweise

- Betroffene Tabelle: `metatype_property_mappings`
- UNIQUE(metatype_id, property_def_id, valid_from)
- API: `POST /api/v1/metamodel/metatypes/{id}/property-mappings`
- Aktive Zuordnung ermitteln: `WHERE valid_from <= CURRENT_DATE ORDER BY valid_from DESC LIMIT 1`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Integration-Test: Stichtag-Wechsel korrekt; Konflikt-Check korrekt
- [ ] Dokumentation: `docs/data-model.puml` bereits aktuell (ADR-022)
- [ ] Linter und Type-Checks grün

## Abhängigkeiten

- Wartet auf: US-033 (Metamodell-Import, Starter-Paket)
- Blockiert: US-138 (Validierung basiert auf aktiver Zuordnung)

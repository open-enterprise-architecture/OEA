# US-107: ABB anlegen und Verfeinerungs-Hierarchie aufbauen

**ID**: US-107
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich `ArchitectureBuildingBlock`-Einträge anlegen, bearbeiten und in einer Verfeinerungs-Hierarchie verknüpfen, damit ich eine organisationsspezifische Continuum-Bibliothek aufbauen und verwalten kann.

## Bezug

**Use Case**: [UC-17](../use-cases/UC-17-continuum-bausteine-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-107](../req/REQ-107-abb-anlegen-verfeinern.md)

## Akzeptanzkriterien

**AC1** (ABB anlegen):
- Wenn: ich einen ABB mit `continuumLevel=organization` und `maturityLevel=established` anlege
- Dann: speichert das System ihn mit HTTP 201; er ist sofort in der Bibliothek sichtbar

**AC2** (Zyklus-Erkennung):
- Wenn: ich ABB „B" auf `refines=A` setze und dann ABB „A" auf `refines=B` setzen will
- Dann: lehnt das System die Speicherung mit HTTP 422 „Zyklus erkannt: A → B → A" ab

**AC3** (Pflichtfeld industry):
- Wenn: ich einen ABB mit `continuumLevel=industry` ohne das Feld `industry` speichere
- Dann: erhalte ich HTTP 422

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

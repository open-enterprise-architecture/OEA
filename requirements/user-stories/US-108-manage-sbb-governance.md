# US-108: SBB mit Governance-Status verwalten

**ID**: US-108
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich `SolutionBuildingBlock`-Einträge mit Governance-Status anlegen und verwalten, damit ich Technology-Standards durchsetzen und verbotene Produkte in der Landschaft automatisch kenntlich machen kann.

## Bezug

**Use Case**: [UC-17](../use-cases/UC-17-continuum-bausteine-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-108](../req/REQ-108-sbb-governance-verwalten.md)

## Akzeptanzkriterien

**AC1** (SBB anlegen):
- Wenn: ich eine SBB mit `governanceStatus=approved` und `implements=[ABB-UUID]` anlege
- Dann: speichert das System sie mit HTTP 201

**AC2** (Prohibited-Warnung):
- Wenn: ich eine SBB auf `governanceStatus=prohibited` setze
- Dann: zeigen alle Entitäten mit `instanceOfSBBId=dieser-SBB` den Hinweis „Verwendetes Produkt verboten – Migration erforderlich"

**AC3** (Warnung ohne ABB-Referenz):
- Wenn: ich eine SBB ohne `implements`-ABBs anlege
- Dann: zeigt das System die Governance-Warnung „SBB ohne ABB-Referenz ist nur Produktkatalog-Eintrag"; die Speicherung wird nicht blockiert

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

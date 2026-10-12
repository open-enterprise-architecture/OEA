# US-095: Navigationsbaumeintrag als Soft-Reference

**ID**: US-095
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich Entitäten, Diagramme und Kataloge als Items in Ordner einhängen und wieder entfernen können, ohne dass das referenzierte Objekt dabei verändert wird, damit der Navigationsbaum eine unabhängige Strukturierungsebene bleibt.

## Bezug

**Use Case**: [UC-13](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-095](../req/REQ-095-navigationsbaum-item-soft-reference.md)

## Akzeptanzkriterien

**AC1** (Item entfernen ohne Datenverlust):
- Wenn: ich ein TreeNodeItem entferne
- Dann: ist das Item gelöscht; die referenzierte Entität existiert weiterhin

**AC2** (Verwaiste Items markieren):
- Wenn: eine Entität gelöscht wird, die als Item in 2 Ordnern referenziert ist
- Dann: zeigen beide Items ein Warnsymbol

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

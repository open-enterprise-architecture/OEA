# US-105: Selektive Feldauswahl im Teilwiederherstellungs-Wizard

**ID**: US-105
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich im Wizard nur Felder auswählen können, die sich tatsächlich unterscheiden, und für jedes Feld den Quellwert als Vorschau sehen, damit ich präzise entscheiden kann, was wiederhergestellt werden soll.

## Bezug

**Use Case**: [UC-16](../use-cases/UC-16-teilwiederherstellung-entitaet.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-105](../req/REQ-105-teilwiederherstellung-feldauswahl.md)

## Akzeptanzkriterien

**AC1** (Identische Felder ausgegraut):
- Wenn: ein Feld in Quelle und aktuellem Stand identisch ist
- Dann: ist die Checkbox ausgegraut und nicht anklickbar

**AC2** (Neue Properties gekennzeichnet):
- Wenn: eine Property in der Quellversion noch nicht vorhanden war
- Dann: zeigt der Wizard "(in Quellversion nicht vorhanden)"; Checkbox nicht anwählbar

**AC3** (Vorschau bei Feldauswahl):
- Wenn: ich die Checkbox eines geänderten Feldes aktiviere
- Dann: wird der Quellwert als Vorschau angezeigt

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

# US-099: Feldweisen Diff zwischen Versionen anzeigen

**ID**: US-099
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich beim Anklicken einer Version einen feldweisen Diff sehen und zwei beliebige Versionen direkt vergleichen können, damit ich Änderungen ohne technische Vorkenntnisse schnell verstehe.

## Bezug

**Use Case**: [UC-14](../use-cases/UC-14-aenderungshistorie-einsehen.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-099](../req/REQ-099-versionshistorie-diff-ansicht.md)

## Akzeptanzkriterien

**AC1** (Nur geänderte Felder):
- Wenn: ich eine Version anklicke
- Dann: sehe ich nur die geänderten Felder; identische Felder sind ausgeblendet

**AC2** (Dot-Notation für Properties):
- Wenn: `properties.owner` geändert wurde
- Dann: erscheint der Eintrag als "properties.owner: 'Kurt' → 'Michael'"

**AC3** (Kumulierter Diff):
- Wenn: ich Version v2 und v7 für den Vergleich auswähle
- Dann: sehe ich den kumulierten Diff über alle Übergänge von v2 bis v7

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

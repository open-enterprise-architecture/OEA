# US-125: Sichtbarkeits-Badge im Metamodell-Editor und konditionelle Felder

**ID**: US-125
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich auf einen Blick sehen, welche Properties eines EntityType eingeschränkt sind, und die Einschränkung direkt im Editor anpassen können, damit ich den Überblick über sensible Felder behalte.

## Bezug

**Use Case**: [UC-21](../use-cases/UC-21-property-sichtbarkeit-konfigurieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-125](../req/REQ-125-property-sichtbarkeit-konfigurationsmaske.md)

## Akzeptanzkriterien

**AC1** (Badge in Property-Liste):
- Wenn: Ein Property hat `visibilityMode ≠ public`
- Dann: Zeigt ein „Eingeschränkt"-Badge (Icon oder Label) in der Property-Listzeile

**AC2** (Konditionelle Felder):
- Wenn: Modus-Selektor geändert
- Dann: Nur relevante Hilfsfelder sichtbar; nicht benötigte ausgeblendet (kein Layout-Shift)

**AC3** (Inline-Validierung):
- Wenn: Speichern mit leerem Pflichtfeld
- Dann: Inline-Fehlerhinweis erscheint; kein Server-Request abgesetzt

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

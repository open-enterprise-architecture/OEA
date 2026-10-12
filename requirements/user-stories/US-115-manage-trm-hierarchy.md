# US-115: TRM-Kategorie-Hierarchie anlegen und reorganisieren

**ID**: US-115
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich TRM-Kategorien anlegen, umbenennen, löschen und per Drag & Drop reorganisieren, damit ich das TRM an die eigene Organisationsstruktur anpassen kann.

## Bezug

**Use Case**: [UC-19](../use-cases/UC-19-trm-konfigurieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-115](../req/REQ-115-trm-hierarchie-verwalten.md)

## Akzeptanzkriterien

**AC1** (Automatisches level):
- Wenn: ich eine Unterkategorie unter „Security Services" anlege
- Dann: ist das `level`-Feld automatisch auf `parent.level + 1` gesetzt

**AC2** (Löschen mit Unterknoten):
- Wenn: ich eine Kategorie mit 3 Unterknoten löschen möchte
- Dann: erhalte ich HTTP 422 „Unterknoten müssen zuerst entfernt werden"

**AC3** (Drag & Drop Zyklus-Schutz):
- Wenn: ich eine Kategorie per Drag & Drop unter einen ihrer eigenen Nachkommen ziehe
- Dann: wird der Drop visuell blockiert; ein API-Versuch antwortet mit HTTP 422

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

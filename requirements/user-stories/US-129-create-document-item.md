# US-129: DocumentItem mit Name, Alias und WYSIWYG-Inhalt anlegen

**ID**: US-129
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Michael – Solution Architekt möchte ich in einer DocumentCollection Kapitel als DocumentItems anlegen können, die jeweils einen Namen, einen optionalen Alias und einen WYSIWYG-Inhalt tragen, damit ich Architekturdokumentation strukturiert und direkt im EA-Tool erfassen kann.

## Bezug

**Use Case**: [UC-09](../use-cases/UC-09-loesungsarchitektur-arc42-dokumentieren.md)
**Persona**: Michael – Solution Architekt (SH-04)
**Requirements**: [REQ-132](../req/REQ-132-document-item-struktur.md)

## Akzeptanzkriterien

**AC1** (Anlegen):
- Wenn: Michael in einer Collection „Neues Kapitel" klickt
- Dann: Neues DocumentItem erscheint mit Fokus auf dem `name`-Feld; `content` leer; `sortOrder` automatisch ans Ende gesetzt

**AC2** (Alias setzen):
- Wenn: Michael den Alias-Wert setzt, der in dieser Collection schon existiert
- Dann: Inline-Fehler; Speichern verhindert

**AC3** (Kapitelnavigation):
- Wenn: Collection mit mehreren Items geöffnet wird
- Dann: Navigation zeigt alle `name`-Werte nach `sortOrder`; aktives Item hervorgehoben

**AC4** (WYSIWYG-Inhalt):
- Wenn: Michael ein Kapitel öffnet und Markdown, Mermaid oder Draw.io einfügt
- Dann: Inhalte werden korrekt gerendert (REQ-068)

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

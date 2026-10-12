# US-059: Text-Widget hinzufügen

**ID**: US-059
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einem Dashboard ein Text-Widget mit Markdown-Inhalt hinzufügen – damit ich zwischen Charts und KPIs erklärenden Text, Methodik-Hinweise oder Kontext für den CIO einbetten kann.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-widget-konfigurieren.md), [REQ-055](../req/REQ-055-dashboard-daten-abrufen.md)

## Akzeptanzkriterien

**AC1** (Text-Widget anlegen):
- Wenn: Kurt legt Text-Widget an: title="Methodische Hinweise", content="**Investitionskosten** basieren auf Schätzungen der jeweiligen Solution Architekten (Stand: Q1 2026). Werte in CHF, ohne MwSt."
- Dann: Widget gespeichert; beim Daten-Abruf erscheint das Widget mit `status=static`; kein DB-Aufruf für Daten

**AC2** (Markdown wird im Web Portal gerendert):
- Gegeben: Text-Widget mit Markdown-Inhalt (Fettschrift, Listen, Links)
- Wenn: CIO öffnet Dashboard im Web Portal
- Dann: Inhalt wird als gerendetes Markdown dargestellt (HTML); kein Raw-Markdown sichtbar

**AC3** (Kein DataSource-Feld):
- Wenn: Text-Widget ohne `dataSource` angelegt
- Dann: HTTP 201; kein 422 wegen fehlendem dataSource (text hat kein dataSource)

**AC4** (Leerer Content erlaubt):
- Wenn: Text-Widget mit content="" angelegt
- Dann: HTTP 201; leeres Widget-Feld im Dashboard (kein Fehler)

## Technische Hinweise

- Widget-Typ: `text`; kein DataSource-Feld; `status=static` in REQ-055-Response
- Markdown-Rendering: serverseitig zu sanitiertem HTML konvertieren (XSS-Schutz: Script-Tags, Event-Handler, unbekannte HTML-Tags entfernen); oder client-seitig mit sanitierter Markdown-Bibliothek
- max. 10 000 Zeichen content

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests: Widget anlegen, Markdown-Rendering, status=static in Data-Response, leerer Content
- [ ] XSS-Check: `<script>alert(1)</script>` in content → nicht ausgeführt
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

---
id: REQ-132
title: DocumentItem als atomare Einheit der DocumentCollection
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - document-collection
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-132: DocumentItem als atomare Einheit der DocumentCollection

## Aussage

Das System MUSS `DocumentItem` als atomare Instanz-Einheit einer `DocumentCollection` bereitstellen. Jeder DocumentItem MUSS folgende Felder tragen:

| Feld | Pflicht | Beschreibung |
|---|---|---|
| `name` | required | Kapitelbezeichnung; wird in der Kapitelnavigation und als Überschrift im Inhalt angezeigt; max. 200 Zeichen |
| `alias` | optional | Kurzname für Querverweise; innerhalb der Collection eindeutig (case-insensitive, kebab-case); max. 60 Zeichen |
| `content` | required | WYSIWYG-Inhalt (Markdown, Mermaid, PlantUML, Draw.io, Entity-Mentions, DocumentItem-Querverweise) |
| `sortOrder` | required | Reihenfolge unter Geschwister-Items; aufsteigend |

Eine DocumentCollection besteht aus einer geordneten Liste von DocumentItems. Die Gesamtzahl der Items pro Collection ist nicht begrenzt; der `content`-Wert ist auf 100.000 Zeichen begrenzt (BR-07).

## Begründung

Die bisherige Trennung von `DocumentChapterDefinition` (Template) und `DocumentationEntry` (Instanz) ist für Nutzer schwer verständlich. `DocumentItem` vereint Struktur und Inhalt in einer Einheit und erlaubt freies Anlegen und Bearbeiten von Kapiteln ohne Template-Bindung.

## Akzeptanzkriterien

**AC1** (Anlegen):
- Wenn: Nutzer in einer DocumentCollection ein neues Kapitel anlegt
- Dann: System erstellt einen DocumentItem mit `name` (Pflicht), optionalem `alias`, leerem `content` und automatisch vergebenem `sortOrder`

**AC2** (Name in Navigation):
- Wenn: DocumentCollection geöffnet wird
- Dann: Kapitelnavigation zeigt `name` jedes Items; Items sind nach `sortOrder` sortiert

**AC3** (Alias eindeutig):
- Wenn: Nutzer `alias` setzt, der innerhalb der Collection bereits vergeben ist
- Dann: Inline-Fehler; Speichern verhindert

**AC4** (Content WYSIWYG):
- Wenn: Nutzer einen DocumentItem öffnet
- Dann: WYSIWYG-Editor ist verfügbar; alle unterstützten Formate (Markdown, Mermaid, PlantUML, Draw.io, `[[`, `{{`) sind nutzbar

## Abhängigkeiten

- **Voraussetzungen**: REQ-068 (WYSIWYG-Editor vorhanden), REQ-069 (Dokumentation bearbeiten)
- **Folgewirkungen**: REQ-133 (Verschachtelung), REQ-134 (Querverweise)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

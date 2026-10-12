# US-046: Katalog anlegen

**ID**: US-046
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einen neuen Katalog mit Name, Beschreibung, primärem Entitätstyp, Scope und Default-Join-Modus anlegen können, damit ich eine Basis-Konfiguration habe, die ich danach mit Spalten und Joins ausbaue.

## Bezug

**Use Case**: [UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-043: Katalog anlegen](../req/REQ-043-katalog-anlegen.md)

## Akzeptanzkriterien

**AC1** (Anlegen, Happy Path):
- Gegeben: Kurt ist eingeloggt; MetamodelConfiguration enthält `ApplicationComponent`
- Wenn: Kurt füllt das Formular aus (Name=„Application Inventory", Typ=ApplicationComponent, Scope=instance, Join-Modus=aggregate) und bestätigt
- Dann: Katalog erscheint in der Katalog-Übersicht; er ist leer (keine Spalten, keine Joins)

**AC2** (Name-Kollision):
- Gegeben: Ein Katalog „Application Inventory" existiert bereits
- Wenn: Kurt legt einen weiteren mit demselben Namen an
- Dann: Inline-Fehlermeldung im Formular „Ein Katalog mit diesem Namen existiert bereits"; kein Datensatz angelegt

**AC3** (Scope=personal):
- Wenn: Sabine legt einen Katalog mit scope=personal an
- Dann: Katalog erscheint nur in Sabines Katalog-Übersicht; Franz sieht ihn nicht

**AC4** (Fehlende Berechtigung):
- Gegeben: Franz hat nur Besucher-Rolle
- Wenn: Franz öffnet die Katalog-Übersicht
- Dann: Schaltfläche „Neuen Katalog anlegen" ist nicht sichtbar oder deaktiviert

## Technische Hinweise

- Betroffene Komponenten: Katalog-Übersicht (Liste + „Neu"-Button), Erstellungsformular, Backend `POST /api/v1/catalogs`
- Primärer Entitätstyp: Dropdown aus MetamodelConfiguration-EntityTypes; live geladen
- Nach Speichern: Weiterleitung in den Spalten-Konfigurator (US-047)

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC4 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Happy Path, Name-Kollision, scope=personal Sichtbarkeit, Berechtigungsprüfung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

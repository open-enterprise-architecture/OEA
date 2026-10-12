# US-055: Dashboard anlegen (instance-scope)

**ID**: US-055
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich ein neues Dashboard mit Name, Beschreibung und Scope anlegen – damit ich für den CIO und andere Stakeholder eine strukturierte C-Level-Ansicht auf die Architektur-Kennzahlen bereitstellen kann.

## Bezug

**Use Case**: [UC-07: Dashboard anlegen und im Web Portal verwenden](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-051: Dashboard anlegen](../req/REQ-051-dashboard-anlegen.md), [REQ-056: Zugriff und Sichtbarkeit](../req/REQ-056-dashboard-zugriff-sichtbarkeit.md)

## Akzeptanzkriterien

**AC1** (Anlegen):
- Gegeben: Kurt ist in der Client App eingeloggt (Rolle Enterprise Architekt)
- Wenn: Kurt klickt „Neues Dashboard anlegen", gibt Name „IT-Investitionsplanung 2026–2030" und Scope=instance ein
- Dann: Dashboard wird angelegt; erscheint in der Dashboard-Liste; im Web Portal für alle Nutzer sichtbar

**AC2** (Validierung: Duplikat-Name):
- Gegeben: Dashboard „IT-Investitionsplanung 2026–2030" existiert bereits
- Wenn: Kurt versucht, ein zweites Dashboard mit demselben Namen anzulegen
- Dann: Fehlermeldung „Name bereits vergeben"; kein zweites Dashboard angelegt

**AC3** (Leeres Dashboard nach Anlage):
- Wenn: Dashboard angelegt
- Dann: Dashboard enthält keine Widgets; Hinweis „Noch keine Widgets. Widget hinzufügen." sichtbar

**AC4** (Kein Zugriff auf fremde personal-Dashboards):
- Gegeben: Franz hat ein scope=personal-Dashboard
- Wenn: Kurt die Dashboard-Liste aufruft
- Dann: Franzs Dashboard erscheint nicht

## Technische Hinweise

- Endpoint: `POST /api/v1/dashboards`
- Berechtigungsprüfung: scope=instance erfordert Dashboard-Schreibberechtigung (BR-02 REQ-051)
- Nach Anlage: Frontend navigiert direkt in den Widget-Editor (leeres Dashboard)

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Code-Review
- [ ] Tests: Anlegen erfolgreich, Duplikat-Name 409, 403 ohne Berechtigung, scope=personal nicht in Liste anderer
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

# US-062: Persönliches Dashboard anlegen und schützen

**ID**: US-062
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich ein persönliches Dashboard für meine eigene Analyse anlegen, das nur für mich sichtbar ist – damit ich meine eigenen Auswertungen ohne Einfluss auf die öffentlichen Dashboards ausprobieren und verfeinern kann.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Michael – Solution Architekt (SH-04); gilt für alle Stakeholder
**Requirements**: [REQ-051](../req/REQ-051-dashboard-anlegen.md), [REQ-056](../req/REQ-056-dashboard-zugriff-sichtbarkeit.md)

## Akzeptanzkriterien

**AC1** (Personal-Dashboard anlegen ohne Sonderberechtigung):
- Gegeben: Michael ist eingeloggt (Rolle Solution Architekt; keine Dashboard-Schreibberechtigung)
- Wenn: Michael legt Dashboard mit scope=personal an
- Dann: HTTP 201; Dashboard angelegt

**AC2** (Nicht in Liste anderer Nutzer):
- Gegeben: Michael hat ein scope=personal-Dashboard
- Wenn: Kurt, Franz oder der CIO GET /api/v1/dashboards aufrufen
- Dann: Michaels Dashboard erscheint in keiner dieser Listen

**AC3** (Direktzugriff via ID geblockt):
- Gegeben: Dritte kennen Michaels Dashboard-ID (z.B. durch Raten)
- Wenn: Direkter GET /api/v1/dashboards/{id} oder /data ohne Berechtigung
- Dann: HTTP 403

**AC4** (Scope-Upgrade mit Berechtigung):
- Gegeben: Michael erhält nachträglich die Dashboard-Schreibberechtigung
- Wenn: PUT mit scope=instance
- Dann: HTTP 200; Dashboard ab sofort für alle sichtbar

**AC5** (scope=instance ohne Berechtigung abgelehnt):
- Gegeben: Michael hat keine Dashboard-Schreibberechtigung
- Wenn: PUT mit scope=instance
- Dann: HTTP 403

## Technische Hinweise

- Keine separate Permission für scope=personal; jeder eingeloggte Nutzer darf persönliche Dashboards anlegen
- `scope=personal`-Dashboards erscheinen nicht in `GET /api/v1/dashboards` anderer Nutzer (serverseitige Filterung nach `createdBy = currentUser`)
- Admins können alle personal-Dashboards sehen (für Support/Moderation)

## Definition of Done

- [ ] AC1–AC5 erfüllt
- [ ] Tests: Anlegen ohne Berechtigung, nicht in fremder Liste, 403-Direktzugriff, Upgrade mit/ohne Berechtigung
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

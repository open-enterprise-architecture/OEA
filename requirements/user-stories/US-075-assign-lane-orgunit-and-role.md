---
id: US-075
title: Lane einer Organisationseinheit und Rolle zuordnen
use_case: UC-10
requirement: REQ-077
priority: must
status: proposed
story_points: 5
created: 2026-06-26
---

# US-075: Lane einer Organisationseinheit und Rolle zuordnen

**Als** Business Analyst (Anna, SH-08)  
**möchte ich** eine Swimlane direkt im Prozessdiagramm einer Organisationseinheit (z.B. Abteilung Einkauf) und einer oder mehreren Rollen (z.B. „Genehmiger") zuordnen,  
**damit** im Prozessmodell sofort ersichtlich ist, wer für welchen Prozessabschnitt verantwortlich ist.

## Akzeptanzkriterien

- [ ] Rechtsklick auf Lane öffnet Kontextmenü mit „Organisationseinheit zuordnen" und „Rolle zuordnen"
- [ ] Autocomplete-Suche listet vorhandene `organizational-unit`-Entitäten; gefiltert nach Eingabe
- [ ] Autocomplete-Suche listet vorhandene `role`-Entitäten; mehrere Rollen pro Lane möglich
- [ ] „Neu anlegen"-Option im Autocomplete-Dialog legt neue OrgUnit oder Rolle direkt im Repository an
- [ ] Nach Zuordnung zeigt Lane OrgUnit-Badge (Kürzel/Name) und Rollen-Chips sichtbar auf Canvas
- [ ] Pool-Header: Rechtsklick → „Teilnehmer setzen" verbindet Pool mit OrgUnit (`bpmn-pool-represents-org-unit`)
- [ ] Bestehende Zuordnung kann über Kontextmenü entfernt werden; Badge/Chip verschwindet sofort
- [ ] Zuordnungen als Katalog-Join abfragbar (AC6 REQ-077)

## Technische Hinweise

- Connection-Typen: `bpmn-lane-belongs-to-org-unit`, `bpmn-lane-performs-role`, `bpmn-pool-represents-org-unit`
- Search-Endpunkt: `GET /api/v1/entities/search?q=...&entityTypeId=organizational-unit` (bzw. `role`)
- BR-03: max. 1 OrgUnit pro Lane (API validiert)

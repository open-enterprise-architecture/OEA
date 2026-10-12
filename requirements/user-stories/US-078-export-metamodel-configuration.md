---
id: US-078
title: Metamodell-Konfiguration exportieren
use_case: UC-04
requirement: REQ-058
priority: should
status: proposed
story_points: 3
created: 2026-06-26
---

# US-078: Metamodell-Konfiguration exportieren

**Als** Lead Enterprise Architekt (Kurt, SH-03)  
**möchte ich** die aktuelle Metamodell-Konfiguration als JSON- oder YAML-Datei exportieren,  
**damit** ich sie in einer anderen OEA-Instanz importieren, als Backup sichern oder mit dem Team versionieren kann.

## Akzeptanzkriterien

- [ ] „Metamodell exportieren"-Button im MetamodelConfiguration-UI verfügbar
- [ ] Export enthält alle EntityTypeDefinitions (inkl. scope, extends, properties, creationSteps)
- [ ] Export enthält alle Connection-Typen und arc42Collections
- [ ] Format: JSON und YAML wählbar
- [ ] scope=built-in Typen sind im Export enthalten aber als unveränderlich markiert
- [ ] Exportierte Datei kann via US-033 (Starter-Paket importieren) wieder importiert werden
- [ ] Dateiname: `oea-metamodel-{instance-slug}-{datum}.json`

## Technische Hinweise

- `GET /api/v1/metamodel/export?format=json|yaml`
- ADR-002: scope-Property im Export mitführen; Import behandelt `scope=built-in` als `scope=imported`

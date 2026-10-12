# US-100: Vollständigen historischen Entitätszustand abrufen

**ID**: US-100
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich den vollständigen Entitätszustand zu jeder historischen Version aufrufen können und sicher sein, dass dieser Snapshot unveränderlich ist, damit ich Compliance-Nachweise lückenlos erbringen kann.

## Bezug

**Use Case**: [UC-14](../use-cases/UC-14-aenderungshistorie-einsehen.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-100](../req/REQ-100-versionshistorie-snapshot-abruf.md)

## Akzeptanzkriterien

**AC1** (Vollständiger Stand aufklappbar):
- Wenn: ich "Vollständiger Stand v4 anzeigen" klicke
- Dann: werden alle Felder inkl. leerer Properties des Snapshots angezeigt

**AC2** (Löschen abgewiesen):
- Wenn: ein DELETE-Request an `/entity-versions/{id}` gesendet wird
- Dann: antwortet die API mit HTTP 405

**AC3** (Bearbeiten abgewiesen):
- Wenn: ein PUT-Request an `/entity-versions/{id}` gesendet wird
- Dann: antwortet die API mit HTTP 405

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

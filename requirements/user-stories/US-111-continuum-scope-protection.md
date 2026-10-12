# US-111: Importierte Continuum-Bausteine vor Änderungen schützen

**ID**: US-111
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich, dass importierte und built-in Continuum-Bausteine nicht bearbeitbar oder löschbar sind, damit die Integrität der Ursprungs-Frameworks und die Traceability zu Paket-Updates erhalten bleiben.

## Bezug

**Use Case**: [UC-17](../use-cases/UC-17-continuum-bausteine-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-111](../req/REQ-111-continuum-scope-schutz.md)

## Akzeptanzkriterien

**AC1** (API-Schreibschutz):
- Wenn: ein PUT- oder DELETE-Request auf einen Baustein mit `scope=imported` abgesetzt wird
- Dann: antwortet das System mit HTTP 422

**AC2** (UI-Schreibschutz und Badge):
- Wenn: ich einen Baustein mit `scope=imported` in der UI öffne
- Dann: sind Bearbeiten- und Löschen-Buttons nicht sichtbar; ein Badge „Importiert – TOGAF 10 TRM" ist sichtbar

**AC3** (SBB-Zuordnungen editierbar):
- Wenn: ich eine TRM-Kategorie mit `scope=imported` öffne
- Dann: kann ich SBB-Zuordnungen bearbeiten; Name und Parent-Kategorie sind gesperrt

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

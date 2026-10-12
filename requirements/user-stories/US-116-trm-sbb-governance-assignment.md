# US-116: SBB-Governance-Zuordnung je TRM-Kategorie pflegen

**ID**: US-116
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich je TRM-Kategorie einen `preferredStandard`, `acceptedAlternatives` und `deprecatedOptions` (SBBs) setzen, damit das TRM echte Steuerungswirkung auf die Ist-Landschaft entfaltet.

## Bezug

**Use Case**: [UC-19](../use-cases/UC-19-trm-konfigurieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-116](../req/REQ-116-trm-sbb-governance-zuordnung.md)

## Akzeptanzkriterien

**AC1** (preferredStandard und Abweichungs-Hinweis):
- Wenn: ich `preferredStandard=PostgreSQL 17` in der Kategorie „Database Management" setze
- Dann: zeigen alle Entitäten in dieser Kategorie mit einem anderen SBB den Hinweis „Abweichung vom TRM-Standard"

**AC2** (Mutually exclusive):
- Wenn: ich denselben SBB gleichzeitig in `preferredStandard` und `acceptedAlternatives` setze
- Dann: erhalte ich HTTP 422 „SBB darf nur in einer Liste erscheinen"

**AC3** (imported-Kategorie editierbar):
- Wenn: ich eine TRM-Kategorie mit `scope=imported` öffne
- Dann: kann ich SBB-Zuordnungen bearbeiten; Name und Parent-Kategorie sind gesperrt

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

# US-088: Go-Live als atomare Transition

**ID**: US-088
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich sicher sein, dass der Go-Live-Übergang entweder vollständig ausgeführt oder vollständig zurückgerollt wird, damit kein inkonsistenter Architekturstatus entstehen kann.

## Bezug

**Use Case**: [UC-11](../use-cases/UC-11-plateau-definieren-und-go-live.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-088](../req/REQ-088-go-live-atomare-transition.md)

## Akzeptanzkriterien

**AC1** (Rollback bei Fehler):
- Wenn: ein technischer Fehler während der Transition auftritt
- Dann: sind P0 und P1 unverändert; kein partieller Zustand ist vorhanden

**AC2** (Erfolgreicher Go-Live):
- Wenn: der Go-Live erfolgreich ausgeführt wird
- Dann: hat P1 `status=baseline`, P0 hat `status=realized` und alle `retiring`-Entitäten haben `lifecycle_state=retired`

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

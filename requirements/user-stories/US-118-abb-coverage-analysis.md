# US-118: ABB-Abdeckungs-Analyse und Gap-Quote einsehen

**ID**: US-118
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich für alle approved ABBs sehen, wie viele konforme Entitäten und implementierende SBBs vorhanden sind, und eine Gap-Quote ablesen können, damit ich Governance-Lücken zwischen definierten Standards und der Ist-Landschaft identifizieren kann.

## Bezug

**Use Case**: [UC-20](../use-cases/UC-20-continuum-conformance-analysieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-118](../req/REQ-118-abb-abdeckungs-analyse.md)

## Akzeptanzkriterien

**AC1** (Gap-Quote):
- Wenn: 5 approved ABBs vorhanden sind, davon 2 ohne Instanz
- Dann: zeigt die Analyse Gap-Quote = 40%; die Tabelle zeigt alle 5 ABBs mit Instanz-Zählungen

**AC2** (ABB-Detailansicht):
- Wenn: ich einen ABB in der Analyse-Tabelle öffne
- Dann: sehe ich implementierende SBBs und konforme Entitäten als anklickbare Links

**AC3** (Leer-Zustand):
- Wenn: noch keine ABBs konfiguriert sind
- Dann: zeigt das System den Hinweis „Noch keine ABBs konfiguriert; starte mit UC-17"

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

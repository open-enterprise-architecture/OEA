# US-120: Technology-Diversity analysieren und als PDF/CSV exportieren

**ID**: US-120
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich je TRM-Kategorie sehen, wie viele unterschiedliche SBBs tatsächlich genutzt werden, und die Analyse mit Executive Summary als PDF oder CSV exportieren, damit ich Standardisierungspotenziale sichtbar machen und Entscheidungsträger informieren kann.

## Bezug

**Use Case**: [UC-20](../use-cases/UC-20-continuum-conformance-analysieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-120](../req/REQ-120-technology-diversity-analyse.md)

## Akzeptanzkriterien

**AC1** (Standardisierungspotenzial hervorheben):
- Wenn: die TRM-Kategorie „Container Orchestration" 3 unterschiedliche genutzte SBBs aufweist
- Dann: wird diese Kategorie als „Standardisierungspotenzial" hervorgehoben

**AC2** (Fehlende Standards auflisten):
- Wenn: TRM-Kategorien ohne konfigurierten `preferredStandard` vorhanden sind
- Dann: erscheinen diese in einer separaten Liste „Fehlende Standards"

**AC3** (PDF-Export mit Executive Summary):
- Wenn: ich den Export als PDF auslöse
- Dann: enthält das Dokument eine Executive-Summary-Sektion mit Top-3-Risiken (prohibited Entitäten, Gap-Quote, Diversity-Hotspots) und Detailtabellen je Analyse-Sektion

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

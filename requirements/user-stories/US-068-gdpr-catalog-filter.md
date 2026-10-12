# US-068: DSGVO-Katalog — Datenflüsse mit personalDataCategories filtern und exportieren

**ID**: US-068
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Data Architekt möchte ich im Katalog alle Datenflüsse, die personenbezogene Daten transportieren, per Filter herausfiltern und als CSV exportieren – damit ich dem Datenschutzbeauftragten eine maschinenlesbare Grundlage für das DSGVO-Verarbeitungsverzeichnis (Art. 30) liefern kann, ohne ein separates Compliance-Tool zu pflegen.

## Bezug

**Use Case**: [UC-08](../use-cases/UC-08-data-lineage-modellieren.md)
**Persona**: Lukas – Senior Data Architekt (SH-02)
**Requirements**: [REQ-064](../req/REQ-064-dsgvo-katalogfilter.md)

## Akzeptanzkriterien

**AC1** (Filter auf personalDataCategories):
- Gegeben: DataFlow id=5 mit `personalDataCategories="Name,Adresse,E-Mail"` und DataFlow id=7 ohne personalDataCategories
- Wenn: Lukas öffnet Katalog mit Filter `entityTypeId=data-flow` + `personalDataCategories ist nicht leer`
- Dann: Nur DataFlow id=5 erscheint; id=7 nicht

**AC2** (Kombinierter Filter):
- Wenn: Lukas fügt zusätzlich `protocol=JDBC` hinzu
- Dann: Nur JDBC-Datenflüsse mit personalDataCategories angezeigt

**AC3** (CSV-Export):
- Gegeben: Katalog zeigt 3 DataFlows nach Filter
- Wenn: Lukas klickt „Als CSV exportieren"
- Dann: CSV-Download; enthält Header + 3 Datenzeilen; Felder: id, name, protocol, personalDataCategories, source, target; UTF-8-Encoding

**AC4** (Leeres Ergebnis kein Fehler):
- Gegeben: Keine DataFlows mit personalDataCategories
- Wenn: Filter angewendet
- Dann: Leere Tabelle; Export liefert nur Header; kein Fehler

## Technische Hinweise

- Filteroperator `isNotEmpty` auf PropertyValue (SQL: `value IS NOT NULL AND value != ''`)
- CSV-Export: Backend-seitig generiert, kein clientseitiges CSV

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests: Filter, Kombination, CSV-Export, Leer-Zustand
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

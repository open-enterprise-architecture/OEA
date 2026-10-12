# US-063: Datenobjekt anlegen und Properties befüllen

**ID**: US-063
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Data Architekt möchte ich ein Datenobjekt (z.B. „Kundenstamm") als eigenständige Entität anlegen und mit Properties wie Datenklassifikation und DSGVO-Kategorien befüllen – damit es eine stabile Integer-ID erhält, die ich in Datenflüssen referenzieren kann.

## Bezug

**Use Case**: [UC-08](../use-cases/UC-08-data-lineage-modellieren.md)
**Persona**: Lukas – Senior Data Architekt (SH-02)
**Requirements**: [REQ-060](../req/REQ-060-datenobjekt-anlegen.md)

## Akzeptanzkriterien

**AC1** (Anlegen mit ID):
- Wenn: Lukas legt Entität „Kundenstamm" mit entityTypeId=`data-object` an
- Dann: Entität erhält Integer-ID ≥ 1; ID ist in der gesamten Instanz eindeutig (keine Kollision mit anderen Entitäten oder Connections)

**AC2** (Properties befüllen):
- Wenn: Lukas setzt `dataClassification=confidential`, `personalDataCategories="Name,Adresse,E-Mail"`
- Dann: Properties gespeichert; Entität abrufbar mit korrekten Werten

**AC3** (Mandatory-Validierung):
- Gegeben: `dataClassification` ist mandatory
- Wenn: Lukas versucht zu speichern ohne dataClassification
- Dann: Fehlermeldung benennt die fehlende Property; nichts gespeichert

**AC4** (entityTypeId unveränderlich):
- Wenn: Lukas versucht den EntityType einer bestehenden Entität zu ändern
- Dann: Änderung abgelehnt; Fehlermeldung erklärt Unveränderlichkeit

## Technische Hinweise

- `POST /api/v1/entities` mit entityTypeId=`data-object`
- Integer-ID wird serverseitig als nächste freie Sequenznummer vergeben (DB-Sequenz)

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests: Anlegen, Properties, Mandatory-Fehler, Typ-Unveränderlichkeit
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

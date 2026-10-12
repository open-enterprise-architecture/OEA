# US-040: Neue Entität im Katalog als Delta anlegen

**ID**: US-040
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich innerhalb einer Solution über ein Formular (Katalog-Ansicht) eine neue Entität anlegen können (deltaType=new), damit ich Systeme und Komponenten beschreibe, die durch diese Initiative erstmalig entstehen.

## Bezug

**Use Case**: [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**Requirement**: [REQ-040: EntityDeltas einer Solution erfassen](../req/REQ-040-entity-deltas-erfassen.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Michael arbeitet an einer Solution im Status `draft`
- Wenn: er im Bereich „Neue Entität anlegen" den Typ `ApplicationComponent` und den Namen `Salesforce` eingibt und speichert
- Dann: erscheint `Salesforce` in der Delta-Liste der Solution mit `deltaType=new`; sie ist **nicht** in der Ausgangsbasis (existiert noch nicht in der Landschaft)

**AC2**:
- Gegeben: Solution hat status=`implemented`
- Wenn: Michael versucht, eine neue Entität anzulegen
- Dann: 409 Conflict „Diese Solution ist abgeschlossen und kann nicht mehr verändert werden"

**AC3**:
- Gegeben: Michael legt `Salesforce (new)` an, und die Delta-Liste zeigt `Salesforce` bereits
- Wenn: er versucht, erneut ein Delta für `Salesforce` hinzuzufügen
- Dann: Validierungsfehler „Für diese Entität existiert bereits ein Delta in dieser Solution"

**AC4**:
- Gegeben: Michael gibt einen EntityType ein, der in der MetamodelConfiguration nicht existiert
- Wenn: er speichert
- Dann: Validierungsfehler; nur typisierte Entitäten aus dem Metamodell sind erlaubt

## Technische Hinweise

- Betroffene Komponenten: Solution-Detailansicht, Katalog-Tab (Formular „Neue Entität"), Backend `POST /api/v1/solutions/{id}/deltas`
- Request-Body: `{ entityName: string, entityType: string, deltaType: "new", properties: {...} }`
- `new`-Deltas referenzieren keine entityId aus der Landschaft; das System vergibt beim Speichern eine neue UUID für die künftige Entität
- EntityType-Dropdown aus MetamodelConfiguration (REQ-032-Daten)
- Diagramm-Pfad (Canvas) ist nicht Teil dieser Story → US-045

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC4 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Delta anlegen, Solution=implemented blockiert, Duplikat-Prüfung, ungültiger EntityType
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-038 (Solution), US-039 (Delta-Backend setzt Solution-Datenstruktur voraus)
- Blockiert: US-043 (Diff-Ansicht braucht mind. ein Delta), US-044 (Konflikt-Check braucht Deltas), US-045 (Diagramm-Pfad teilt dasselbe Backend)

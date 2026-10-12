# US-127: Domain Architekt sieht sensible Felder nur für eigene Domäne

**ID**: US-127
**Story Points**: 8
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Michael – Solution Architekt möchte ich sensible Properties (z.B. `sicherheitseinstufung`) nur für Entitäten sehen, die meiner Domäne über eine `DomainAssignment`-Connection zugeordnet sind, damit ich keine organisationsfremden Vertraulichkeitsdaten einsehe.

## Bezug

**Use Case**: [UC-21](../use-cases/UC-21-property-sichtbarkeit-konfigurieren.md)
**Persona**: Michael – Solution Architekt (SH-04)
**Requirements**: [REQ-127](../req/REQ-127-property-sichtbarkeit-enforcement-connection-scoped.md)

## Akzeptanzkriterien

**AC1** (Traversal positiv — eigene Domäne):
- Gegeben: `sicherheitseinstufung` hat `visibilityMode=connection-scoped`, `scopingConnectionType=DomainAssignment`
- Wenn: Michael ruft Entity A ab, die über `DomainAssignment` mit Michaels Domäne verbunden ist
- Dann: Wert ist sichtbar und editierbar

**AC2** (Traversal negativ — fremde Domäne):
- Wenn: Michael ruft Entity B ab, die NICHT über `DomainAssignment` mit Michaels Domäne verbunden ist
- Dann: Feld ist leer und nicht editierbar

**AC3** (Katalog-View):
- Wenn: Katalog enthält connection-scoped Property als Spalte
- Dann: Zeilen der eigenen Domäne zeigen den Wert; fremde Domänen-Zeilen zeigen leere Zelle

**AC4** (Performance):
- Wenn: Michaels Domäne hat bis zu 500 direkt zugeordnete Entitäten
- Dann: Traversal-Overhead p95 ≤ 200 ms (gecacht, TTL 60 s)

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Graph-Traversal-Logik mit Unit-Tests abgedeckt
- [ ] Cache-Invalidierung bei Connection-Änderung getestet
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen

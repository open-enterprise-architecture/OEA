# US-008: Login-Latenz-Ziele erfüllen und messen

**ID**: US-008
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich, dass der Login-Vorgang ein definiertes Latenzbudget einhält, damit die tägliche Nutzung des Tools nicht durch langsame Anmeldung beeinträchtigt wird.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-008: Login-Latenz](../req/REQ-008-login-latenz.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein Repository mit 10.000 Personen und 1.000 Rollen
- Wenn: 95% der OIDC-Login-Vorgänge gemessen werden
- Dann: liegt die serverseitige Verarbeitungszeit unter 300ms

**AC2**:
- Gegeben: ein Repository mit 10.000 aktiven API-Keys
- Wenn: 95% der API-Key-Validierungen gemessen werden
- Dann: liegt die Verarbeitungszeit unter 100ms

## Technische Hinweise

- Betroffene Komponenten: Auth-Modul, Caching-Layer für Rollenzuweisungen
- Betroffene EntityTypes/Relations: `Person`, `Role`, `RoleAssignment`
- API-Endpunkte: alle Login-/Auth-Endpunkte
- Datenbank-Änderungen: ggf. zusätzliche Indizes, Cache-Layer (Lösung offen, kein Solution-Design hier)

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Lasttest mit synthetischem Datensatz geschrieben und in CI als Performance-Gate integriert
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung (z.B. Cache-Strategie)
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-001, US-002
- Blockiert: keine

## Notizen

Zielwerte sind vorläufige Walking-Skeleton-Annahmen (siehe REQ-008), bei realer Lastmessung zu validieren. Sollte erst sinnvoll messbar sein, wenn US-001/US-002 stehen.

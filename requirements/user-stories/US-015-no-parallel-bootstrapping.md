# US-015: Kein paralleles oder wiederholtes Bootstrapping

**ID**: US-015
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich, dass nur ein Bootstrapping-Vorgang pro Instanz erfolgreich abgeschlossen werden kann, damit kein unautorisierter zweiter System-Admin-Account entstehen kann.

## Bezug

**Use Case**: [UC-02: System-Admin-Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-015: Kein paralleles oder wiederholtes Bootstrapping](../req/REQ-015-kein-paralleles-bootstrapping.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: zwei nahezu gleichzeitig gestartete Bootstrapping-Versuche auf derselben frischen Instanz
- Wenn: beide um Abschluss konkurrieren
- Dann: wird genau einer erfolgreich abgeschlossen, der andere abgelehnt

**AC2**:
- Gegeben: eine Instanz mit bereits bestehendem System-Admin-Account
- Wenn: ein erneuter Bootstrapping-Versuch unternommen wird
- Dann: wird dieser ohne explizite Reset-Aktion abgelehnt

## Technische Hinweise

- Betroffene Komponenten: Bootstrapping-Endpunkt
- Betroffene EntityTypes/Relations: `SystemAdminAccount`
- API-Endpunkte: Bootstrapping-Endpunkt
- Datenbank-Änderungen: atomarer Check-and-Set-Mechanismus (DB-Constraint oder Distributed Lock)

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person (mit Fokus Concurrency)
- [ ] Tests geschrieben (Concurrency-Test für AC1)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-013
- Blockiert: keine

## Notizen

Deckt Exception Flow E3 aus UC-02 ab. Sicherheitskritisch – Race-Condition-Tests sind Pflichtbestandteil des Tests, nicht optional.

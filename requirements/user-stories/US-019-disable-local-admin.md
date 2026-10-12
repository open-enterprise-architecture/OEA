# US-019: Deaktivierbarkeit des lokalen System-Admin-Accounts

**ID**: US-019
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als System-Admin mit funktionierendem Remote-Mapping möchte ich den ursprünglichen lokalen Bootstrap-Account deaktivieren können, damit die Instanz nach Erstkonfiguration keinen zusätzlichen, unkontrollierten Zugang mehr offen hat.

## Bezug

**Use Case**: [UC-02: System-Admin-Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-019: Deaktivierbarkeit des lokalen System-Admin-Accounts](../req/REQ-019-deaktivierbarkeit-lokaler-admin.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein aktiver lokaler System-Admin-Account und ein funktionsfähiges Remote-Mapping
- Wenn: ein System-Admin den lokalen Account deaktiviert
- Dann: ist eine Anmeldung über den lokalen Account danach nicht mehr möglich, während der Remote-Weg weiter funktioniert

**AC2**:
- Gegeben: kein funktionsfähiges Remote-Mapping konfiguriert
- Wenn: versucht wird, den lokalen Account zu deaktivieren
- Dann: warnt das System vor einem möglichen vollständigen Lockout, blockiert die Aktion aber nicht zwingend

## Technische Hinweise

- Betroffene Komponenten: Admin-Verwaltungs-UI/CLI
- Betroffene EntityTypes/Relations: `SystemAdminAccount.active`
- API-Endpunkte: Verwaltungs-Endpunkt für System-Admin-Accounts
- Datenbank-Änderungen: keine zusätzlichen (Lifecycle-Übergang `active → deactivated`)

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Deaktivierung mit/ohne funktionsfähiges Remote-Mapping)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-013, US-014, US-018
- Blockiert: keine

## Notizen

Warnung statt Hard-Block, um nicht selbst einen Lockout (UC-02, E4) zu erzeugen.

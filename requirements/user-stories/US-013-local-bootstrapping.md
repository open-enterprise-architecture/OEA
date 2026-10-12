# US-013: Lokales Bootstrapping bei fehlendem System-Admin-Account

**ID**: US-013
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Operator einer frisch installierten OEA-Instanz möchte ich einen lokalen System-Admin-Account einrichten können, damit ich die Instanz konfigurieren kann, ohne vorher einen externen Identity-Provider anbinden zu müssen.

## Bezug

**Use Case**: [UC-02: System-Admin-Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-013: Lokales Bootstrapping bei fehlendem System-Admin-Account](../req/REQ-013-lokales-bootstrapping.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine frisch installierte Instanz ohne System-Admin-Account
- Wenn: sie zum ersten Mal gestartet bzw. aufgerufen wird
- Dann: bietet das System den lokalen Bootstrapping-Vorgang an, ohne einen externen IdP vorauszusetzen

**AC2**:
- Gegeben: ein abgeschlossener lokaler Bootstrapping-Vorgang
- Wenn: der Operator sich mit dem festgelegten Credential anmeldet
- Dann: erhält er Zugriff mit System-Admin-Rechten

## Technische Hinweise

- Betroffene Komponenten: Setup-Wizard/CLI, Auth-Modul
- Betroffene EntityTypes/Relations: `SystemAdminAccount` (siehe `system-admin-account.md`)
- API-Endpunkte: Bootstrapping-Endpunkt (nur erreichbar, solange kein Account existiert)
- Datenbank-Änderungen: neue Tabelle/Collection für `SystemAdminAccount`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Integrationstest auf frischer Instanz)
- [ ] Dokumentation aktualisiert (Installationsanleitung)
- [ ] ADR erstellt, falls Architekturentscheidung (siehe ADR-006)
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: keine
- Blockiert: US-014, US-015, US-016, US-017, US-019

## Notizen

Deckt den Hauptablauf aus UC-02 ab. Setup-Token-Übergabe selbst ist Gegenstand von US-017.

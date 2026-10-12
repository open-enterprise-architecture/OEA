# US-018: Warnung bei leerem oder nicht-existierendem Remote-Admin-Claim

**ID**: US-018
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Operator möchte ich beim Konfigurieren eines Remote-Bootstrapping-Mappings gewarnt werden, wenn die angegebene Gruppe leer oder nicht vorhanden ist, damit ich mich nicht versehentlich aus der Instanz aussperre.

## Bezug

**Use Case**: [UC-02: System-Admin-Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-018: Warnung bei leerem Remote-Admin-Claim](../req/REQ-018-warnung-leerer-admin-claim.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein Remote-Mapping auf eine zum Konfigurationszeitpunkt leere oder nicht existierende Gruppe
- Wenn: Max die Konfiguration speichert
- Dann: erhält er eine Warnung, dass dieses Mapping aktuell niemandem Zugriff verleiht

**AC2**:
- Gegeben: dieselbe Situation
- Wenn: Max die Warnung zur Kenntnis nimmt
- Dann: kann er die Konfiguration trotzdem speichern (kein Hard-Block)

## Technische Hinweise

- Betroffene Komponenten: Konfigurations-UI/CLI für Remote-Mapping, IdP-API-Client
- Betroffene EntityTypes/Relations: `SystemAdminAccount` mit `mode: remote`
- API-Endpunkte: Konfigurations-Endpunkt für Claim-Mapping (Validierungs-Aufruf)
- Datenbank-Änderungen: keine zusätzlichen

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Test-IdP mit leerer/nicht-existierender Gruppe)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-014
- Blockiert: keine

## Notizen

Prüfung ist Best-Effort (abhängig von Provider-API-Zugriff, z.B. Microsoft Graph), kein verpflichtender Hard-Block.

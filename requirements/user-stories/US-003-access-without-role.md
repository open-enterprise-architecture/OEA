# US-003: Eingeschränkter Zugriff bei fehlender aktiver Rollenzuweisung

**ID**: US-003
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Person ohne aktive Rollenzuweisung möchte ich mich trotzdem anmelden können, damit ich zumindest öffentlich-lesbare Inhalte sehe und einen Hinweis erhalte, dass mir noch keine Rolle zugewiesen wurde.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-003: Eingeschränkter Zugriff bei fehlender Rollenzuweisung](../req/REQ-003-zugriff-ohne-rollenzuweisung.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine erfolgreich authentifizierte Person ohne aktive Rollenzuweisung
- Wenn: sie auf das Repository zugreift
- Dann: erhält sie ausschließlich Zugriff auf öffentlich-lesbare Inhalte und einen Hinweis, dass keine Rolle zugewiesen ist

**AC2**:
- Gegeben: eine Person erhält nachträglich eine aktive Rollenzuweisung
- Wenn: sie sich erneut anmeldet oder die Session aktualisiert wird
- Dann: spiegeln ihre Berechtigungen die neue Rolle wider

## Technische Hinweise

- Betroffene Komponenten: Autorisierungs-Modul
- Betroffene EntityTypes/Relations: `Person`, `Role`, `RoleAssignment`
- API-Endpunkte: Autorisierungs-Check bei jedem geschützten Endpunkt
- Datenbank-Änderungen: keine zusätzlichen

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Unit, Integration)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-001
- Blockiert: keine

## Notizen

Deckt Exception Flow E3 aus UC-01 ab. Setzt Konzept-Constraint `person-minimum-role` als Warning, nicht Hard-Block, um.

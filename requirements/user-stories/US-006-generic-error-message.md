# US-006: Generische Fehlermeldung ohne Preisgabe der Account-Existenz

**ID**: US-006
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Sicherheitsverantwortlicher möchte ich, dass fehlgeschlagene Login-Versuche keine Rückschlüsse auf die Existenz eines Accounts erlauben, damit Angreifer keine User-Enumeration betreiben können.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-006: Keine Preisgabe der Account-Existenz](../req/REQ-006-keine-preisgabe-account-existenz.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein Login-Versuch mit nicht-existierender Identität
- Wenn: der Login fehlschlägt
- Dann: ist die Fehlermeldung identisch zu der bei einer existierenden, aber falsch authentifizierten Identität

**AC2**:
- Gegeben: zwei Login-Fehlversuche (existierende vs. nicht-existierende Identität)
- Wenn: die Antwortzeiten verglichen werden
- Dann: unterscheiden sie sich nicht in auswertbarer Weise (kein Timing-Seitenkanal)

## Technische Hinweise

- Betroffene Komponenten: Auth-Modul (Fehlerbehandlung, alle Mechanismen)
- Betroffene EntityTypes/Relations: keine
- API-Endpunkte: alle Login-Endpunkte
- Datenbank-Änderungen: keine

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person (mit Fokus Security)
- [ ] Tests geschrieben (inkl. Timing-Vergleich)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-001, US-004
- Blockiert: keine

## Notizen

Sollte bei jedem neuen lokalen Mechanismus (US-010, US-011) erneut verifiziert werden, da dort Passwort-Korrektheit ein zusätzlicher Leak-Vektor ist (siehe REQ-010).

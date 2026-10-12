# US-004: Ablehnung der Session-Erstellung für unbekannte oder offboardete Personen

**ID**: US-004
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich, dass Personen, die im Repository nicht (mehr) als aktiv bekannt sind, keine Session erhalten, damit ehemalige Mitarbeitende trotz gültigem IdP-Token keinen Zugriff bekommen.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-004: Ablehnung bei unbekannter/offboardeter Person](../req/REQ-004-ablehnung-unbekannte-offboarded-person.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein gültiges Identity-Token, das keiner Person im Repository zugeordnet werden kann
- Wenn: der Login-Vorgang abgeschlossen wird
- Dann: wird keine Session erstellt

**AC2**:
- Gegeben: ein gültiges Identity-Token, dessen zugeordnete Person den Status `offboarded` hat
- Wenn: der Login-Vorgang abgeschlossen wird
- Dann: wird keine Session erstellt

## Technische Hinweise

- Betroffene Komponenten: Auth-Modul (Person-Lookup)
- Betroffene EntityTypes/Relations: `Person` (Lifecycle-Status)
- API-Endpunkte: OIDC-Callback-Endpunkt
- Datenbank-Änderungen: Index auf `Person.externalReference` für schnellen Lookup

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
- Blockiert: US-005 (Audit-Log sollte diesen Fall mit abdecken)

## Notizen

Deckt Exception Flow E2 aus UC-01 ab.

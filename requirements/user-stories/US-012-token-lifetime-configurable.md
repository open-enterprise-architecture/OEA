# US-012: Konfigurierbare Token-Lebensdauer bei lokaler Authentifizierung

**ID**: US-012
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber einer Instanz mit lokaler Authentifizierung möchte ich die Lebensdauer von Access- und Refresh-Token konfigurieren können, damit ich die Abwägung zwischen Sicherheit und Usability selbst treffen kann.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-012: Konfigurierbare Token-Lebensdauer bei lokaler Authentifizierung](../req/REQ-012-token-lebensdauer-lokal.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Instanz mit lokal konfigurierter Access-Token-Lebensdauer von 15 Minuten
- Wenn: eine Person sich über einen lokalen Mechanismus anmeldet
- Dann: läuft das Access-Token nach 15 Minuten ab

**AC2**:
- Gegeben: ein abgelaufenes Access-Token, aber ein noch gültiges Refresh-Token
- Wenn: ein Refresh angefordert wird
- Dann: wird ein neues Access-Token mit der konfigurierten Lebensdauer ausgestellt

## Technische Hinweise

- Betroffene Komponenten: Instanz-Konfiguration, Token-Erstellung (lokale Mechanismen)
- Betroffene EntityTypes/Relations: keine fachlichen
- API-Endpunkte: Token-Refresh-Endpunkt
- Datenbank-Änderungen: Konfigurationswerte `accessTokenLifetime`, `refreshTokenLifetime`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Ablauf- und Refresh-Verhalten bei unterschiedlichen Werten)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-009, US-010 oder US-011 (mindestens ein lokaler Mechanismus muss existieren)
- Blockiert: keine

## Notizen

Gilt nicht für extern ausgestellte Token (dort bestimmt der IdP die Lebensdauer, siehe US-001 AC3).

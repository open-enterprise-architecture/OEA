# US-011: Username/Passwort-Login ohne zweiten Faktor (Minimal-Variante)

**ID**: US-011
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Operator einer einfachen Self-Hosting-Instanz möchte ich bewusst die Minimal-Variante Username/Passwort ohne zweiten Faktor aktivieren können, damit ich auch ohne Passkey- oder TOTP-Infrastruktur einen lokalen Zugang anbieten kann.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-011: Username/Passwort-Login ohne zweiten Faktor](../req/REQ-011-username-passwort-minimal.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Instanz, auf der diese Variante explizit aktiviert wurde, und eine Person mit korrektem Passwort
- Wenn: sie sich anmeldet
- Dann: erhält sie eine Session gemäß ihren aktiven Rollenzuweisungen

**AC2**:
- Gegeben: eine neu installierte, nicht konfigurierte OEA-Instanz
- Wenn: ihr Authentifizierungs-Setup geprüft wird
- Dann: ist diese Variante standardmäßig deaktiviert

**AC3**:
- Gegeben: jeder Login über diese Variante
- Wenn: der Audit-Log-Eintrag erzeugt wird
- Dann: ist erkennbar, dass kein zweiter Faktor verwendet wurde

## Technische Hinweise

- Betroffene Komponenten: Auth-Modul, Instanz-Konfiguration
- Betroffene EntityTypes/Relations: Passwort-Hash (geteilt mit US-010)
- API-Endpunkte: Login-Endpunkt (einstufig)
- Datenbank-Änderungen: Konfigurations-Flag für Aktivierung dieser Variante

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person (mit Fokus Security, da schwächster Mechanismus)
- [ ] Tests geschrieben (inkl. Default-Konfiguration einer frischen Installation)
- [ ] Dokumentation aktualisiert (inkl. Sicherheits-Warnhinweis im Setup-Wizard)
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-001, US-006, US-023 (Passwort-Enrollment muss existieren)
- Blockiert: keine

## Notizen

Aktivierung muss eine bewusste, mit Warnung versehene Konfigurationsentscheidung sein, nicht in einer Config-Datei versteckt (siehe REQ-011, Realisierungs-Hinweise).

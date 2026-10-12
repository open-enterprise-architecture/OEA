---
id: REQ-003
title: Eingeschränkter Zugriff bei fehlender aktiver Rollenzuweisung
type: business-rule
priority: must
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
    - role
  business_rules:
    - BR-01
  stakeholders:
    - SH-03
  concept:
    - concept/20-entities/08-organisation-rollen-personen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-003: Eingeschränkter Zugriff bei fehlender aktiver Rollenzuweisung

## Aussage

Eine erfolgreich authentifizierte Person OHNE aktive Rollenzuweisung DARF NICHT auf andere als öffentlich-lesbare Inhalte des Repositorys zugreifen.

## Begründung

Konzept-Constraint `person-minimum-role` (§8.8) ist als Warning, nicht als Hard-Block modelliert – eine Person ohne Rolle soll sich anmelden können (z.B. für Onboarding), aber keine Berechtigungen über das Minimum hinaus erhalten. UC-01, Exception Flow E3.

## Kontext

Betrifft den Fall, dass eine Person beim Identity-Provider erfolgreich authentifiziert wurde, aber im EA-Repository (noch) keine aktive `RoleAssignment` besitzt.

## Typ-spezifische Felder

### Bei type=business-rule

**Auslöser**: onLogin

**Betroffene Business Objects**: [person](../../business-objects/person.md), [role](../../business-objects/role.md)

**Formale Aussage** (vereinfacht):
```
if Person.fillsRoles.filter(active=true).isEmpty():
    Session.permissions = PUBLIC_READ_ONLY
```

**Beispiel**: Eine neu eingeladene Person hat noch keine Rollenzuweisung erhalten. Sie kann sich anmelden, sieht aber nur öffentlich freigegebene Inhalte, bis eine Person mit Autorisierungsrecht ihr eine Rolle zuweist.

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Person ist erfolgreich authentifiziert, hat aber keine aktive Rollenzuweisung
- Wenn: sie auf das Repository zugreift
- Dann: erhält sie ausschließlich Zugriff auf öffentlich-lesbare Inhalte und einen Hinweis, dass keine Rolle zugewiesen ist

**AC2**:
- Gegeben: eine Person erhält nachträglich eine aktive Rollenzuweisung
- Wenn: sie sich erneut anmeldet oder die Session aktualisiert wird
- Dann: spiegeln ihre Berechtigungen die neue Rolle wider

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Login mit Testperson ohne RoleAssignment, Prüfung der resultierenden Berechtigungen
- [x] Mess-Werkzeug: Test-Suite des Auth-/Autorisierungsmoduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-001
- **Folgewirkungen**: keine bekannt
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne diese Regel könnten neu authentifizierte Personen versehentlich vollen Zugriff erhalten – Sicherheitsrisiko, schwerwiegend
- Risiko 2: Wird die Regel als Hard-Block statt Soft-Limit umgesetzt, blockiert sie das Onboarding neuer Personen unnötig

## Trade-offs

- vs. Benutzerfreundlichkeit: ein Hard-Block beim Login ohne Rolle wäre einfacher zu implementieren, widerspricht aber dem Konzept-Constraint (Warning statt Error) und würde Onboarding erschweren

## Realisierungs-Hinweise

- Session-Refresh-Mechanismus nötig, damit neu zugewiesene Rollen ohne vollständigen Re-Login wirksam werden (siehe REQ-001 Realisierungs-Hinweise zu Caching)

## Realisierung

- ADR(s): keine
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-01 Login, Exception Flow E3, und Konzept-Constraint `person-minimum-role`.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |

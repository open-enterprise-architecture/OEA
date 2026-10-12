---
id: REQ-015
title: Kein paralleles oder wiederholtes Bootstrapping
type: business-rule
priority: must
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - system-admin-account
  business_rules:
    - BR-01
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-015: Kein paralleles oder wiederholtes Bootstrapping

## Aussage

Das System DARF NICHT mehr als einen aktiven Bootstrapping-Vorgang gleichzeitig zulassen, und MUSS einen erneuten Bootstrapping-Versuch verweigern, sobald für die Instanz bereits ein [System-Admin-Account](../../business-objects/system-admin-account.md) existiert.

## Begründung

Ohne diese Regel könnte ein Race Condition zwischen zwei parallelen Bootstrapping-Versuchen zu inkonsistenten Zuständen führen, oder ein Angreifer mit kurzfristigem Zugriff auf eine bereits konfigurierte Instanz könnte versuchen, einen zusätzlichen, eigenen System-Admin-Account anzulegen. UC-02, Business Rule BR-01 (`system-admin-account.md`) und Exception Flow E3.

## Kontext

Gilt für den Zeitraum der Erstinstallation (Race-Condition-Schutz) sowie für jeden späteren Zeitpunkt, an dem bereits ein Account existiert (Wiederholungs-Schutz).

## Typ-spezifische Felder

### Bei type=business-rule

**Auslöser**: onCreate (Versuch, einen System-Admin-Account anzulegen)

**Betroffene Business Objects**: [system-admin-account](../../business-objects/system-admin-account.md)

**Formale Aussage** (vereinfacht):
```
if exists(SystemAdminAccount for instance) or bootstrappingInProgress(instance):
    reject(new bootstrapping attempt)
```

**Beispiel**: Zwei parallele Setup-Wizard-Aufrufe auf derselben frisch installierten Instanz dürfen nicht zu zwei unterschiedlichen System-Admin-Accounts führen; nur der erste abgeschlossene Vorgang gewinnt, der zweite wird abgelehnt.

## Akzeptanzkriterien

**AC1**:
- Gegeben: zwei nahezu gleichzeitig gestartete Bootstrapping-Versuche auf derselben frischen Instanz
- Wenn: beide um Abschluss konkurrieren
- Dann: wird genau einer erfolgreich abgeschlossen, der andere abgelehnt

**AC2**:
- Gegeben: eine Instanz mit bereits bestehendem System-Admin-Account
- Wenn: ein erneuter Bootstrapping-Versuch unternommen wird
- Dann: wird dieser ohne explizite Reset-Aktion durch einen bestehenden System-Admin-Account abgelehnt

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: parallele Bootstrapping-Versuche (Concurrency-Test), Bootstrapping-Versuch auf bereits konfigurierter Instanz
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-013 und/oder REQ-014 (es muss überhaupt einen Bootstrapping-Mechanismus geben, den diese Regel einschränkt)
- **Folgewirkungen**: keine bekannt
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne diese Regel könnte ein zweiter, unautorisierter System-Admin-Account neben dem legitimen entstehen – schwerwiegendes Sicherheitsrisiko (Privilege-Escalation-Vektor)

## Trade-offs

- keine bekannt

## Realisierungs-Hinweise

- Erfordert einen atomaren Check-and-Set-Mechanismus (z.B. Datenbank-Constraint oder Distributed Lock), nicht nur eine einfache Existenzprüfung vor dem Schreiben

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-02, Exception Flow E3, und Business Rule BR-01 aus `system-admin-account.md`.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-02 |

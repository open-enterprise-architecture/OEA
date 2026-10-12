---
id: REQ-007
title: Lifecycle-Übergang invited→active beim ersten erfolgreichen Login
type: business-rule
priority: should
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
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/20-entities/08-organisation-rollen-personen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-007: Lifecycle-Übergang invited→active beim ersten erfolgreichen Login

## Aussage

Das System SOLL den Lifecycle-Status einer [Person](../../business-objects/person.md) von `invited` zu `active` ändern, sobald ihr erster erfolgreicher Login-Vorgang abgeschlossen ist.

## Begründung

UC-01, Nachbedingung bei Erfolg. Der Lifecycle von Person (siehe `person.md`) unterscheidet `invited` (im System referenziert, aber noch nicht authentifiziert) von `active`. Der Login ist der definierte Übergangs-Trigger.

## Kontext

Betrifft Personen, die neu im Repository angelegt/referenziert wurden, sich aber noch nicht angemeldet haben.

## Typ-spezifische Felder

### Bei type=business-rule

**Auslöser**: onLogin (genauer: beim ersten erfolgreichen Login)

**Betroffene Business Objects**: [person](../../business-objects/person.md)

**Formale Aussage** (vereinfacht):
```
if Person.status == "invited" and Login.result == "success":
    Person.status = "active"
```

**Beispiel**: Eine neu eingeladene Person meldet sich zum ersten Mal über den Identity-Provider an; ihr Status wechselt von `invited` zu `active`, unabhängig davon, ob ihr bereits eine Rolle zugewiesen ist (siehe REQ-003).

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Person mit Status `invited`
- Wenn: sie sich erstmals erfolgreich anmeldet
- Dann: wechselt ihr Status zu `active`

**AC2**:
- Gegeben: eine Person mit Status `active`
- Wenn: sie sich erneut anmeldet
- Dann: bleibt ihr Status unverändert `active` (kein erneuter Übergang, keine Seiteneffekte)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Login-Simulation für `invited`-Person und für bereits `active`-Person
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-001
- **Folgewirkungen**: keine bekannt
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne automatischen Übergang bliebe der Lifecycle-Status manuell zu pflegen – Inkonsistenz-Risiko, geringer Schweregrad

## Trade-offs

- keine bekannt

## Realisierungs-Hinweise

- Übergang sollte idempotent sein (mehrfacher Login bei bereits `active`-Status darf keinen Fehler werfen)

## Realisierung

- ADR(s): keine
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-01 Login, Nachbedingung bei Erfolg, und Lifecycle-Definition in `person.md`.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |

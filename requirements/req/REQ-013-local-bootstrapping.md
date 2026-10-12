---
id: REQ-013
title: Lokales Bootstrapping bei fehlendem System-Admin-Account
type: functional
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
  business_rules: []
  stakeholders:
    - SH-06
    - SH-03
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-013: Lokales Bootstrapping bei fehlendem System-Admin-Account

## Aussage

Das System MUSS erkennen, wenn für eine Instanz noch kein [System-Admin-Account](../../business-objects/system-admin-account.md) existiert, und in diesem Fall einen lokalen Bootstrapping-Vorgang anbieten, der ohne externen Identity-Provider auskommt.

## Begründung

Eine frisch installierte OEA-Instanz hat per Definition keine Person-/Role-Daten und ggf. keinen konfigurierten IdP. Ohne einen Weg, der ohne externe Abhängigkeit funktioniert, könnte eine Instanz ohne bereits eingerichteten IdP nie administriert werden. UC-02, Hauptablauf.

## Kontext

Betrifft ausschließlich den Zustand "kein System-Admin-Account vorhanden" einer Instanz. Sobald ein Account existiert, greift REQ-015 (keine wiederholten Bootstrapping-Vorgänge).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Start der Instanz ohne vorhandenen System-Admin-Account
- Vom Operator gewähltes initiales Credential (Passwort) oder entgegengenommenes generiertes Setup-Token

**Verarbeitung**:
- Prüfung beim Start/ersten Zugriff, ob ein System-Admin-Account existiert
- Falls nicht: Generierung eines einmaligen Setup-Tokens oder Aufforderung zur Passwort-Festlegung
- Anlegen des System-Admin-Accounts mit `mode: local` nach Bestätigung des Credentials

**Ausgaben**:
- Lokaler System-Admin-Account im Zustand `bootstrapped`
- Bestätigung an den Operator, dass das Bootstrapping abgeschlossen ist

**Fehlerfälle**:
- Vorgang wird abgebrochen, bevor das Credential bestätigt ist → kein Account wird angelegt, Vorgang kann erneut gestartet werden (siehe UC-02, E1)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine frisch installierte Instanz ohne System-Admin-Account
- Wenn: sie zum ersten Mal gestartet bzw. aufgerufen wird
- Dann: bietet das System den lokalen Bootstrapping-Vorgang an, ohne einen externen IdP vorauszusetzen

**AC2**:
- Gegeben: ein abgeschlossener lokaler Bootstrapping-Vorgang
- Wenn: der Operator sich mit dem festgelegten Credential anmeldet
- Dann: erhält er Zugriff mit System-Admin-Rechten

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Start einer Instanz ohne vorhandene Konfiguration, Durchlauf des Bootstrapping-Vorgangs
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls / Integrationstest auf frischer Instanz
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: keine
- **Folgewirkungen**: REQ-015 (keine wiederholten Vorgänge), REQ-016 (Audit-Log), REQ-017 (sichere Token-Übergabe)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne lokalen Bootstrapping-Weg wäre jede Instanz ohne vorab eingerichteten IdP unadministrierbar – schwerwiegend, blockiert jegliche Nutzung

## Trade-offs

- vs. Sicherheit: ein lokaler Bootstrap-Weg ist ein zusätzlicher Angriffsvektor, der nach Erstkonfiguration ggf. deaktiviert werden sollte (siehe REQ-019)

## Realisierungs-Hinweise

- Erkennung "kein Account vorhanden" muss race-condition-sicher sein (siehe REQ-015)
- Setup-Token-Übergabe siehe REQ-017

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-02, Hauptablauf.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-02 |

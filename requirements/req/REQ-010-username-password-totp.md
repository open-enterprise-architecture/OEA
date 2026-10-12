---
id: REQ-010
title: Username/Passwort-Login mit TOTP als zweitem Faktor
type: functional
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
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-010: Username/Passwort-Login mit TOTP als zweitem Faktor

## Aussage

Das System SOLL Personen die Anmeldung mit Username/Passwort und einem TOTP-Code als zweitem Faktor ermöglichen, wenn die OEA-Instanz lokale Authentifizierung ohne Passkey-Unterstützung anbietet.

## Begründung

Konzept §21.8: TOTP als zweiter Faktor ist für Umgebungen vorgesehen, die WebAuthn/Passkey nicht unterstützen (z.B. ältere Clients). UC-01, Alternative A4.

## Kontext

Setzt voraus, dass die Person zuvor ein Passwort gesetzt und ein TOTP-Secret registriert hat (Enrollment ist eigener Use Case).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Username, Passwort
- TOTP-Code (6-stellig, zeitbasiert)

**Verarbeitung**:
- Prüfung des Passwort-Hashes gegen den gespeicherten Hash der Person
- Bei korrektem Passwort: Anforderung des TOTP-Codes
- Verifikation des TOTP-Codes gegen das registrierte Secret der Person (inkl. Zeitfenster-Toleranz)

**Ausgaben**:
- Bei erfolgreicher Verifikation beider Faktoren: Fortsetzung mit Person-/Rollen-Lookup (siehe REQ-001, Schritt 6)

**Fehlerfälle**:
- Falsches Passwort → generische Fehlermeldung (siehe REQ-006), kein Hinweis, ob Username existiert
- Falscher oder abgelaufener TOTP-Code → generische Fehlermeldung, kein Hinweis darauf, dass das Passwort korrekt war (verhindert partielle Informationspreisgabe)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Person mit korrektem Passwort und gültigem TOTP-Code
- Wenn: sie sich anmeldet
- Dann: erhält sie eine Session gemäß ihren aktiven Rollenzuweisungen

**AC2**:
- Gegeben: ein korrektes Passwort, aber ein falscher TOTP-Code
- Wenn: der Login-Versuch abgeschlossen wird
- Dann: wird keine Session erstellt, und die Fehlermeldung lässt nicht erkennen, dass das Passwort korrekt war

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Login-Versuche mit korrektem/falschem Passwort kombiniert mit korrektem/falschem TOTP-Code
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün, keine partielle Informationspreisgabe
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-022 (TOTP-Enrollment) und REQ-024 (Passwort-Enrollment) müssen vorher abgeschlossen sein
- **Folgewirkungen**: keine bekannt
- **Konflikte**: REQ-006 (Fehlermeldungen dürfen Passwort-Korrektheit nicht durchsickern lassen)

## Risiken bei Nichterfüllung

- Risiko 1: Ohne TOTP als Option bleibt für Clients ohne WebAuthn-Unterstützung nur reines Passwort (REQ-011), was schwächer ist – mittlerer Schweregrad

## Trade-offs

- vs. Usability: zusätzlicher Eingabeschritt (TOTP-Code) erhöht Login-Aufwand gegenüber reinem Passwort oder Passkey

## Realisierungs-Hinweise

- Passwort-Hash (z.B. Argon2id) und TOTP-Secret getrennt vom fachlichen Person-Objekt speichern (sensible Credential-Daten)
- Rate-Limiting für TOTP-Versuche vorsehen (Brute-Force-Schutz bei nur 6-stelligem Code)

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-01 Login, Alternative A4.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |

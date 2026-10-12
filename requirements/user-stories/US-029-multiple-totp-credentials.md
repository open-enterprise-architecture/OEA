# US-029: Mehrere TOTP-Authenticatoren registrieren

**ID**: US-029
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Person möchte ich mehrere TOTP-Authenticatoren auf verschiedenen Geräten registrieren können, damit ich bei Geräteverlust einen Backup-Authenticator habe und mich von jedem meiner Geräte aus mit TOTP anmelden kann.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-030: Mehrere aktive TOTP-Credentials pro Person](../req/REQ-030-mehrere-totp-credentials.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt hat bereits ein TOTP-Credential registriert
- Wenn: er in den Profil-Einstellungen ein weiteres TOTP-Credential einrichtet (UC-03 A3)
- Dann: hat er danach zwei aktive TOTP-Credentials; das erste bleibt unverändert

**AC2**:
- Gegeben: Kurt hat zwei aktive TOTP-Credentials
- Wenn: er sich einloggt und den Code seines zweiten Authenticators eingibt
- Dann: ist der Login erfolgreich

**AC3**:
- Gegeben: Kurt richtet ein neues TOTP ein
- Wenn: er dem Credential einen Label-Namen gibt (z.B. "Backup-YubiKey")
- Dann: erscheint dieses Label in der Übersicht seiner Sicherheitseinstellungen

**AC4**:
- Gegeben: Kurt richtet ein TOTP ohne Label ein
- Wenn: das Credential gespeichert wird
- Dann: erhält es ein automatisch vergebenes Default-Label

## Technische Hinweise

- Betroffene Komponenten: Auth-Modul (Login-Verifikation gegen mehrere TOTP-Credentials), UC-03 Enrollment-Flow (kein Auto-Revoke mehr), Profil-Sicherheitseinstellungen
- Betroffene EntityTypes/Relations: `local_credentials` (kein `UNIQUE(person_id, type)` mehr für TOTP; neues Feld `label`)
- Datenbank-Änderungen: `label`-Spalte zu `local_credentials` hinzufügen; UNIQUE-Constraint für `(person_id, type=totp)` entfernen

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Multi-TOTP-Login, Enrollment ohne Auto-Revoke, Label-Default)
- [ ] Dokumentation aktualisiert
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-021 (TOTP-Enrollment-Grundlage), US-025 (Weitere Methode über Profil-Einstellungen)
- Blockiert: keine

## Notizen

Spiegelt das bestehende Passkey-Modell (mehrere Passkeys pro Person seit Beginn erlaubt). Kein UI-Select "welcher Authenticator" beim Login nötig: der Code identifiziert das Credential implizit. Label-Freitextfeld beim Enrollment ("Wie möchtest du dieses Gerät nennen?"). Betrifft auch `local-credential.md` BR-02 (aufgehoben durch REQ-030).

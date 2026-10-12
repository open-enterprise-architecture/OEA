---
id: REQ-030
title: Mehrere aktive TOTP-Credentials pro Person
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - local-credential
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

# REQ-030: Mehrere aktive TOTP-Credentials pro Person

## Aussage

Das System MUSS es einer Person erlauben, gleichzeitig mehrere aktive TOTP-Credentials (`LocalCredential.type=totp`) zu hinterlegen; beim Login (REQ-010) MUSS ein gültiger TOTP-Code aus irgendeinem der aktiven TOTP-Credentials der Person akzeptiert werden; jedes TOTP-Credential MUSS ein nutzer-vergebbares Label tragen, damit die Person ihre Credentials voneinander unterscheiden kann.

## Begründung

Eine Person kann mehrere Geräte (Smartphone, Tablet, Hardware-Token) als TOTP-Authenticator nutzen wollen. Besonders bei Personen mit administrativen Rollen ist ein Backup-Authenticator für Recovery-Szenarien sinnvoll. Die bisherige Beschränkung auf ein aktives TOTP pro Person (aufgehoben durch dieses Requirement) spiegelt nicht die tatsächliche Praxis wider: dasselbe Secret auf mehrere Geräte zu replizieren (was Secrets-Sharing bedeutet) ist schlechter als separate Registrierungen mit eigenem Secret pro Gerät. Dieses Verhalten entspricht bereits dem Passkey-Modell im selben BO (mehrere Passkeys pro Person sind schon erlaubt, BR-02 war für TOTP restriktiver).

## Kontext

Gilt für `LocalCredential.type=totp`. Passwort-Credentials (`type=password`) bleiben auf maximal eines pro Person beschränkt (BR-03 in `local-credential.md`). Das Requirement hebt die bisherige BR-02 ("maximal ein aktives TOTP pro Person") auf und ersetzt sie durch: "mehrere aktive TOTP-Credentials pro Person zulässig; jedes mit eigenem Secret und Label". Ein neues TOTP invalidiert vorhandene NICHT mehr automatisch (bisheriges Verhalten gestrichen). Die Person verwaltet ihre TOTP-Credentials selbst (Löschen über Profil-Einstellungen, separater UC).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Label (nutzer-vergeben, optional; max. 255 Zeichen, z.B. "iPhone – Google Authenticator", "YubiKey TOTP")
- TOTP-Enrollment-Ablauf wie in REQ-022 und UC-03 definiert

**Verarbeitung beim Enrollment**:
- Neues TOTP-Credential wird angelegt und zur Person verknüpft; vorhandene TOTP-Credentials bleiben aktiv
- Kein implizites Revoken des bisherigen TOTP; nur der Nutzer kann Credentials explizit löschen

**Verarbeitung beim Login (REQ-010)**:
- System lädt alle aktiven TOTP-Credentials der Person
- Verifiziert den eingegebenen Code gegen jedes Credential sequenziell (Reihenfolge: zuletzt genutzt zuerst, für Performance)
- Sobald ein Credential den Code verifiziert: Login erfolgreich; `lastUsedAt` dieses Credentials aktualisieren
- Kein "welches Credential"-Feld für den Nutzer beim Login nötig – der Code ist der einzige Input

**Fehlerfälle**:
- Label leer → zulässig; System vergibt Default-Label (z.B. "TOTP-Authenticator 2026-06-25")
- Label länger als 255 Zeichen → Validierungsfehler beim Enrollment
- Kein aktives TOTP-Credential → Login-Verhalten gemäß REQ-010 (kein zweiter Faktor verfügbar)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Person hat zwei aktive TOTP-Credentials (unterschiedliche Secrets, unterschiedliche Geräte)
- Wenn: ein gültiger TOTP-Code aus dem zweiten Credential eingegeben wird
- Dann: ist der Login erfolgreich; `lastUsedAt` des zweiten Credentials wird aktualisiert

**AC2**:
- Gegeben: Person hat ein bestehendes aktives TOTP-Credential
- Wenn: sie über UC-03 A3 ein weiteres TOTP-Credential einrichtet
- Dann: hat sie danach zwei aktive TOTP-Credentials; das erste ist nicht revoked

**AC3**:
- Gegeben: zwei aktive TOTP-Credentials
- Wenn: die Person ihre Sicherheitseinstellungen aufruft
- Dann: werden beide Credentials mit ihrem Label und `createdAt`/`lastUsedAt` angezeigt

**AC4**:
- Gegeben: Person richtet ein TOTP ohne Label ein
- Wenn: das Credential angelegt wird
- Dann: erhält es ein sinnvolles Default-Label (z.B. "TOTP-Authenticator" + Datum)

**AC5**:
- Gegeben: zwei aktive TOTP-Credentials
- Wenn: ein ungültiger Code (keinem der Credentials zuordenbar) eingegeben wird
- Dann: schlägt der Login fehl; keine Information darüber, welches Credential nicht getroffen wurde (kein Leak)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Person mit 2 TOTP-Credentials anlegen; Login mit Code aus jedem Credential testen; Enrollment-Test (kein Auto-Revoke); Default-Label-Test
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-022 (TOTP-Enrollment), REQ-010 (Login-Flow mit TOTP)
- **Folgewirkungen**: `local-credential.md` BR-02 muss aktualisiert werden (Einschränkung aufgehoben); `label`-Attribut wird zu TOTP-Typ-spezifischen Attributen hinzugefügt
- **Konflikte**: bisher BR-02 in `local-credential.md` – wird durch dieses REQ aufgehoben

## Risiken bei Nichterfüllung

- Risiko 1: Ohne mehrere TOTP-Credentials sind Personen gezwungen, ein Secret auf mehrere Geräte zu kopieren (Secrets-Sharing) – schlechter als separate Credentials; moderater Schweregrad
- Risiko 2: Ohne Backup-TOTP riskieren Nutzer Lock-out bei Geräteverlust – höhere Support-Last für Betreiber

## Trade-offs

- Mehrere TOTP-Credentials erhöhen die Angriffsfläche minimal (jedes Credential ist ein potentieller Angriffspunkt). Mitigiert durch: separates Secret pro Credential (kein Secrets-Sharing), `lastUsedAt`-Tracking, Audit-Log bei Enrollment/Nutzung.
- Performance-Overhead bei Login: sequentielle Verifikation aller TOTP-Credentials. Bei vernünftigen Werten (max. 5–10 Credentials) negligierbar; zuletzt-genutzt-zuerst-Sortierung mitigiert den Overhead weiter.

## Realisierungs-Hinweise

- DB-Query beim Login: `SELECT * FROM local_credentials WHERE person_id = ? AND type = 'totp' AND status = 'active' ORDER BY last_used_at DESC NULLS LAST`
- Verifikationsschleife bricht bei erstem Treffer ab
- Label-Input: Freitextfeld im Enrollment-UI ("Wie möchtest du dieses Gerät nennen?"); optional mit Vorschlägen (iOS, Android, Hardware-Token)
- Kein UI-Select "welches Credential beim Login" – der TOTP-Code ist selbst die Unterscheidung; multi-Credential-Lookup ist serverseitig transparent

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus Betreiber-Anforderung (SH-06): "mehrere 2. Faktoren vom gleichen Typ hinterlegen". Insbesondere für Instanzen mit mehreren Administratoren sinnvoll: jeder Admin kann seinen eigenen Backup-TOTP-Authenticator einrichten, ohne andere Credentials zu beeinflussen. Hebt BR-02 in `local-credential.md` auf.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft; hebt BR-02 in local-credential.md auf |

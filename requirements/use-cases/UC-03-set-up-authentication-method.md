---
id: UC-03
title: Authentifizierungsmethode einrichten (Required Action beim ersten Login)
status: draft
priority: must
target_release: walking-skeleton
complexity: medium
version: 0.2.0
created: 2026-06-25
last_updated: 2026-06-25
primary_actor: SH-03
secondary_actors:
  - SH-06
references:
  business_objects:
    - person
    - local-credential
  concept:
    - concept/70-platform/21-api-architektur.md
    - concept/20-entities/08-organisation-rollen-personen.md
  related_use_cases:
    - UC-01
    - UC-02
---

# UC-03: Authentifizierungsmethode einrichten (Required Action beim ersten Login)

## Diagramm

```plantuml
@startuml UC-03-AuthMethode
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Lead EA)" as Kurt
actor "Max\n(Operator)" as Max

rectangle "OEA – Authentifizierungsmethode einrichten" {
  usecase "TOTP einrichten\n(Required Action)" as UC_TOTP
  usecase "Passkey einrichten\n(Required Action)" as UC_Passkey
  usecase "Weitere Methode\nhinzufügen (eingeloggt)" as UC_Extra
  usecase "2FA-Erzwingung\nkonfigurieren" as UC_2FA
  usecase "Credential persistieren\n& Required Action löschen" as UC_Cred
  usecase "Audit-Log schreiben" as UC_Audit
}

Kurt --> UC_TOTP
Kurt --> UC_Passkey
Kurt --> UC_Extra
Max --> UC_2FA

UC_TOTP ..> UC_Cred : <<include>>
UC_Passkey ..> UC_Cred : <<include>>
UC_Extra ..> UC_Cred : <<include>>
UC_Cred ..> UC_Audit : <<include>>
@enduml
```

## Goal in Context

Instanzen ohne externen Identity-Provider nutzen lokale Authentifizierung (Username/Passwort, optional mit 2. Faktor). Der Administrator legt eine Person an und setzt ein initiales Passwort. Beim ersten Login erkennt das System, welche „Required Actions" noch ausstehen: Ist 2FA instanzweit erzwungen (REQ-020) und noch kein zweiter Faktor eingerichtet, wird die Person direkt zur Einrichtungsseite weitergeleitet – sie erhält keinen Zugriff auf die Applikation, bis die Required Action abgeschlossen ist. Ist 2FA nicht erzwungen, entfällt UC-03 vollständig; der Login (UC-01 A5) gewährt direkt Zugriff.

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Weitere Beteiligte**: [Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md) – Max konfiguriert, ob 2FA erzwungen wird und welche lokalen Methoden verfügbar sind

**Story**: Als Person, die sich zum ersten Mal mit ihrem initialen Passwort anmeldet, möchte ich im Rahmen des Logins einen zweiten Faktor einrichten, damit ich danach vollen Zugriff gemäß meiner Rolle erhalte.

## Trigger

- Systeminterner Anlass: UC-01 erkennt nach erfolgreicher Passwort-Prüfung, dass eine Required Action aussteht (2FA erzwungen, aber noch kein `LocalCredential` vom Typ `totp` oder `passkey` vorhanden)
- Zeitpunkt: beim ersten Login nach Personen-Anlage durch Administrator; oder nach einem Admin-Reset der Required Actions
- Vorgänger-Use-Case: UC-01 (Login, Schritt 1–2 des Hauptablaufs A5 sind bereits ausgeführt)

## Vorbedingungen (Pre-Conditions)

- [ ] Eine [Person](../../business-objects/person.md) mit Lifecycle-Status `invited` existiert im System
- [ ] Der Administrator hat ein initiales Passwort für die Person gesetzt (REQ-024); die Person kennt dieses Passwort
- [ ] Die OEA-Instanz hat 2FA-Erzwingung aktiviert (REQ-020) und mindestens eine 2FA-Methode freigeschaltet (TOTP oder Passkey)
- [ ] Die Person hat noch kein aktives [LocalCredential](../../business-objects/local-credential.md) vom Typ `totp` oder `passkey`

## Nachbedingungen (Post-Conditions)

### Bei Erfolg

- Ein neues [LocalCredential](../../business-objects/local-credential.md) (TOTP-Secret oder Passkey-Public-Key) ist persistiert und mit der Person verknüpft
- Die Required-Action-Flag "2FA-Einrichtung" ist für die Person gelöscht
- Audit-Log enthält einen Eintrag: Person-ID, Zeitpunkt, eingerichtete Methode
- System gewährt Kurt nun vollständigen Zugriff (Session mit Berechtigungen gemäß Rollen)
- Lifecycle-Status der Person wechselt von `invited` zu `active` (erster erfolgreicher Login-Abschluss)

### Bei Misserfolg

- Kein Credential wird persistiert
- Required-Action-Flag bleibt gesetzt
- Kurt erhält keine Session; er kann es erneut versuchen oder den Browser schließen

## Hauptablauf (Basic Flow)

*Standardfall: TOTP-Einrichtung als Required Action, ausgelöst durch ersten Login*

1. **Kurt**: gibt Username und initiales Passwort in die Login-Maske ein (UC-01 A5, Schritte 1–2)
2. **System**: validiert das Passwort; stellt fest, dass Required Action "2FA-Einrichtung" aussteht; leitet Kurt auf die Einrichtungsseite weiter – ohne Session und ohne Zugriff auf die Applikation
3. **Kurt**: wählt TOTP als Methode (oder System zeigt direkt TOTP, falls es die einzige verfügbare Methode ist)
4. **System**: generiert ein neues TOTP-Secret, zeigt es als QR-Code (`otpauth://`-URI-Format) und im Klartext zur manuellen Eingabe an
5. **Kurt**: scannt den QR-Code mit einer Authenticator-App und gibt den aktuell angezeigten 6-stelligen Code zur Verifikation ein
6. **System**: verifiziert den Code gegen das Secret (Zeittoleranz ±1 Fenster à 30 s); bei Erfolg persistiert es das Secret verschlüsselt, löscht die Required-Action-Flag, schreibt Audit-Log-Eintrag
7. **System**: erstellt die Session mit Kurts Berechtigungen, leitet ihn in den rollenbasierten Einstiegsbereich weiter; Lifecycle-Übergang `invited → active`

## Alternative Abläufe (Alternative Flows)

**A1 – Passkey als Required Action**: Bei Schritt 3, wenn Kurt Passkey wählt (oder die Instanz nur Passkey aktiviert hat):
1. System startet WebAuthn-Registrierungs-Challenge
2. Kurt bestätigt mit kompatiblem Authenticator
3. System verifiziert Registrierungsantwort, persistiert Public Key und Credential-ID
- Mündet zurück in Schritt 6 (Required-Action-Flag löschen, Session erstellen)

**A2 – 2FA nicht erzwungen**: Wenn REQ-020 inaktiv ist:
- UC-03 wird nicht ausgelöst; UC-01 A5 gewährt nach Passwort-Prüfung direkt Zugriff
- Kurt richtet optional über seine Profil-Einstellungen eine weitere Methode ein (A3)

**A3 – Weitere Methode für bereits eingeloggte Person**: Wenn Kurt bereits authentifiziert ist und eine zusätzliche lokale Methode einrichten möchte:
1. Kurt navigiert zu den Sicherheitseinstellungen seines Profils
2. System zeigt verfügbare, noch nicht eingerichtete Methoden
3. Methodenspezifischer Ablauf wie Hauptablauf (TOTP) oder A1 (Passkey); kein Required-Action-Kontext
4. System persistiert die neue Methode; Session bleibt aktiv

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – TOTP-Verifikation schlägt fehl**:
- Bedingung: Kurt gibt einen falschen oder abgelaufenen Code ein (Schritt 5)
- Erwartete Reaktion: System zeigt Fehlermeldung, kein Secret wird persistiert; Required-Action-Flag bleibt gesetzt
- Wiederaufnahme: Kurt gibt den nächsten Code ein; die Einrichtungsseite bleibt geöffnet

**E2 – Passkey-Registrierung schlägt fehl**:
- Bedingung: Kurt bricht die Authenticator-Bestätigung ab oder der Authenticator meldet einen Fehler
- Erwartete Reaktion: System zeigt allgemeine Fehlermeldung, kein Credential persistiert; Required-Action-Flag bleibt gesetzt
- Wiederaufnahme: Kurt kann es erneut versuchen oder auf TOTP wechseln (falls verfügbar)

**E3 – Kurt bricht die Required Action ab**:
- Bedingung: Kurt schließt den Browser oder bricht die Einrichtung explizit ab
- Erwartete Reaktion: keine Session, kein Credential; beim nächsten Login-Versuch beginnt UC-03 von vorn
- Wiederaufnahme: Kurt meldet sich erneut an

## Datenfluss

| Schritt | Daten | Richtung | Bemerkung |
|---|---|---|---|
| 1–2 | Username, Passwort-Klartext | Kurt → System | Passwort nur für Verifikation, nie persistiert |
| 2 | Required-Action-Status | System intern | Flag-Abfrage nach Passwort-Prüfung |
| 4 (TOTP) | TOTP-Secret, QR-Code-URI | System → Kurt | Secret nur einmalig anzeigen |
| 5 (TOTP) | Verifikations-Code (6 Stellen) | Kurt → System | |
| A1 (Passkey) | WebAuthn-Challenge, RP-ID, Nutzer-Handle | System → Kurt | |
| A1 (Passkey) | Public Key, Credential-ID, Attestation | Kurt → System | Privater Schlüssel verlässt den Authenticator nie |
| 6 | Credential-Persistierung, Flag-Löschung, Audit-Log | System intern | Credentials getrennt vom Person-Objekt speichern |
| 7 | Session-Token | System → Kurt | |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [person](../../business-objects/person.md) | read, update | Lifecycle-Übergang `invited → active` bei Abschluss; Required-Action-Flag lesen/löschen |
| [local-credential](../../business-objects/local-credential.md) | create | TOTP-Secret (verschlüsselt) oder Passkey-Public-Key wird angelegt und mit der Person verknüpft |

## Akzeptanzkriterien

- [ ] Hauptablauf (TOTP als Required Action beim ersten Login) vollständig durchlaufbar
- [ ] A1 (Passkey als Required Action) funktioniert; Passkey-Login (UC-01 A3) ist danach möglich
- [ ] A2 (2FA nicht erzwungen): UC-03 wird nicht ausgelöst; Login (UC-01 A5) gewährt direkt Zugriff
- [ ] A3 (Weitere Methode in Profil-Einstellungen) funktioniert ohne Required-Action-Kontext
- [ ] Ohne abgeschlossene Required Action erhält Kurt keine Session (kein Partial-Access)
- [ ] E1 (TOTP-Fehler): Fehlermeldung, kein Credential, Required-Action-Flag bleibt gesetzt
- [ ] E2 (Passkey-Fehler): Fehlermeldung, kein Credential, Required-Action-Flag bleibt gesetzt
- [ ] Audit-Log enthält nach Abschluss: Person-ID, Zeitpunkt, eingerichtete Methode (`totp` oder `passkey`)
- [ ] TOTP-Secret erscheint nicht in Server-Logs
- [ ] Nach abgeschlossener Required Action ist der Login mit der neuen Methode (UC-01 A3/A4) möglich

## Nicht im Scope

- Enrollment via separatem Einladungslink (bewusst verworfen; siehe REQ-021 `rejected`)
- Anlegen der Person und Setzen des initialen Passworts durch Administrator (eigener UC; REQ-024 modelliert das Passwort-Hashing)
- Passwort-Reset für lokal verwaltete Accounts (separater Use Case)
- Verwaltung (Anzeige, Löschen) bereits registrierter Methoden (separater Use Case)
- Recovery-Codes bei vollständigem Methodenverlust (separater Use Case / Break-Glass-Verfahren)
- Enrollment für den System-Admin-Account (UC-02)
- OIDC-Account-Verknüpfung (Person hat bereits `externalReference`; kein Enrollment nötig)

## Konzept-Bezüge

- [§21.8 Authentifizierung/Autorisierung – Lokale Authentifizierung](../../concept/70-platform/21-api-architektur.md)
- [§8.3 Person](../../concept/20-entities/08-organisation-rollen-personen.md)

## Realisierungs-Hinweise

- Required-Action-Mechanismus: nach erfolgreicher Passwort-Prüfung (UC-01 A5) prüft das System, ob offene Required Actions vorliegen; Session wird erst nach deren Abschluss erstellt (kein Early-Session-Grant)
- TOTP: RFC 6238, 30-Sekunden-Fenster, SHA-1; Secret AES-256-GCM-verschlüsselt persistieren
- Passkey: WebAuthn Level 2, `attestation: none` als Default; discoverable credentials bevorzugen
- EntityTypes: `Person` (Required-Action-Flag), `LocalCredential` (Ergebnis)
- A3 (Profil-Einstellungen): CSRF-Schutz via SameSite=Strict-Cookie; dieselbe Enrollment-Logik wie Hauptablauf, nur anderer Auth-Gate

## Realisierende Bestandteile

- Requirements: [REQ-022](../req/REQ-022-totp-enrollment.md), [REQ-023](../req/REQ-023-passkey-enrollment.md), [REQ-024](../req/REQ-024-initiales-passwort-admin.md), [REQ-025](../req/REQ-025-audit-log-enrollment.md), [REQ-026](../req/REQ-026-weitere-methode-authentifizierte-person.md)
- User Stories: [US-021](../user-stories/US-021-totp-enrollment.md), [US-022](../user-stories/US-022-passkey-enrollment.md), [US-023](../user-stories/US-023-initiales-passwort-admin.md), [US-024](../user-stories/US-024-audit-log-enrollment.md), [US-025](../user-stories/US-025-weitere-methode-eingeloggte-person.md)
- Verworfen: [REQ-021](../req/REQ-021-enrollment-token-validierung.md) (`rejected`), [US-020](../user-stories/US-020-enrollment-einstieg-einladungslink.md) (`rejected`)
- ADRs: [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md) (lokale Auth als Option)
- Test Cases: noch keine
- Implementation: noch keine

## Offene Fragen

- [x] Welches Business Object modelliert die lokalen Credentials? → [local-credential](../../business-objects/local-credential.md)
- [x] Soll ein Nutzer mehrere Passkeys registrieren können? → Ja (BR-02 in `local-credential.md`)
- [x] Enrollment via Einladungslink oder beim ersten Login? → First-Login Required Action (2026-06-25); Einladungslink-Ansatz verworfen
- [ ] Wird für die Minimal-Passkey-Einrichtung im Walking Skeleton nur discoverable credentials unterstützt, oder auch non-resident?
- [ ] Required-Action-Flag: im Person-Objekt als zusätzliches Attribut (`requiredActions: [totp_setup]`) oder als separate Tabelle?

## Notizen

v0.2.0: Architektonische Grundentscheidung revidiert. Der ursprüngliche Enrollment-Token-Ansatz (v0.1.0) wurde verworfen, weil er einen separaten Link-Zustellungsmechanismus (E-Mail o.ä.) vorausgesetzt hätte. Das Just-in-Time-Enrollment als Required Action beim ersten Login ist der Industriestandard (Keycloak, Authentik, AWS IAM) und kommt ohne zusätzliche Infrastruktur aus. Der OIDC-Pfad (UC-01 Hauptablauf) ist davon unberührt; UC-03 betrifft ausschließlich die lokale Authentifizierung.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | Requirements Engineer | Initial draft (Enrollment via Einladungslink) |
| 0.2.0 | 2026-06-25 | Requirements Engineer | Architektur-Revision: Enrollment-Token-Ansatz verworfen; Required-Action-beim-ersten-Login als neuer Hauptablauf |

---
id: REQ-024
title: Initiales Passwort durch Administrator setzen
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - person
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-024: Initiales Passwort durch Administrator setzen

## Aussage

Das System MUSS einem Administrator ermöglichen, beim Anlegen einer Person für lokale Authentifizierung ein initiales Passwort zu setzen; dieses Passwort MUSS sofort nach dem Setzen als sicherer Hash (argon2id oder bcrypt) persistiert werden und MUSS dem Administrator genau einmal im Klartext angezeigt werden, danach nicht mehr abrufbar sein.

## Begründung

Lokale Authentifizierung ohne externen IdP benötigt einen Startpunkt: Bevor eine Person sich einloggen kann, muss sie Credentials besitzen. Das initiale Passwort überbrückt diese Lücke – es ist der Startpunkt für den ersten Login (UC-01 A5), nach dem UC-03 (Required Action) den 2. Faktor einrichtet. Der Administrator setzt das Passwort einmalig, gibt es an die Person weiter (beliebiger Kanal), und die Person ändert es beim ersten Login (REQ-024b – Passwort-Änderung beim ersten Login, ggf. erzwungen). Adressiert SH-06s Anforderung nach lokaler Nutzerverwaltung ohne E-Mail-Infrastruktur.

## Kontext

Das initiale Passwort ist ein temporäres Credential: Es reicht aus, um UC-01 A5 (Passwort-Login) auszuführen und UC-03 (Required Action) auszulösen. Ein Passwort-Wechsel beim ersten Login ist empfohlen, aber als eigenständige Required Action separat zu modellieren (hier nicht entschieden). Kein Enrollment-Token oder Einladungslink wird benötigt.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Generiertes oder manuell eingegebenes initiales Passwort durch den Administrator (Admin-UI oder CLI)

**Verarbeitung**:
- System prüft das Passwort gegen konfigurierbare Mindestanforderungen (Mindestlänge; optionale Komplexitätsregeln)
- System erzeugt Hash (argon2id bevorzugt; bcrypt als Fallback)
- Hash wird als `LocalCredential` mit `type=password` persistiert und mit der Person verknüpft
- Passwort-Klartext wird einmalig in der Admin-UI / CLI-Output angezeigt, danach nicht mehr abrufbar
- Optional: System kann ein kryptographisch starkes Passwort automatisch generieren (Empfehlung)

**Ausgaben**:
- Passwort-Klartext: einmalige Anzeige im Admin-UI oder CLI-Stdout
- Bestätigung: "Initiales Passwort gesetzt. Bitte sicher an die Person weitergeben."
- Bei Fehler: Fehlermeldung mit nicht erfüllter Anforderung

**Fehlerfälle**:
- Passwort erfüllt Mindestanforderungen nicht → Fehlermeldung mit konkreten Anforderungen, kein Hash gespeichert
- Person existiert nicht oder hat bereits ein aktives Password-Credential → Fehlermeldung; bestehende Credentials unverändert

## Akzeptanzkriterien

**AC1**:
- Gegeben: Administrator legt eine Person an und setzt ein initiales Passwort
- Wenn: die Anfrage erfolgreich verarbeitet wird
- Dann: ist der Passwort-Hash als `LocalCredential (type=password)` persistiert; der Klartext wird genau einmal angezeigt

**AC2**:
- Gegeben: das initiale Passwort wurde gesetzt und angezeigt
- Wenn: der Administrator die Admin-UI erneut öffnet oder die API erneut aufruft
- Dann: ist der Passwort-Klartext nicht mehr abrufbar (nur Hash in der DB)

**AC3**:
- Gegeben: das initiale Passwort ist gesetzt
- Wenn: die Person sich mit diesem Passwort anmeldet (UC-01 A5)
- Dann: gelingt der Login und UC-03 (Required Action) wird ausgelöst, falls 2FA erzwungen ist

**AC4**:
- Gegeben: das System verarbeitet die Passwort-Anfrage
- Wenn: Server-Logs geschrieben werden
- Dann: ist der Passwort-Klartext nicht in den Logs enthalten

**AC5**:
- Gegeben: System generiert automatisch ein Passwort
- Wenn: es generiert wird
- Dann: hat es mindestens 16 Zeichen und ausreichend Entropie (Groß/Klein/Ziffern/Sonderzeichen)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Passwort setzen, Hash-Format prüfen, Klartext-Abruf versuchen, Log-Scan
- [x] Mess-Werkzeug: Test-Suite des Admin-Moduls; DB-Inspektion im Test
- [x] Bestanden-Kriterium: AC1–AC5 grün; Login mit gesetztem Passwort (AC3) funktioniert via REQ-011
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: Person muss im System angelegt sein (kein eigener UC bisher); REQ-028 (Passwort-Richtlinien müssen konfiguriert sein, damit Validierung greift)
- **Folgewirkungen**: REQ-011 (Passwort-Login); UC-03 Required Action (setzt initiales Passwort voraus)
- **Empfohlen**: REQ-027 (Passwort-Generator erzeugt automatisch ein richtlinienkonformes Passwort)
- **Folge-Requirement**: REQ-031 (Passwort-Reset für bestehende Nutzer – gleiches Muster, aber mit Revoke des alten Credentials und erzwungenem Passwort-Wechsel)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne initiales Passwort kein erster Login für lokale Nutzer – vollständiger Blockierung des Onboardings, schwerwiegend
- Risiko 2: Klartext in Logs → Credentials-Leak, schwerwiegend (durch AC4 mitigiert)

## Trade-offs

- Kein E-Mail-Versand durch OEA: Der Administrator ist für die sichere Weitergabe des initialen Passworts verantwortlich (persönlich, Messenger, eigenes Mail-System). OEA bleibt infrastrukturunabhängig.

## Realisierungs-Hinweise

- Automatisch generierte Passwörter bevorzugen (Admin muss kein eigenes erfinden); kryptographisch sicher (CSPRNG)
- Hashing sofort beim Setzen: Klartext nur im Arbeitsspeicher, nach Hashing verwerfen; nie in Request-Logs
- Admin-UI: Passwort in einem einmaligen Popup oder einer abgesicherten Seite anzeigen (nicht im regulären Seiteninhalt, der gecacht werden könnte)
- CLI-Alternative: `oea user create --name "Kurt" --generate-password` → gibt Passwort auf Stdout aus; Admin kopiert es

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

v0.2.0: Vollständig neu geschrieben. v0.1.0 (Passwort-Enrollment durch den User selbst via Enrollment-Token) wurde verworfen, da der Token-Ansatz (REQ-021 `rejected`) durch Just-in-Time-Enrollment im Login-Flow ersetzt wurde. Das initiale Passwort wird nun durch den Administrator gesetzt und einmalig angezeigt – wie der Setup-Token in UC-02, aber für reguläre Personen.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft: Passwort-Enrollment durch User via Enrollment-Token |
| 0.2.0 | 2026-06-25 | requirements-engineer | Vollständige Revision: Admin setzt initiales Passwort; Token-Ansatz verworfen |

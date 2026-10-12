---
id: REQ-026
title: Weitere Authentifizierungsmethode für eingeloggte Person einrichten
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

# REQ-026: Weitere Authentifizierungsmethode für eingeloggte Person einrichten

## Aussage

Das System SOLL einer bereits authentifizierten Person ermöglichen, über die eigenen Sicherheitseinstellungen eine weitere lokale Authentifizierungsmethode einzurichten, ohne dafür ein Enrollment-Token zu benötigen; die Session MUSS dabei als Authentifizierungsnachweis dienen.

## Begründung

Nicht jeder Enrollment-Bedarf entsteht beim ersten Zugang (via Einladungslink). Eine bereits aktive Person (via OIDC oder bestehender lokaler Methode eingeloggt) muss nachträglich eine lokale Methode hinzufügen können – z.B. einen zweiten Passkey für ein neues Gerät oder TOTP als Fallback wenn der primäre Passkey verloren geht. UC-03 Alternative A3. Ohne diese Fähigkeit wäre Recovery bei Geräteverlust nur über Administrative Eingriffe möglich.

## Kontext

Setzt eine gültige Session voraus (UC-01 Hauptablauf oder A1–A5 erfolgreich). Kein Enrollment-Token erforderlich; stattdessen CSRF-Schutz der Session. Die methodenspezifische Durchführung (TOTP, Passkey, Passwort) entspricht REQ-022, REQ-023 bzw. REQ-024, jedoch ohne Token-Validierungsschritt am Anfang.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Authentifizierte Session (Cookie oder Bearer-Token)
- Ausgewählte neue Methode und Methoden-spezifische Eingaben (identisch zu REQ-022/023/024)

**Verarbeitung**:
- System prüft, dass eine gültige Session für die anfragende Person vorliegt
- System zeigt die für die Instanz verfügbaren, noch nicht eingerichteten Methoden
- Methodenspezifischer Enrollment-Ablauf gemäß REQ-022, REQ-023 oder REQ-024 (identisch, ohne Token-Schritt)
- CSRF-Schutz: Session-gebundenes Token oder SameSite-Cookie-Richtlinie

**Ausgaben**:
- Liste verfügbarer, noch nicht eingerichteter Methoden
- Erfolgsmeldung nach abgeschlossenem Enrollment
- Fehlermeldung bei ungültiger Session oder nicht verfügbarer Methode

**Fehlerfälle**:
- Session abgelaufen oder ungültig → Weiterleitung zum Login (UC-01)
- Alle Methoden bereits eingerichtet → informativer Hinweis, kein Fehler
- Methode von Instanz nicht aktiviert → Methode wird nicht angeboten

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt ist eingeloggt und hat noch keine TOTP-Methode eingerichtet
- Wenn: er in den Sicherheitseinstellungen TOTP hinzufügt und die Einrichtung abschließt
- Dann: ist TOTP als zusätzliche Methode persistiert; kein Enrollment-Token wurde benötigt

**AC2**:
- Gegeben: Kurt ist eingeloggt und möchte einen zweiten Passkey hinzufügen
- Wenn: er die Passkey-Registrierung durchführt
- Dann: ist der neue Public Key persistiert; beide Passkeys sind für den Login verwendbar

**AC3**:
- Gegeben: Kurt hat bereits Passkey, TOTP und Passwort eingerichtet
- Wenn: er die Sicherheitseinstellungen öffnet
- Dann: zeigt das System an, dass alle Methoden bereits eingerichtet sind (kein Fehler)

**AC4**:
- Gegeben: eine Anfrage an den Enrollment-Endpunkt ohne gültige Session
- Wenn: die Anfrage eingeht
- Dann: wird sie abgelehnt; Weiterleitung zum Login

**AC5**:
- Gegeben: eine Anfrage ohne CSRF-Token (bei POST-Formular-basiertem Flow)
- Wenn: die Anfrage eingeht
- Dann: wird sie mit einem entsprechenden Fehler abgelehnt

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: eingeloggte Person fügt Methoden hinzu; unauthentifizierter Zugriff auf Endpunkt testen
- [x] Mess-Werkzeug: Test-Suite des Enrollment-Moduls
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: UC-01 (Login muss funktionieren); REQ-022, REQ-023 oder REQ-024 (methodenspezifisches Enrollment)
- **Folgewirkungen**: Recovery bei Geräteverlust (erleichtert, aber kein vollständiger Recovery-UC)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne diese Funktion können Personen nach Geräteverlust keine neuen Credentials einrichten; erhöhter Admin-Aufwand für Token-Neuausstellung – mittlerer Schweregrad

## Trade-offs

- vs. Sicherheit: Enrollment ohne Enrollment-Token erhöht die Angriffsfläche minimal (Session muss kompromittiert sein); mitigiert durch CSRF-Schutz und Session-Validierung

## Realisierungs-Hinweise

- Profil/Sicherheitseinstellungen-Seite: authentifizierter Bereich, gleicher Aufbau wie Enrollment-Seite, aber ohne Token-URL
- CSRF: SameSite=Strict auf Session-Cookie ist ausreichend für Browser-Schutz; zusätzliches CSRF-Token bei Formularen ist defense-in-depth
- Endpunkt sollte derselbe Enrollment-Code-Pfad sein wie Token-basiertes Enrollment (nur der Authentifizierungs-Gate-Schritt unterscheidet sich), um Duplikation zu vermeiden

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-03 Alternative A3. Priorisierung als `should` statt `must`, weil der primäre Enrollment-Weg (Einladungstoken) ausreicht; A3 ist eine wesentliche Verbesserung der Nutzererfahrung und des Recovery-Szenarios, aber keine harte Anforderung für den initialen Betrieb.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft aus UC-03 |

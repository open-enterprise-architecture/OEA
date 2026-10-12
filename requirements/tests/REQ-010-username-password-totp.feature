# Ableitung aus: requirements/req/req-010-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-01
Feature: REQ-010 – Username/Passwort-Login mit TOTP als zweitem Faktor

  Das System SOLL Personen die Anmeldung mit Username/Passwort und
  einem TOTP-Code als zweitem Faktor ermöglichen, wenn die OEA-Instanz
  lokale Authentifizierung ohne Passkey-Unterstützung anbietet.

  Background:
    Given eine OEA-Instanz mit konfiguriertem Authentifizierungsmechanismus
    And eine Person mit E-Mail "test@example.com" und aktiver Rolle existiert

  @AC1
  Scenario: AC1 – eine Person mit korrektem Passwort und gültigem TOTP-Code
    Given eine Person mit korrektem Passwort und gültigem TOTP-Code
    When  sie sich anmeldet
    Then  erhält sie eine Session gemäß ihren aktiven Rollenzuweisungen

  @AC2
  Scenario: AC2 – ein korrektes Passwort, aber ein falscher TOTP-Code
    Given ein korrektes Passwort, aber ein falscher TOTP-Code
    When  der Login-Versuch abgeschlossen wird
    Then  wird keine Session erstellt, und die Fehlermeldung lässt nicht erkennen, dass das Passwort korrekt war

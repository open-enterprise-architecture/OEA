# Ableitung aus: requirements/req/req-136-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-01 @UC-02
Feature: REQ-136 – Lokale Authentifizierung durch Betreiber konfigurierbar

  Der Betreiber **MUSS** pro OEA-Instanz konfigurieren können, ob die
  lokale Authentifizierung (Username/Passwort, Passkey, TOTP) als
  Anmeldeoption in der Benutzeroberfläche angeboten wird. OEA **DARF
  NICHT** eine Authentifizierungsmethode als empfohlen kennzeichnen
  oder vorauswählen — diese Entscheidung liegt ausschließlich beim
  Betreiber.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – `auth.localAuthEnabled = false`
    Given `auth.localAuthEnabled = false`
    When  Login-Screen geöffnet wird
    Then  Kein Username/Passwort-Formular, kein Passkey-Link, keine lokale Option sichtbar

  @AC2
  Scenario: AC2 – `auth.oidcEnabled = false`
    Given `auth.oidcEnabled = false`
    When  Login-Screen geöffnet wird
    Then  Kein SSO-Button sichtbar; lokale Optionen werden angezeigt (sofern aktiviert)

  @AC3
  Scenario: AC3 – `auth.localAuthEnabled = true`, `auth.oidcEnabled = true`
    Given `auth.localAuthEnabled = true`, `auth.oidcEnabled = true`
    When  Login-Screen geöffnet wird
    Then  Beide Optionen werden gleichwertig angezeigt; keine Option trägt das Label „empfohlen", „Standard" oder ähnliches

  @AC4
  Scenario: AC4 – `auth.localAuthEnabled = false`
    Given `auth.localAuthEnabled = false`
    When  Bootstrapping-Wizard geöffnet wird
    Then  Nur OIDC-Bootstrapping-Option angeboten; lokaler Modus nicht anzeigbar

  @AC5
  Scenario: AC5 – `auth.localAuthEnabled = false` und `auth.oidcEnabled = false`
    Given `auth.localAuthEnabled = false` und `auth.oidcEnabled = false`
    When  Login-Screen aufgerufen wird
    Then  Fehlerzustand mit klarer Meldung für den Betreiber; kein normaler Login möglich

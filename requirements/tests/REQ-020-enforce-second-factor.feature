# Ableitung aus: requirements/req/req-020-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + inspection (Default-Konfiguration)

@business-rule @should @UC-01
Feature: REQ-020 – Instanzweite Erzwingung eines zweiten Faktors für reguläre Logins

  Der Betreiber MUSS bei der Inbetriebnahme einer Instanz festlegen
  können, dass sich alle Personen ausschließlich mit einem zweiten
  Faktor anmelden dürfen (Passkey oder Username/Passwort mit TOTP,
  siehe REQ-009/REQ-010); ist diese Erzwingung aktiv, DARF die
  Minimal-Variante ohne zweiten Faktor (REQ-011) für reguläre Personen
  NICHT nutzbar sein. Diese Erzwingung gilt NICHT für den
  [System-Admin-Account](../../business-objects/system-admin-account.md)
  aus UC-02.

  @AC1
  Scenario: AC1 – eine Instanz, bei der "zweiter Faktor erzwingen" bei der Inbetriebnahme ...
    Given eine Instanz, bei der "zweiter Faktor erzwingen" bei der Inbetriebnahme aktiviert wurde
    When  versucht wird, REQ-011 (Username/Passwort ohne zweiten Faktor) für reguläre Personen zu aktivieren
    Then  wird dies abgelehnt, solange die Erzwingung aktiv ist

  @AC2
  Scenario: AC2 – dieselbe Instanz mit aktiver Erzwingung
    Given dieselbe Instanz mit aktiver Erzwingung
    When  der System-Admin-Account aus UC-02 sich anmeldet (lokal oder remote)
    Then  ist diese Anmeldung von der Erzwingung unberührt und funktioniert wie in UC-02 spezifiziert

  @AC3
  Scenario: AC3 – eine Instanz ohne aktivierte Erzwingung (Default)
    Given eine Instanz ohne aktivierte Erzwingung (Default)
    When  das Authentifizierungs-Setup geprüft wird
    Then  ist REQ-011 weiterhin gemäß seiner eigenen Bedingungen aktivierbar (kein Default-Zwang)

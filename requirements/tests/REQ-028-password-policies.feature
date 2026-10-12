# Ableitung aus: requirements/req/req-028-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-03
Feature: REQ-028 – Passwort-Richtlinien durch Betreiber konfigurieren

  Das System MUSS dem Betreiber ermöglichen, instanzweite
  Passwort-Richtlinien zu konfigurieren, die bei jeder Passwort-Setzung
  oder -Änderung durchgesetzt werden; die Richtlinien MÜSSEN mindestens
  folgende Regeln umfassen: Mindestlänge, Zeichentypen-Erzwingung
  (Großbuchstaben, Kleinbuchstaben, Ziffern, Sonderzeichen – einzeln
  aktivierbar), Ablaufdauer und Passwort-Historie.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Betreiber aktiviert `requireUppercase`, `requireLowercase`, `requireDigi...
    Given Betreiber aktiviert `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars` und setzt `minLength=16`
    When  eine Person ein Passwort setzt, das nur Kleinbuchstaben enthält
    Then  wird es abgelehnt mit einer Fehlermeldung, die alle nicht erfüllten Regeln nennt

  @AC2
  Scenario: AC2 – Betreiber setzt `expiryDays=90`
    Given Betreiber setzt `expiryDays=90`
    When  eine Person sich 91 Tage nach dem letzten Passwort-Wechsel einloggt
    Then  wird Required Action "Passwort ändern" ausgelöst; kein vollständiger Zugriff bis zum Wechsel

  @AC3
  Scenario: AC3 – Betreiber setzt `historyCount=5`
    Given Betreiber setzt `historyCount=5`
    When  eine Person versucht, ein Passwort zu setzen, das mit einem der letzten 5 Passwörter übereinstimmt
    Then  wird es abgelehnt mit Hinweis auf Wiederverwendungsverbot

  @AC4
  Scenario: AC4 – Betreiber setzt eine inkonsistente Konfiguration (`minLength=3`, alle 4 ...
    Given Betreiber setzt eine inkonsistente Konfiguration (`minLength=3`, alle 4 Zeichentypen erzwungen)
    When  die Konfiguration gespeichert werden soll
    Then  wird sie abgelehnt mit konkreter Fehlermeldung (Mindestlänge muss ≥ 4 sein)

  @AC5
  Scenario: AC5 – alle Regeln sind auf Default (false / 0)
    Given alle Regeln sind auf Default (false / 0)
    When  ein Passwort mit Mindestlänge gesetzt wird
    Then  wird es akzeptiert, auch wenn es nur Kleinbuchstaben enthält

  @AC6
  Scenario: AC6 – `requireSpecialChars=true`
    Given `requireSpecialChars=true`
    When  ein Passwort geprüft wird
    Then  werden alle druckbaren ASCII-Sonderzeichen als gültig akzeptiert (kein eingeschränkter Teilmenge-Check)

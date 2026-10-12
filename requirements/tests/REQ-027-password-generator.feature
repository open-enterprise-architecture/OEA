# Ableitung aus: requirements/req/req-027-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-03
Feature: REQ-027 – Passwort-Generator für sichere lokale Passwörter

  Das System MUSS einen kryptographisch sicheren Passwort-Generator
  bereitstellen, der Passwörter aus dem vollständigen druckbaren
  ASCII-Sonderzeichen-Zeichensatz erzeugt und die jeweils
  konfigurierten Passwort-Richtlinien (REQ-028) automatisch erfüllt;
  der Generator MUSS sowohl im Admin-Kontext (initiales Passwort
  setzen, REQ-024) als auch im Nutzer-Kontext (Passwort ändern)
  verfügbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Instanz-Konfiguration erzwingt Großbuchstaben, Kleinbuchstaben, Ziffern ...
    Given Instanz-Konfiguration erzwingt Großbuchstaben, Kleinbuchstaben, Ziffern und Sonderzeichen
    When  der Generator ein Passwort erzeugt
    Then  enthält es mindestens je ein Zeichen aus jedem erzwungenen Typ

  @AC2
  Scenario: AC2 – Sonderzeichen sind aktiviert
    Given Sonderzeichen sind aktiviert
    When  der Generator 1000 Passwörter erzeugt
    Then  enthält jedes mindestens ein Sonderzeichen aus dem vollständigen ASCII-Sonderzeichen-Zeichensatz (nicht nur `!@#$%` o.ä.)

  @AC3
  Scenario: AC3 – ein generiertes Passwort
    Given ein generiertes Passwort
    When  es gegen REQ-028-Richtlinien geprüft wird
    Then  erfüllt es alle aktiven Regeln (kein Nachbessern durch den Aufrufer nötig)

  @AC4
  Scenario: AC4 – CSPRNG ist verfügbar
    Given CSPRNG ist verfügbar
    When  1000 Passwörter gleicher Länge generiert werden
    Then  sind alle verschieden (keine Kollisionen in dieser Stichprobe)

  @AC5
  Scenario: AC5 – die Mindestlänge-Konfiguration ist zu niedrig für die erzwungenen Zeiche...
    Given die Mindestlänge-Konfiguration ist zu niedrig für die erzwungenen Zeichentypen
    When  der Generator aufgerufen wird
    Then  gibt er einen Fehler zurück, anstatt ein nicht-konformes Passwort zu erzeugen

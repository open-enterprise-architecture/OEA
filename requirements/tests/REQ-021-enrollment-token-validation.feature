# Ableitung aus: requirements/req/req-021-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @skip @UC-03
Feature: REQ-021 – Enrollment-Token-Validierung und Einmaligkeit

  Das System MUSS vor Anzeige der Enrollment-Oberfläche prüfen, ob das
  übergebene Enrollment-Token existiert, noch nicht verbraucht und
  nicht abgelaufen ist; nach erfolgreich abgeschlossenem Enrollment
  MUSS das Token als verbraucht markiert werden und bei erneutem
  Einlösen abgelehnt werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – ein gültiges, nicht verbrauchtes, nicht abgelaufenes Enrollment-Token
    Given ein gültiges, nicht verbrauchtes, nicht abgelaufenes Enrollment-Token
    When  Kurt den Einrichtungslink öffnet
    Then  zeigt das System die Enrollment-Oberfläche mit den verfügbaren Methoden

  @AC2
  Scenario: AC2 – ein bereits verbrauchtes Enrollment-Token
    Given ein bereits verbrauchtes Enrollment-Token
    When  Kurt den Einrichtungslink erneut öffnet
    Then  zeigt das System eine allgemeine Fehlermeldung, kein Formular

  @AC3
  Scenario: AC3 – ein abgelaufenes Enrollment-Token
    Given ein abgelaufenes Enrollment-Token
    When  Kurt den Einrichtungslink öffnet
    Then  zeigt das System dieselbe allgemeine Fehlermeldung wie bei AC2 (keine Unterscheidung)

  @AC4
  Scenario: AC4 – ein erfolgreich abgeschlossenes Enrollment
    Given ein erfolgreich abgeschlossenes Enrollment
    When  das Token danach erneut eingelöst werden soll
    Then  wird es abgelehnt (identisch zu AC2)

  @AC5
  Scenario: AC5 – ein gültiges Token wird verarbeitet
    Given ein gültiges Token wird verarbeitet
    When  das System den Token-Wert in Logs schreibt
    Then  ist der Token-Wert nicht im Klartext im Server-Log enthalten (gehasht oder weggelassen)

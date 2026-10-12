# Ableitung aus: requirements/req/req-031-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-03
Feature: REQ-031 – Passwort-Reset durch Administrator

  Das System MUSS einem Administrator ermöglichen, das lokale Passwort
  einer beliebigen Person zurückzusetzen; dabei MUSS das bisherige
  Passwort-Credential sofort revoked werden, ein neues
  Passwort-Credential gesetzt werden (compliant mit REQ-028), dem
  Administrator einmalig im Klartext angezeigt werden, und für die
  betroffene Person MUSS automatisch die Required Action „Passwort bei
  nächstem Login ändern" gesetzt werden, die sämtlichen
  Applikationszugriff bis zur Ausführung blockiert.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Person hat ein aktives Password-Credential und kann sich aktuell einloggen
    Given Person hat ein aktives Password-Credential und kann sich aktuell einloggen
    When  ein Administrator das Passwort zurücksetzt
    Then  ist das alte Password-Credential `revoked`; ein neues ist `active`; das neue Passwort-Klartext erscheint einmalig

  @AC2
  Scenario: AC2 – Passwort wurde zurückgesetzt
    Given Passwort wurde zurückgesetzt
    When  die Person sich mit dem neuen Passwort einloggt
    Then  wird Required Action `password_change` erkannt; die Person erhält keinen Applikationszugriff, bis das Passwort geändert wurde

  @AC3
  Scenario: AC3 – Passwort wurde zurückgesetzt
    Given Passwort wurde zurückgesetzt
    When  die Person versucht, sich mit dem alten Passwort einzuloggen
    Then  schlägt der Login fehl (altes Credential ist revoked)

  @AC4
  Scenario: AC4 – Administrator setzt Passwort zurück
    Given Administrator setzt Passwort zurück
    When  der Audit-Log ausgewertet wird
    Then  enthält er Admin-Person-ID, Ziel-Person-ID, Zeitpunkt, Aktion `password_reset`; kein Passwort-Klartext im Log

  @AC5
  Scenario: AC5 – der Vorgang schlägt nach Schritt 4 fehl (z.B. DB-Fehler)
    Given der Vorgang schlägt nach Schritt 4 fehl (z.B. DB-Fehler)
    When  der Fehler auftritt
    Then  sind weder das alte Credential revoked noch ein neues angelegt (vollständiger Rollback)

  @AC6
  Scenario: AC6 – System-Admin-Account soll zurückgesetzt werden
    Given System-Admin-Account soll zurückgesetzt werden
    When  der Admin die Anfrage stellt
    Then  wird sie mit einem Fehler abgelehnt; kein Credential geändert

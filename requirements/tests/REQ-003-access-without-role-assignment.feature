# Ableitung aus: requirements/req/req-003-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@business-rule @must @UC-01
Feature: REQ-003 – Eingeschränkter Zugriff bei fehlender aktiver Rollenzuweisung

  Eine erfolgreich authentifizierte Person OHNE aktive Rollenzuweisung
  DARF NICHT auf andere als öffentlich-lesbare Inhalte des Repositorys
  zugreifen.

  @AC1
  Scenario: AC1 – eine Person ist erfolgreich authentifiziert, hat aber keine aktive Rolle...
    Given eine Person ist erfolgreich authentifiziert, hat aber keine aktive Rollenzuweisung
    When  sie auf das Repository zugreift
    Then  erhält sie ausschließlich Zugriff auf öffentlich-lesbare Inhalte und einen Hinweis, dass keine Rolle zugewiesen ist

  @AC2
  Scenario: AC2 – eine Person erhält nachträglich eine aktive Rollenzuweisung
    Given eine Person erhält nachträglich eine aktive Rollenzuweisung
    When  sie sich erneut anmeldet oder die Session aktualisiert wird
    Then  spiegeln ihre Berechtigungen die neue Rolle wider

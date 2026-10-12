# Ableitung aus: requirements/req/req-004-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-01
Feature: REQ-004 – Ablehnung der Session-Erstellung für unbekannte oder offboardete Personen

  Das System MUSS die Session-Erstellung verweigern, wenn ein gültiges
  Identity-Token keiner aktiven
  [Person](../../business-objects/person.md) im Repository zugeordnet
  werden kann oder die zugeordnete Person den Lifecycle-Status
  `offboarded` hat.

  Background:
    Given eine OEA-Instanz mit konfiguriertem Authentifizierungsmechanismus
    And eine Person mit E-Mail "test@example.com" und aktiver Rolle existiert

  @AC1
  Scenario: AC1 – ein gültiges Identity-Token, das keiner Person im Repository zugeordnet ...
    Given ein gültiges Identity-Token, das keiner Person im Repository zugeordnet werden kann
    When  der Login-Vorgang abgeschlossen wird
    Then  wird keine Session erstellt

  @AC2
  Scenario: AC2 – ein gültiges Identity-Token, dessen zugeordnete Person den Status `offbo...
    Given ein gültiges Identity-Token, dessen zugeordnete Person den Status `offboarded` hat
    When  der Login-Vorgang abgeschlossen wird
    Then  wird keine Session erstellt

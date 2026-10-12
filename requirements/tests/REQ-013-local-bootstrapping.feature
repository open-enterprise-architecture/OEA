# Ableitung aus: requirements/req/req-013-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-02
Feature: REQ-013 – Lokales Bootstrapping bei fehlendem System-Admin-Account

  Das System MUSS erkennen, wenn für eine Instanz noch kein
  [System-Admin-Account](../../business-objects/system-admin-account.md)
  existiert, und in diesem Fall einen lokalen Bootstrapping-Vorgang
  anbieten, der ohne externen Identity-Provider auskommt.

  Background:
    Given eine frisch installierte OEA-Instanz ohne System-Admin-Account

  @AC1
  Scenario: AC1 – eine frisch installierte Instanz ohne System-Admin-Account
    Given eine frisch installierte Instanz ohne System-Admin-Account
    When  sie zum ersten Mal gestartet bzw. aufgerufen wird
    Then  bietet das System den lokalen Bootstrapping-Vorgang an, ohne einen externen IdP vorauszusetzen

  @AC2
  Scenario: AC2 – ein abgeschlossener lokaler Bootstrapping-Vorgang
    Given ein abgeschlossener lokaler Bootstrapping-Vorgang
    When  der Operator sich mit dem festgelegten Credential anmeldet
    Then  erhält er Zugriff mit System-Admin-Rechten

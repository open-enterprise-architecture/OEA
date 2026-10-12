# Ableitung aus: requirements/req/req-007-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@business-rule @should @UC-01
Feature: REQ-007 – Lifecycle-Übergang invited→active beim ersten erfolgreichen Login

  Das System SOLL den Lifecycle-Status einer
  [Person](../../business-objects/person.md) von `invited` zu `active`
  ändern, sobald ihr erster erfolgreicher Login-Vorgang abgeschlossen
  ist.

  @AC1
  Scenario: AC1 – eine Person mit Status `invited`
    Given eine Person mit Status `invited`
    When  sie sich erstmals erfolgreich anmeldet
    Then  wechselt ihr Status zu `active`

  @AC2
  Scenario: AC2 – eine Person mit Status `active`
    Given eine Person mit Status `active`
    When  sie sich erneut anmeldet
    Then  bleibt ihr Status unverändert `active` (kein erneuter Übergang, keine Seiteneffekte)

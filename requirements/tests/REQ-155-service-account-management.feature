# Ableitung aus: requirements/req/req-155-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + demonstration

@functional @should @UC-02
Feature: REQ-155 – Service-Account-Verwaltung für externe Systeme

  Das System **MUSS** es Administratoren ermöglichen, Service-Accounts
  anzulegen, ihnen die Rolle `integration-writer` zuzuweisen und
  rotierende Client-Credentials zu vergeben, damit externe Systeme ohne
  menschliche Benutzeranmeldung auf die Schreib-API zugreifen können.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Admin ist eingeloggt
    Given Admin ist eingeloggt
    When  POST /admin/service-accounts mit `{name: "ci-scanner", role: "integration-writer"}`
    Then  201 Created; Response enthält einmalig `clientSecret`; DB enthält `machine_credentials`-Eintrag

  @AC2
  Scenario: AC2 – Service-Account mit gültigem client_id/secret
    Given Service-Account mit gültigem client_id/secret
    When  OAuth2 Client-Credentials-Flow → JWT erhalten; PUT /entities/by-external-id/...
    Then  200/201; Audit-Log zeigt `source=API`, `agent_id=<client_id>`

  @AC3
  Scenario: AC3 – Admin rotiert Credentials eines Service-Accounts
    Given Admin rotiert Credentials eines Service-Accounts
    When  Altes Secret für Auth-Request verwendet
    Then  401 Unauthorized; neues Secret erforderlich

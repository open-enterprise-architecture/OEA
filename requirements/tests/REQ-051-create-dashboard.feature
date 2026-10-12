# Ableitung aus: requirements/req/req-051-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-07
Feature: REQ-051 – Dashboard anlegen

  Das System MUSS es berechtigten Nutzern ermöglichen, ein neues
  [Dashboard](../../business-objects/dashboard.md)-Objekt mit Name,
  optionaler Beschreibung und Scope (`instance` oder `personal`)
  anzulegen. Dashboards mit `scope=instance` DÜRFEN nur von Nutzern
  angelegt werden, die die Rolle „Enterprise Architekt" oder eine
  äquivalente Dashboard-Schreibberechtigung besitzen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Anlegen mit scope=instance – Kurt (Rolle Enterprise Architekt) ist eingeloggt
    Given Kurt (Rolle Enterprise Architekt) ist eingeloggt
    When  `POST /api/v1/dashboards` mit name="IT-Investitionsplanung" und scope=instance
    Then  HTTP 201; Dashboard-Objekt in DB; für alle Nutzer im GET /api/v1/dashboards sichtbar

  @AC2
  Scenario: AC2 – Duplikat-Name – Dashboard „IT-Investitionsplanung" (scope=instance) existiert bereits
    Given Dashboard „IT-Investitionsplanung" (scope=instance) existiert bereits
    When  zweites POST mit demselben Namen
    Then  HTTP 409 mit Fehlermeldung „Name bereits vergeben"

  @AC3
  Scenario: AC3 – Fehlende Berechtigung für scope=instance – Franz (Rolle Junior Domain Architekt, keine Dashboard-Schreibberechtigun...
    Given Franz (Rolle Junior Domain Architekt, keine Dashboard-Schreibberechtigung) ist eingeloggt
    When  POST mit scope=instance
    Then  HTTP 403; kein Dashboard angelegt

  @AC4
  Scenario: AC4 – Personal-Dashboard für beliebigen Nutzer – Franz (ohne Schreibberechtigung) ist eingeloggt
    Given Franz (ohne Schreibberechtigung) ist eingeloggt
    When  POST mit scope=personal, name="Meine Analyse"
    Then  HTTP 201; Dashboard ist nur für Franz sichtbar (in GET /api/v1/dashboards für andere Nutzer nicht enthalten)

  @AC5
  Scenario: AC5 – Scope-Upgrade – Franz hat ein personal-Dashboard; später erhält er die Dashboard-Berecht...
    Given Franz hat ein personal-Dashboard; später erhält er die Dashboard-Berechtigung
    When  PUT mit scope=instance
    Then  HTTP 200; Dashboard ab sofort für alle sichtbar

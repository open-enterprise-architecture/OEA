# Ableitung aus: requirements/req/req-084-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-11
Feature: REQ-084 – Plateau anlegen

  Das System MUSS das Anlegen von Plateaus mit den Status `baseline`,
  `target` und `transition` ermöglichen. Pflichtfeld: `name`; optional:
  `description` (max.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Initiales Baseline anlegen – ein Plateau ohne `succeeds`-Referenz und mit `status=baseline` angelegt ...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Plateau ohne `succeeds`-Referenz und mit `status=baseline` angelegt wird
    Then  antwortet die API mit HTTP 201 und `status=baseline`

  @AC2
  Scenario: AC2 – Target anlegen – ein Plateau mit `status=target` und `succeeds`=Baseline-ID angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Plateau mit `status=target` und `succeeds`=Baseline-ID angelegt wird
    Then  antwortet die API mit HTTP 201; `status=target` und `succeeds` sind korrekt gesetzt

  @AC3
  Scenario: AC3 – Zweites Baseline abweisen – ein weiteres Plateau mit `status=baseline` angelegt wird, obwohl bereits...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein weiteres Plateau mit `status=baseline` angelegt wird, obwohl bereits ein Baseline-Plateau existiert
    Then  antwortet die API mit HTTP 422 ("Es existiert bereits ein Baseline-Plateau")

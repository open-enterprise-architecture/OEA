# Ableitung aus: requirements/req/req-148-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-04
Feature: REQ-148 – Property-Mapping Stichtag änderbar

  Das System **MUSS** es ermöglichen, die Notwendigkeit
  (mandatory/warning/optional) einer Property-Zuordnung zu einem
  MetaTyp per Stichtag zu ändern, ohne bestehende Werte zu löschen oder
  historische Zuordnungen zu überschreiben.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Property P ist für MetaTyp M seit 2026-01-01 als `optional` gemappt
    Given Property P ist für MetaTyp M seit 2026-01-01 als `optional` gemappt
    When  Admin legt neues Mapping P→M mit valid_from=2026-07-01, necessity=mandatory an
    Then  Ab 2026-07-01 gilt mandatory; Entitäten die P fehlt, erhalten Warning/Error bei Validierung

  @AC2
  Scenario: AC2 – Ein Mapping-Eintrag für valid_from=2026-01-01 existiert
    Given Ein Mapping-Eintrag für valid_from=2026-01-01 existiert
    When  Admin versucht, denselben valid_from nochmals einzutragen
    Then  409 Conflict

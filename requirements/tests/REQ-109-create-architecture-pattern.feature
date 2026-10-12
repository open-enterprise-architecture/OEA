# Ableitung aus: requirements/req/req-109-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-17
Feature: REQ-109 – Architecture Pattern anlegen und verwalten

  Das System MUSS das Anlegen, Bearbeiten und Löschen von
  `ArchitecturePattern`-Einträgen ermöglichen. Pflichtfelder: `name`,
  `problem` (Markdown), `solution` (Markdown).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Pattern anlegen – ein Pattern mit `problem` und `solution` angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Pattern mit `problem` und `solution` angelegt wird
    Then  antwortet das System mit HTTP 201; das Pattern ist in der Bibliothek sichtbar

  @AC2
  Scenario: AC2 – Pflichtfeld problem – ein Pattern ohne `problem`-Feld gespeichert werden soll
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Pattern ohne `problem`-Feld gespeichert werden soll
    Then  antwortet das System mit HTTP 422 „problem ist ein Pflichtfeld"

  @AC3
  Scenario: AC3 – relatedPatterns verknüpfen – `relatedPatterns` per Multi-Select mit anderen Patterns verknüpft werden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `relatedPatterns` per Multi-Select mit anderen Patterns verknüpft werden
    Then  werden die Verknüpfungen gespeichert und in der Detailansicht angezeigt

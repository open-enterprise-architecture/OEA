# Ableitung aus: requirements/req/req-110-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-17
Feature: REQ-110 – Reference Architecture aus Bausteinen komponieren

  Das System MUSS das Anlegen, Bearbeiten und Löschen von
  `ReferenceArchitecture`-Einträgen ermöglichen. Pflichtfelder: `name`,
  `continuumLevel`, `governanceStatus`.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Reference Architecture anlegen und als Blueprint verfügbar – eine Reference Architecture mit 3 ABBs und 1 Pattern angelegt und `gover...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Reference Architecture mit 3 ABBs und 1 Pattern angelegt und `governanceStatus=approved` gesetzt wird
    Then  antwortet das System mit HTTP 201; die Reference Architecture ist als Blueprint in UC-11 (Plateau-Anlage) auswählbar

  @AC2
  Scenario: AC2 – Leere Kompositions-Warnung – eine Reference Architecture ohne ABBs und ohne Patterns gespeichert werd...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Reference Architecture ohne ABBs und ohne Patterns gespeichert werden soll
    Then  zeigt das System eine Governance-Warnung; die Speicherung wird nicht blockiert

  @AC3
  Scenario: AC3 – Pflichtfeld targetIndustry – eine Reference Architecture mit `continuumLevel=industry` ohne `targetIn...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Reference Architecture mit `continuumLevel=industry` ohne `targetIndustry` gespeichert werden soll
    Then  antwortet das System mit HTTP 422

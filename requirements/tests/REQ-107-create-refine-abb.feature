# Ableitung aus: requirements/req/req-107-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-17
Feature: REQ-107 – ABB anlegen und Verfeinerungs-Hierarchie verwalten

  Das System MUSS das Anlegen, Bearbeiten und Löschen von
  `ArchitectureBuildingBlock`-Einträgen (ABBs) mit `scope=organization`
  ermöglichen. Pflichtfelder: `name` (eindeutig je `continuumLevel`),
  `continuumLevel`, `maturityLevel`, `governanceStatus`.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – ABB anlegen – ein ABB mit `continuumLevel=organization` und `maturityLevel=established...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein ABB mit `continuumLevel=organization` und `maturityLevel=established` angelegt wird
    Then  antwortet das System mit HTTP 201; der ABB ist in der Bibliothek sichtbar

  @AC2
  Scenario: AC2 – Zyklus-Erkennung – ABB „B" auf `refines=A` gesetzt ist und anschließend ABB „A" auf `refine...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ABB „B" auf `refines=A` gesetzt ist und anschließend ABB „A" auf `refines=B` gesetzt werden soll
    Then  antwortet das System mit HTTP 422 „Zyklus erkannt: A → B → A" und lehnt die Speicherung ab

  @AC3
  Scenario: AC3 – Pflichtfeld industry – ein ABB mit `continuumLevel=industry` ohne das Feld `industry` gespeiche...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein ABB mit `continuumLevel=industry` ohne das Feld `industry` gespeichert werden soll
    Then  antwortet das System mit HTTP 422

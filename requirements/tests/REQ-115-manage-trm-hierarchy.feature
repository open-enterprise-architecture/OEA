# Ableitung aus: requirements/req/req-115-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-19
Feature: REQ-115 – TRM-Kategorie-Hierarchie verwalten

  Das System MUSS das Anlegen, Umbenennen und Löschen von
  TRM-Kategorien mit `scope=organization` ermöglichen. Beim Anlegen
  MUSS das `level`-Feld automatisch als `parent.level + 1` gesetzt
  werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Automatisches level – eine Unterkategorie unter „Security Services" angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Unterkategorie unter „Security Services" angelegt wird
    Then  ist das `level`-Feld automatisch auf `parent.level + 1` gesetzt

  @AC2
  Scenario: AC2 – Löschen mit Unterknoten – eine Kategorie mit 3 Unterknoten gelöscht werden soll
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Kategorie mit 3 Unterknoten gelöscht werden soll
    Then  antwortet das System mit HTTP 422 „Unterknoten müssen zuerst entfernt werden"

  @AC3
  Scenario: AC3 – Drag & Drop Zyklus-Schutz – eine Kategorie per Drag & Drop unter einen ihrer eigenen Nachkommen gezo...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Kategorie per Drag & Drop unter einen ihrer eigenen Nachkommen gezogen wird
    Then  wird der Drop visuell blockiert; ein API-Versuch antwortet mit HTTP 422

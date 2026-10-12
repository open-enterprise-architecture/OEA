# Ableitung aus: requirements/req/req-134-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-09
Feature: REQ-134 – DocumentItem-Querverweis via {{ im WYSIWYG-Editor

  Das System MUSS im WYSIWYG-Editor von DocumentItems das Tippen von
  `{{` als Auslöser für ein Autocomplete-Dropdown erkennen, das
  DocumentItems der **aktuellen DocumentCollection** durchsucht.
  Gesucht wird sowohl über `alias` als auch über `name`.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Autocomplete-Trigger – Nutzer im Freitext-Bereich `{{` tippt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer im Freitext-Bereich `{{` tippt
    Then  Dropdown öffnet sich mit allen DocumentItems der aktuellen Collection; Suche über `alias` und `name`; erstes Ergebnis vorselektiert

  @AC2
  Scenario: AC2 – Einfügen – Nutzer einen DocumentItem aus dem Dropdown auswählt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer einen DocumentItem aus dem Dropdown auswählt
    Then  `{{Kapitelname|item:uuid}}` wird an Cursor-Position eingefügt; Dropdown schließt sich

  @AC3
  Scenario: AC3 – Rendering — bestehender Name – DocumentItem mit Querverweis gerendert wird und das Ziel existiert
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  DocumentItem mit Querverweis gerendert wird und das Ziel existiert
    Then  Anklickbarer Inline-Link mit aktuellem `name` des Ziel-Items dargestellt; Klick navigiert zum Ziel-Kapitel

  @AC4
  Scenario: AC4 – Rendering — umbenanntes Ziel – Ziel-Item umbenannt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ziel-Item umbenannt wird
    Then  Alle Querverweise auf dieses Item zeigen automatisch den neuen Namen (ID-Auflösung)

  @AC5
  Scenario: AC5 – Rendering — gelöschtes Ziel – Ziel-Item gelöscht wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ziel-Item gelöscht wird
    Then  Verweis zeigt `[gelöschtes Kapitel|item:uuid]` in roter Schrift; keine Navigation möglich

  @AC6
  Scenario: AC6 – Code-Block – Nutzer in einem Mermaid- oder PlantUML-Block `{{` tippt und auswählt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer in einem Mermaid- oder PlantUML-Block `{{` tippt und auswählt
    Then  Gespeichert wird reiner `name`-Text (kein `|item:uuid`); kein Link im Rendering

# Ableitung aus: requirements/req/req-132-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-09
Feature: REQ-132 – DocumentItem als atomare Einheit der DocumentCollection

  Das System MUSS `DocumentItem` als atomare Instanz-Einheit einer
  `DocumentCollection` bereitstellen. Jeder DocumentItem MUSS folgende
  Felder tragen: | Feld | Pflicht | Beschreibung | |---|---|---| |
  `name` | required | Kapitelbezeichnung; wird in der Kapitelnavigation
  und als Überschrift im Inhalt angezeigt; max.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Anlegen – Nutzer in einer DocumentCollection ein neues Kapitel anlegt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer in einer DocumentCollection ein neues Kapitel anlegt
    Then  System erstellt einen DocumentItem mit `name` (Pflicht), optionalem `alias`, leerem `content` und automatisch vergebenem `sortOrder`

  @AC2
  Scenario: AC2 – Name in Navigation – DocumentCollection geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  DocumentCollection geöffnet wird
    Then  Kapitelnavigation zeigt `name` jedes Items; Items sind nach `sortOrder` sortiert

  @AC3
  Scenario: AC3 – Alias eindeutig – Nutzer `alias` setzt, der innerhalb der Collection bereits vergeben ist
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer `alias` setzt, der innerhalb der Collection bereits vergeben ist
    Then  Inline-Fehler; Speichern verhindert

  @AC4
  Scenario: AC4 – Content WYSIWYG – Nutzer einen DocumentItem öffnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer einen DocumentItem öffnet
    Then  WYSIWYG-Editor ist verfügbar; alle unterstützten Formate (Markdown, Mermaid, PlantUML, Draw.io, `[[`, `{{`) sind nutzbar

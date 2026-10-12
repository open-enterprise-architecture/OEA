# Ableitung aus: requirements/req/req-139-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-13
Feature: REQ-139 – Jede Solution hat eine auto-generierte Grundstruktur im Explorer

  Jede Solution MUSS im Browser-Panel automatisch und ohne manuelle
  Konfiguration sechs Grundkategorien als Unterknoten enthalten:
  **Komponenten**, **Verbindungen**, **Kataloge**, **Functional
  Building Blocks**, **Solution Building Blocks** und **Diagramme**.
  Die Kategorien MÜSSEN die Anzahl der enthaltenen Objekte anzeigen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Beliebige Solution (mit oder ohne Inhalt)
    Given Beliebige Solution (mit oder ohne Inhalt)
    When  Solution im Browser aufgeklappt wird
    Then  Genau sechs Kategorien als Unterknoten sichtbar

  @AC2
  Scenario: AC2 – Solution mit 0 Katalogen
    Given Solution mit 0 Katalogen
    When  Solution im Browser aufgeklappt wird
    Then  „Kataloge (0)" erscheint ausgegraut — nicht ausgeblendet

  @AC3
  Scenario: AC3 – Solution hat 3 Komponenten und 5 Verbindungen
    Given Solution hat 3 Komponenten und 5 Verbindungen
    When  Browser angezeigt wird
    Then  „Komponenten (3)" und „Verbindungen (5)" korrekt angezeigt

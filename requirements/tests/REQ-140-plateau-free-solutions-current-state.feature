# Ableitung aus: requirements/req/req-140-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-11 @UC-13
Feature: REQ-140 – Plateau-freie Solutions nur im Aktueller-Stand-Knoten sichtbar

  Solutions im Projekt-Modus (beide Plateau-IDs `null`) DÜRFEN NICHT
  als eigenständige Top-Level-Knoten im Browser erscheinen. Ihre
  implementierten Inhalte MÜSSEN in den virtuellen „Aktueller
  Stand"-Knoten einfliessen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Solution A (Projekt-Modus, `status=implemented`), Solution B (Plateau-Mo...
    Given Solution A (Projekt-Modus, `status=implemented`), Solution B (Plateau-Modus, IST→SOLL)
    When  Browser-Panel geöffnet wird
    Then  Solution A erscheint NICHT als eigener Knoten; Solution B erscheint unter Plateau-Knoten

  @AC2
  Scenario: AC2 – Solution A (Projekt-Modus, `status=implemented`) mit 3 Komponenten
    Given Solution A (Projekt-Modus, `status=implemented`) mit 3 Komponenten
    When  „Aktueller Stand" im Browser aufgeklappt wird
    Then  Die 3 Komponenten sind unter „Aktueller Stand → Komponenten" sichtbar

  @AC3
  Scenario: AC3 – Solution C (Projekt-Modus, `status=in-progress`)
    Given Solution C (Projekt-Modus, `status=in-progress`)
    When  Browser-Panel angezeigt wird
    Then  Solution C ist weder im „Aktueller Stand" noch anderswo im Browser sichtbar

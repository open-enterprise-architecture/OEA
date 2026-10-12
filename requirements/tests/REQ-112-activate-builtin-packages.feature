# Ableitung aus: requirements/req/req-112-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-18
Feature: REQ-112 – Eingebettete Continuum-Pakete aktivieren

  Das System MUSS eine Liste von eingebetteten Continuum-Paketen
  anbieten (mindestens: „TOGAF 10 – Technical Reference Model"). Jedes
  Paket MUSS vor dem Import eine Vorschau anzeigen: Anzahl ABBs, SBBs,
  Patterns, Reference Architectures und TRM-Kategorien sowie die
  wichtigsten Kategorie-Namen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Paket-Vorschau – die Paketliste geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Paketliste geöffnet wird
    Then  ist „TOGAF 10 TRM" mit Vorschau (47 ABBs, 38 TRM-Kategorien, 12 Patterns) sichtbar

  @AC2
  Scenario: AC2 – Import per Klick – auf „Importieren" geklickt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  auf „Importieren" geklickt wird
    Then  wird der Import atomar ausgeführt; ein Import-Protokoll zeigt das Ergebnis

  @AC3
  Scenario: AC3 – Bereits importiertes Paket – ein Paket bereits importiert wurde
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Paket bereits importiert wurde
    Then  zeigt die UI ein Badge „Bereits importiert" und einen Hinweis auf Konflikt-Handling

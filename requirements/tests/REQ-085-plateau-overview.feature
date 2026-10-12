# Ableitung aus: requirements/req/req-085-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-11
Feature: REQ-085 – Plateau-Übersicht nach Status gruppiert

  Das System MUSS Plateaus in einer strukturierten Übersicht nach
  Status gruppiert anzeigen: aktuelles Baseline (hervorgehoben),
  Target-Plateaus (mit geplantem `validFrom`), realisierte Plateaus
  (einklappbar, chronologisch absteigend). Jedes Plateau zeigt Name,
  Status, `validFrom`/`validTo` und verknüpfte Solutions-Anzahl.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Gruppierte Übersicht – die Plateau-Übersicht aufgerufen wird und Baseline-, Target- und Realize...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Plateau-Übersicht aufgerufen wird und Baseline-, Target- und Realized-Plateaus existieren
    Then  wird das Baseline hervorgehoben, Targets mit `validFrom` angezeigt und Realized-Plateaus als einklappbare Sektion dargestellt

  @AC2
  Scenario: AC2 – Leere Target-Sektion – keine Target-Plateaus existieren
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  keine Target-Plateaus existieren
    Then  wird die Target-Sektion mit dem Hinweis "Noch kein Ziel-Plateau geplant" angezeigt

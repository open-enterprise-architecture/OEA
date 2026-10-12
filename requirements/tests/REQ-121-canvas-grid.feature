# Ableitung aus: requirements/req/req-121-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-05
Feature: REQ-121 – Canvas-Raster mit Orientierungslinien

  Der Diagramm-Canvas MUSS ein sichtbares Raster darstellen, das aus
  **4 dünnen Hilfslinien** gefolgt von **1 dickeren
  Orientierungslinie** besteht (Wiederholungsperiode: 5
  Rastereinheiten). Entities, die auf dem Canvas platziert oder in der
  Grösse verändert werden, MÜSSEN auf den Rasterpunkten einrasten.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Raster-Darstellung – Canvas geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Canvas geöffnet wird
    Then  Raster sichtbar mit Muster 4 dünne + 1 dicke Linie; dickere Linie hat deutlich höheren visuellen Kontrast als dünne

  @AC2
  Scenario: AC2 – Entity-Einrasten – Eine Entity auf dem Canvas verschoben oder angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Eine Entity auf dem Canvas verschoben oder angelegt wird
    Then  Position und Grösse rasten auf die nächste Rastereinheit ein; sub-Raster-Positionierung ist nicht möglich

  @AC3
  Scenario: AC3 – Raster ein-/ausblenden – Nutzer `G` drückt oder Toolbar-Button klickt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer `G` drückt oder Toolbar-Button klickt
    Then  Raster-Linien nicht mehr sichtbar; Einrasten bleibt weiterhin aktiv

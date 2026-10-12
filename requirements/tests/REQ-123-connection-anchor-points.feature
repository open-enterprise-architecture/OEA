# Ableitung aus: requirements/req/req-123-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-05
Feature: REQ-123 – Ankerpunkte auf Connections

  Das System MUSS das Setzen, Verschieben und Entfernen von
  **Ankerpunkten** (Waypoints) auf Connection-Linien ermöglichen.
  Ankerpunkte steuern den Verlauf der Connection zwischen Quell- und
  Ziel-Entität.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Ankerpunkt setzen – Nutzer auf eine selektierte Connection klickt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer auf eine selektierte Connection klickt
    Then  Neuer Ankerpunkt wird auf dem nächsten Rasterpunkt gesetzt und ist als Griff sichtbar

  @AC2
  Scenario: AC2 – Ankerpunkt immer auf Raster – Ankerpunkt per Drag & Drop verschoben wird (beliebiger Routing-Modus)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ankerpunkt per Drag & Drop verschoben wird (beliebiger Routing-Modus)
    Then  Ankerpunkt rastet auf nächsten Rasterpunkt ein; sub-Raster-Positionierung ist nicht möglich

  @AC3
  Scenario: AC3 – Ankerpunkt entfernen – Nutzer einen Ankerpunkt doppelklickt oder im Kontextmenü „Ankerpunkt ent...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer einen Ankerpunkt doppelklickt oder im Kontextmenü „Ankerpunkt entfernen" wählt
    Then  Ankerpunkt wird entfernt; Connection passt ihren Verlauf automatisch an

  @AC4
  Scenario: AC4 – Modus-Verhalten – Connection-Modus = Curved und Ankerpunkt gesetzt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Connection-Modus = Curved und Ankerpunkt gesetzt
    And   Connection-Modus = Orthogonal und Ankerpunkt gesetzt
    Then  Bézierkurve verläuft durch den Ankerpunkt; Kurven-Griffe erscheinen beidseitig des Ankerpunkts zur Kurven-Steuerung
    And   Segmente verlaufen rechtwinklig zum Ankerpunkt; Ankerpunkt liegt auf Raster-Schnittpunkt

# Ableitung aus: requirements/req/req-072-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert, Performance-Test im Browser)

@non-functional @performance @must @UC-05
Feature: REQ-072 – Canvas-Rendering-Latenz (Diagramm laden und rendern)

  Das System MUSS ein Architektur-Diagramm — vom Öffnen bis zur
  vollständig interaktiven Darstellung aller Shapes und Connections auf
  dem Canvas — innerhalb eines definierten Zeitbudgets darstellen.

  @AC1
  Scenario: AC1 – Kleines Diagramm – Diagramm mit 50 Entities + 30 Connections; Chrome auf Standard-Developer...
    Given Diagramm mit 50 Entities + 30 Connections; Chrome auf Standard-Developer-Hardware
    When  Diagramm geöffnet
    Then  Canvas interaktiv in < 500ms (gemessen ab vollständiger API-Response)

  @AC2
  Scenario: AC2 – Mittleres Diagramm – 150 Entities + 100 Connections
    Given 150 Entities + 100 Connections
    When  Diagramm geöffnet
    Then  Time-to-Interactive < 1.500ms

  @AC3
  Scenario: AC3 – Grosses Diagramm – 400 Entities + 300 Connections
    Given 400 Entities + 300 Connections
    When  Diagramm geöffnet
    Then  Time-to-Interactive < 3.000ms; keine Browser-Freezes > 100ms

  @AC4
  Scenario: AC4 – Pan/Zoom flüssig – mittleres Diagramm (150+100)
    Given mittleres Diagramm (150+100)
    When  Nutzer zieht Canvas oder zoomt mit Mausrad
    Then  Frame-Rate ≥ 30 fps (kein sichtbares Ruckeln)

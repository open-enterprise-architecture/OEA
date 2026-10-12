# Ableitung aus: requirements/req/req-122-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-05
Feature: REQ-122 – Connection-Routing-Modi (Curved, Gerade, Orthogonal)

  Das System MUSS für jede Connection auf dem Canvas drei Routing-Modi
  anbieten, die pro Connection individuell wählbar sind: | Modus |
  Verlauf | Raster-Bezug | |---|---|---| | **Curved** | Bézierkurve;
  weiches Schwingen um Ankerpunkte | folgt **nicht** dem Raster | |
  **Gerade** | Direkte Linie vom Quell- zum Ziel-Ankerpunkt | folgt
  **nicht** zwingend dem Raster | | **Orthogonal** | Ausschliesslich
  90°-Winkel (rechtwinklige Führung) | folgt dem **Raster** | Der
  Routing-Modus MUSS über das Kontextmenü der Connection (Rechtsklick)
  oder eine Toolbar bei selektierter Connection änderbar sein. Der
  Standard-Routing-Modus für neue Connections MUSS im Viewpoint
  konfigurierbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Curved-Modus – Routing-Modus = Curved
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Routing-Modus = Curved
    Then  Connection wird als Bézierkurve gerendert; Kurven-Verlauf ist durch Bézierkurven-Griffe (via Ankerpunkte, REQ-123) steuerbar; Linie folgt keinem Raster

  @AC2
  Scenario: AC2 – Orthogonal-Modus – Routing-Modus = Orthogonal
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Routing-Modus = Orthogonal
    Then  Connection macht ausschliesslich 90°-Biegungen; alle Segmente verlaufen horizontal oder vertikal; Biegepunkte liegen auf Rasterpunkten (REQ-121)

  @AC3
  Scenario: AC3 – Modus-Wechsel – Nutzer Routing-Modus einer bestehenden Connection ändert
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer Routing-Modus einer bestehenden Connection ändert
    Then  Connection wird sofort neu gerendert; bestehende Ankerpunkte (REQ-123) bleiben erhalten und werden auf das neue Routing angewendet (ggf. auf Raster gerundet bei Wechsel zu Orthogonal)

  @AC4
  Scenario: AC4 – Viewpoint-Default – Neue Connection auf Canvas gezogen wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Neue Connection auf Canvas gezogen wird
    Then  Routing-Modus entspricht dem im Viewpoint konfigurierten Default; ohne Konfiguration = Orthogonal

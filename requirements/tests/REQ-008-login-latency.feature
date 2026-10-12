# Ableitung aus: requirements/req/req-008-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert, Lasttest)

@non-functional @performance @should @UC-01
Feature: REQ-008 – Login-Latenz

  Das System SOLL den Login-Vorgang (ab Einlösen des Authorization
  Codes bzw. Validierung des API-Keys bis zur Session-Bestätigung)
  innerhalb eines definierten Zeitbudgets abschließen.

  @AC1
  Scenario: AC1 – ein Repository mit 10.000 Personen und 1.000 Rollen
    Given ein Repository mit 10.000 Personen und 1.000 Rollen
    When  95% der OIDC-Login-Vorgänge gemessen werden (serverseitiger Anteil)
    Then  liegt die Verarbeitungszeit unter 300ms

  @AC2
  Scenario: AC2 – ein Repository mit 10.000 aktiven API-Keys
    Given ein Repository mit 10.000 aktiven API-Keys
    When  95% der API-Key-Validierungen gemessen werden
    Then  liegt die Verarbeitungszeit unter 100ms

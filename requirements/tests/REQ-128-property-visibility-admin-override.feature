# Ableitung aus: requirements/req/req-128-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-21
Feature: REQ-128 – Admin-Override für Property-Sichtbarkeit

  Das System MUSS einen konfigurierbaren **Admin-Override**
  bereitstellen: Nutzer mit einer als
  `overridesPropertyVisibility=true` markierten Systemrolle (Standard:
  `system-admin`) MÜSSEN alle Properties lesen können, unabhängig von
  `visibilityMode`. Der Override-Status MUSS im Audit-Log vermerkt
  werden, wenn ein eingeschränktes Property von einem Override-Nutzer
  gelesen wird.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Override wirksam – Nutzer mit `system-admin`-Rolle eine Entität mit eingeschränkten Propert...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer mit `system-admin`-Rolle eine Entität mit eingeschränkten Properties abruft
    Then  Alle Werte werden zurückgegeben; `visibilityMode` hat keinen Effekt

  @AC2
  Scenario: AC2 – Audit-Log-Eintrag – Override-Nutzer ein `role-restricted` oder `connection-scoped` Feld liest
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Override-Nutzer ein `role-restricted` oder `connection-scoped` Feld liest
    Then  Audit-Log enthält Eintrag mit Typ `property-override-access`, Nutzer-ID, Entitäts-ID und Property-Name

  @AC3
  Scenario: AC3 – Override konfigurierbar – Kurt im Metamodell-Editor die Override-Rollen-Liste bearbeitet und eine ...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Kurt im Metamodell-Editor die Override-Rollen-Liste bearbeitet und eine weitere Rolle hinzufügt
    Then  Nutzer dieser Rolle erhalten ab sofort Override-Zugriff

  @AC4
  Scenario: AC4 – Kein impliziter Override – Nutzer hat `EA-Manager`-Rolle, aber `EA-Manager` ist nicht in der Overri...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer hat `EA-Manager`-Rolle, aber `EA-Manager` ist nicht in der Override-Liste
    Then  Sichtbarkeits-Einschränkungen greifen normal; kein automatischer Override durch Hierarchie-Annahme

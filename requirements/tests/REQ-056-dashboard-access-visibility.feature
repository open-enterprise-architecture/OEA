# Ableitung aus: requirements/req/req-056-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-07
Feature: REQ-056 – Dashboard-Zugriff und Sichtbarkeit (scope-Logik)

  Das System MUSS die Sichtbarkeit von
  [Dashboards](../../business-objects/dashboard.md) anhand des
  `scope`-Attributs durchsetzen: `scope=instance`-Dashboards MÜSSEN für
  alle eingeloggten Nutzer der Instanz sichtbar und lesbar sein;
  `scope=personal`-Dashboards DÜRFEN ausschliesslich für den Ersteller
  sichtbar sein. Kein Nutzer darf `scope=personal`-Dashboards anderer
  Nutzer lesen, auch nicht per direktem API-Zugriff.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – instance-Dashboard: alle sehen es – Kurt hat ein `scope=instance`-Dashboard angelegt
    Given Kurt hat ein `scope=instance`-Dashboard angelegt
    When  Franz ruft `GET /api/v1/dashboards` auf
    Then  Kurts Dashboard erscheint in der Liste

  @AC2
  Scenario: AC2 – personal-Dashboard: kein Datenleck – Franz hat ein `scope=personal`-Dashboard angelegt
    Given Franz hat ein `scope=personal`-Dashboard angelegt
    When  Kurt ruft `GET /api/v1/dashboards` oder `GET /api/v1/dashboards/{franz-dashboard-id}` auf
    Then  Franzs Dashboard erscheint weder in der Liste noch ist es per ID abrufbar (403)

  @AC3
  Scenario: AC3 – personal-URL-Zugriff geblockt – Kurts hat die ID von Franzs personal-Dashboard (z.B. erraten oder aus Logs)
    Given Kurts hat die ID von Franzs personal-Dashboard (z.B. erraten oder aus Logs)
    When  `GET /api/v1/dashboards/{id}/data` direkt aufgerufen
    Then  HTTP 403

  @AC4
  Scenario: AC4 – Scope-Upgrade – Franz hat ein personal-Dashboard; er erhält danach Dashboard-Schreibbere...
    Given Franz hat ein personal-Dashboard; er erhält danach Dashboard-Schreibberechtigung
    When  `PUT /api/v1/dashboards/{id}` mit scope=instance
    Then  HTTP 200; Dashboard ab sofort für alle sichtbar

  @AC5
  Scenario: AC5 – Downgrade blockiert wenn andere Nutzer – Kurt hat ein instance-Dashboard; 5 weitere Nutzer haben es aufgerufen
    Given Kurt hat ein instance-Dashboard; 5 weitere Nutzer haben es aufgerufen
    When  Kurt versucht PUT mit scope=personal
    Then  HTTP 409 „Dashboard wird von anderen Nutzern verwendet; Downgrade nicht möglich"

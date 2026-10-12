# Ableitung aus: requirements/req/req-015-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@business-rule @must @UC-02
Feature: REQ-015 – Kein paralleles oder wiederholtes Bootstrapping

  Das System DARF NICHT mehr als einen aktiven Bootstrapping-Vorgang
  gleichzeitig zulassen, und MUSS einen erneuten Bootstrapping-Versuch
  verweigern, sobald für die Instanz bereits ein
  [System-Admin-Account](../../business-objects/system-admin-account.md)
  existiert.

  @AC1
  Scenario: AC1 – zwei nahezu gleichzeitig gestartete Bootstrapping-Versuche auf derselben...
    Given zwei nahezu gleichzeitig gestartete Bootstrapping-Versuche auf derselben frischen Instanz
    When  beide um Abschluss konkurrieren
    Then  wird genau einer erfolgreich abgeschlossen, der andere abgelehnt

  @AC2
  Scenario: AC2 – eine Instanz mit bereits bestehendem System-Admin-Account
    Given eine Instanz mit bereits bestehendem System-Admin-Account
    When  ein erneuter Bootstrapping-Versuch unternommen wird
    Then  wird dieser ohne explizite Reset-Aktion durch einen bestehenden System-Admin-Account abgelehnt

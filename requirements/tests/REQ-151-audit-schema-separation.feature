# Ableitung aus: requirements/req/req-151-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + inspection

@compliance @must @UC-14
Feature: REQ-151 – Audit-Datenhaltung unveränderlich und schematrennt

  Das System **MUSS** alle Audit-Events in einem separaten
  Datenbankschema (`audit`) speichern, für das der Applikationsbenutzer
  ausschliesslich INSERT-Rechte besitzt; UPDATE und DELETE auf
  Audit-Tabellen **DÜRFEN NICHT** durch den Applikationsbenutzer
  ausführbar sein.

  @AC1
  Scenario: AC1 – OEA-Applikationsbenutzer `oea_app` aktiv
    Given OEA-Applikationsbenutzer `oea_app` aktiv
    When  Versuch, `UPDATE audit.events SET event_type='manipulated'` auszuführen
    Then  Datenbankfehler "permission denied"; kein Datensatz geändert

  @AC2
  Scenario: AC2 – Entity-Änderung via UI
    Given Entity-Änderung via UI
    When  Änderung persistiert
    Then  Eintrag in `audit.events` mit korrektem `event_type`, `actor_name`, `actor_email`, `occurred_at`; `old_value`/`new_value` in `audit.event_changes`

  @AC3
  Scenario: AC3 – `oea.datasource.audit.url` gesetzt (externe DB)
    Given `oea.datasource.audit.url` gesetzt (externe DB)
    When  Applikation startet
    Then  Audit-Writes gehen an externe DB; Applikationsdaten bleiben in Haupt-DB

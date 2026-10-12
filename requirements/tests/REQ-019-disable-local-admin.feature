# Ableitung aus: requirements/req/req-019-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@business-rule @should @UC-02
Feature: REQ-019 – Deaktivierbarkeit des lokalen System-Admin-Accounts bei aktivem Remote-Mapping

  Sobald für eine Instanz mindestens ein funktionsfähiges
  Remote-Bootstrapping-Mapping (REQ-014) konfiguriert ist, SOLL der
  lokale System-Admin-Account (REQ-013) durch einen System-Admin
  gezielt deaktivierbar sein.

  @AC1
  Scenario: AC1 – ein aktiver lokaler System-Admin-Account und ein funktionsfähiges Remote...
    Given ein aktiver lokaler System-Admin-Account und ein funktionsfähiges Remote-Mapping
    When  ein System-Admin den lokalen Account deaktiviert
    Then  ist eine Anmeldung über den lokalen Account danach nicht mehr möglich, während der Remote-Weg weiter funktioniert

  @AC2
  Scenario: AC2 – kein funktionsfähiges Remote-Mapping konfiguriert
    Given kein funktionsfähiges Remote-Mapping konfiguriert
    When  versucht wird, den lokalen Account zu deaktivieren
    Then  warnt das System vor einem möglichen vollständigen Lockout, blockiert die Aktion aber nicht zwingend (Operator-Entscheidung)

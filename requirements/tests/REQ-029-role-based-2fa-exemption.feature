# Ableitung aus: requirements/req/req-029-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-01 @UC-03
Feature: REQ-029 – Rollen-basierte 2FA-Ausnahme durch Betreiber konfigurieren

  Ist die instanzweite 2FA-Erzwingung aktiv (REQ-020), MUSS der
  Betreiber zusätzlich konfigurieren können, welche OEA-Rollen von
  dieser Pflicht ausgenommen sind; Personen, deren aktive Rolle(n)
  vollständig in der Ausnahmeliste enthalten sind, MÜSSEN REQ-020 ohne
  zweiten Faktor erfüllen können. Der System-Admin-Account (UC-02) ist
  von dieser Konfiguration unabhängig – er ist immer ausgenommen
  (REQ-020).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – `enforce2FA=true` und `twoFactorExemptRoles=['administrator']`
    Given `enforce2FA=true` und `twoFactorExemptRoles=['administrator']`
    When  eine Person mit Rolle `administrator` sich mit Passwort anmeldet und kein 2FA-Credential hat
    Then  erhält sie direkt Zugriff, ohne UC-03 Required Action

  @AC2
  Scenario: AC2 – dieselbe Konfiguration
    Given dieselbe Konfiguration
    When  eine Person ohne Rolle `administrator` sich mit Passwort anmeldet und kein 2FA-Credential hat
    Then  wird UC-03 Required Action ausgelöst (Verhalten von REQ-020 unverändert)

  @AC3
  Scenario: AC3 – `twoFactorExemptRoles=['administrator']` und `enforce2FA=true`
    Given `twoFactorExemptRoles=['administrator']` und `enforce2FA=true`
    When  eine Person sowohl Rolle `administrator` als auch `editor` hat
    Then  ist die Person ausgenommen (mindestens eine ausgenommene Rolle genügt)

  @AC4
  Scenario: AC4 – `twoFactorExemptRoles` enthält eine nicht-existente Rollen-ID
    Given `twoFactorExemptRoles` enthält eine nicht-existente Rollen-ID
    When  der Betreiber speichern möchte
    Then  wird die Konfiguration abgelehnt mit konkreter Fehlermeldung (unbekannte Rollen-ID)

  @AC5
  Scenario: AC5 – System-Admin-Account aus UC-02 und beliebige `twoFactorExemptRoles`-Konf...
    Given System-Admin-Account aus UC-02 und beliebige `twoFactorExemptRoles`-Konfiguration
    When  System-Admin-Account sich anmeldet
    Then  ist er immer ausgenommen, unabhängig von dieser Konfiguration

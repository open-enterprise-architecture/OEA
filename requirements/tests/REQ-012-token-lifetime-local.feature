# Ableitung aus: requirements/req/req-012-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@data @should @UC-01
Feature: REQ-012 – Konfigurierbare Token-Lebensdauer bei lokaler Authentifizierung

  Bei lokaler Authentifizierung (Passkey, Username/Passwort, mit oder
  ohne TOTP – siehe REQ-009–REQ-011) MUSS die Gültigkeitsdauer von
  Access-Token (Authorization Token) und Refresh-Token instanzweit
  konfigurierbar sein.

  @AC1
  Scenario: AC1 – eine Instanz mit lokal konfigurierter Access-Token-Lebensdauer von 15 Mi...
    Given eine Instanz mit lokal konfigurierter Access-Token-Lebensdauer von 15 Minuten
    When  eine Person sich über einen lokalen Mechanismus (Passkey, Username/Passwort) anmeldet
    Then  läuft das Access-Token nach 15 Minuten ab

  @AC2
  Scenario: AC2 – ein abgelaufenes Access-Token, aber ein noch gültiges Refresh-Token aus ...
    Given ein abgelaufenes Access-Token, aber ein noch gültiges Refresh-Token aus lokaler Authentifizierung
    When  ein Refresh angefordert wird
    Then  wird ein neues Access-Token mit der konfigurierten Lebensdauer ausgestellt

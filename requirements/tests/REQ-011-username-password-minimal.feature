# Ableitung aus: requirements/req/req-011-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + inspection (Default-Konfiguration)

@functional @could @UC-01
Feature: REQ-011 – Username/Passwort-Login ohne zweiten Faktor (Minimal-Variante)

  Das System KANN Personen die Anmeldung mit ausschließlich
  Username/Passwort ohne zweiten Faktor ermöglichen; diese Variante
  MUSS auf Instanz-Ebene explizit aktiviert werden und DARF NICHT der
  Standardzustand einer neu installierten OEA-Instanz sein.

  Background:
    Given eine OEA-Instanz mit konfiguriertem Authentifizierungsmechanismus
    And eine Person mit E-Mail "test@example.com" und aktiver Rolle existiert

  @AC1
  Scenario: AC1 – eine Instanz, auf der diese Variante explizit aktiviert wurde, und eine ...
    Given eine Instanz, auf der diese Variante explizit aktiviert wurde, und eine Person mit korrektem Passwort
    When  sie sich anmeldet
    Then  erhält sie eine Session gemäß ihren aktiven Rollenzuweisungen

  @AC2
  Scenario: AC2 – eine neu installierte, nicht konfigurierte OEA-Instanz
    Given eine neu installierte, nicht konfigurierte OEA-Instanz
    When  ihr Authentifizierungs-Setup geprüft wird
    Then  ist diese Variante standardmäßig deaktiviert

  @AC3
  Scenario: AC3 – jeder erfolgreiche oder fehlgeschlagene Login über diese Variante
    Given jeder erfolgreiche oder fehlgeschlagene Login über diese Variante
    When  der Audit-Log-Eintrag erzeugt wird (siehe REQ-005)
    Then  ist im Eintrag erkennbar, dass kein zweiter Faktor verwendet wurde

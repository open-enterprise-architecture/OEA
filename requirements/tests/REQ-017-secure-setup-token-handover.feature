# Ableitung aus: requirements/req/req-017-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + inspection

@constraint @must @UC-02
Feature: REQ-017 – Sichere Übergabe des Setup-Tokens

  Das initiale Setup-Token bzw. das Aufforderungs-Interface zur
  Passwortvergabe DARF NICHT über einen Kanal ausgeliefert werden, der
  ohne vorherigen privilegierten Zugriff auf die Instanz (z.B.

  @AC1
  Scenario: AC1 – eine frisch installierte Instanz
    Given eine frisch installierte Instanz
    When  das Setup-Token generiert wird
    Then  ist es ausschließlich über einen Kanal abrufbar, der bereits privilegierten Zugriff voraussetzt (z.B. CLI-Ausgabe beim Start, lokale Logdatei, Container-Exec)

  @AC2
  Scenario: AC2 – das initiale Setup-Webinterface (falls UI-basiert)
    Given das initiale Setup-Webinterface (falls UI-basiert)
    When  es ohne gültiges Setup-Token aufgerufen wird
    Then  verweigert es die Passwortvergabe

# Ableitung aus: requirements/req/req-129-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: security-test (TLS-Scan via `testssl.sh` oder `sslyze`)

@non-functional @performance @must @UC-01 @UC-21
Feature: REQ-129 – Datenverschlüsselung (at-rest und in-transit)

  Das System MUSS alle gespeicherten Daten (at-rest) und alle
  Datentransporte (in-transit) kryptographisch schützen: **At-rest**: -
  Alle persistierten Daten (Datenbank, Backups, Export-Dateien im
  Storage) MÜSSEN mit **AES-256** oder einem gleichwertigen,
  anerkannten Verfahren verschlüsselt werden - Backups MÜSSEN
  verschlüsselt abgelegt werden; unverschlüsselte Backup-Dateien DÜRFEN
  nicht auf Datenträgern verbleiben - Verschlüsselungsschlüssel DÜRFEN
  NICHT hardcodiert sein; sie MÜSSEN über Umgebungsvariablen oder ein
  Secrets-Management-System konfigurierbar sein **In-transit**: - Alle
  HTTP-Verbindungen (Browser ↔ API, API ↔ Datenbank, API ↔ Object
  Storage) MÜSSEN über **TLS 1.2** oder höher abgewickelt werden -
  Unverschlüsselte HTTP-Verbindungen zum API-Server MÜSSEN auf HTTPS
  umgeleitet (301-Redirect) oder blockiert werden - TLS-Zertifikate
  MÜSSEN konfigurierbar sein (eigene Zertifikate, Let's Encrypt,
  Proxy-Terminierung)

  @AC1
  Scenario: AC1 – TLS erzwungen – Browser sendet HTTP-Anfrage an OEA-API (Port 80)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Browser sendet HTTP-Anfrage an OEA-API (Port 80)
    Then  Antwort ist 301-Redirect auf HTTPS oder Verbindung wird verweigert; kein Klartext-Response

  @AC2
  Scenario: AC2 – TLS-Version – TLS-Scan gegen den API-Endpunkt durchgeführt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  TLS-Scan gegen den API-Endpunkt durchgeführt wird
    Then  TLS 1.0 und TLS 1.1 werden abgelehnt; TLS 1.2 und 1.3 werden akzeptiert

  @AC3
  Scenario: AC3 – Backup verschlüsselt – Backup-Routine ausgeführt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Backup-Routine ausgeführt wird
    Then  Backup-Datei ist AES-256-verschlüsselt; kein Klartext-Dump auf dem Storage

  @AC4
  Scenario: AC4 – Keine hardcodierten Schlüssel – Statische Code-Analyse (Secrets-Scanner) gegen den Source Code läuft
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Statische Code-Analyse (Secrets-Scanner) gegen den Source Code läuft
    Then  Keine Trefffer für Verschlüsselungsschlüssel, Passwörter oder Tokens im Quellcode oder in Docker-Images

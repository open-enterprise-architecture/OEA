# Ableitung aus: requirements/req/req-075-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert für AC1, AC3–AC6) + manuell (AC2, AC7)

@non-functional @performance @must @UC-02
Feature: REQ-075 – Plattformunabhängigkeit und Cloud-Betreibbarkeit

  Das System MUSS als containerisierte Anwendung (Docker) auslieferbar
  sein, die ohne Anpassung auf Linux, macOS und Windows (Docker
  Desktop) sowie auf jeder CNCF-zertifizierten Kubernetes-Distribution
  betrieben werden kann. Das System DARF KEINE harten Abhängigkeiten
  auf proprietäre Cloud-Dienste eines einzelnen Anbieters haben — alle
  externen Dienste (Datenbank, Objektspeicher, Identity-Provider)
  MÜSSEN durch offene Standards oder anbieterübergreifende APIs
  austauschbar sein.

  @AC1
  Scenario: AC1 – Docker Compose auf Linux – `docker compose up` auf einem Linux-System (Ubuntu 22.04 LTS) ohne vorhe...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `docker compose up` auf einem Linux-System (Ubuntu 22.04 LTS) ohne vorherige Konfiguration ausgeführt wird
    Then  OEA-API, Datenbank und Auth-Service starten; `GET /health` antwortet HTTP 200 innerhalb von 60 Sekunden

  @AC2
  Scenario: AC2 – Docker Compose auf macOS und Windows – Wie AC1, auf macOS 14+ (Docker Desktop) und Windows 11 (Docker Desktop W...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then  identisches Ergebnis wie AC1; keine plattformspezifischen Anpassungen nötig

  @AC3
  Scenario: AC3 – Kubernetes / Kind-Cluster – Helm Chart für OEA; lokaler Kind-Cluster
    Given Helm Chart für OEA; lokaler Kind-Cluster
    When  `helm install oea ./chart` ausgeführt
    Then  alle Pods `Running` innerhalb von 120 Sekunden; `GET /health` HTTP 200

  @AC4
  Scenario: AC4 – PostgreSQL ohne proprietäre Extensions – vanilla PostgreSQL 15 (kein AWS Aurora, kein Azure Flexible Server, kein...
    Given vanilla PostgreSQL 15 (kein AWS Aurora, kein Azure Flexible Server, kein Cloud SQL)
    When  OEA mit dieser Datenbank verbunden
    Then  alle Funktionen arbeiten korrekt; keine Extension ausser `uuid-ossp` (falls benötigt) oder keine

  @AC5
  Scenario: AC5 – MinIO statt AWS S3 – MinIO-Instanz als Objektspeicher konfiguriert (S3-kompatible API)
    Given MinIO-Instanz als Objektspeicher konfiguriert (S3-kompatible API)
    When  OEA Dateien/Blobs schreibt und liest
    Then  korrekte Funktion; kein AWS-SDK-spezifischer Code-Pfad

  @AC6
  Scenario: AC6 – Keycloak statt kommerziellem IdP – Keycloak als OIDC-Provider konfiguriert
    Given Keycloak als OIDC-Provider konfiguriert
    When  Nutzer Login via OIDC
    Then  Login erfolgreich; Rollen korrekt übertragen; kein Entra-ID-spezifisches API verwendet

  @AC7
  Scenario: AC7 – Keine proprietären Umgebungsvariablen als Pflicht – OEA ohne AWS_ACCESS_KEY, AZURE_CLIENT_ID o.ä. Cloud-spezifische Env-Vari...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  OEA ohne AWS_ACCESS_KEY, AZURE_CLIENT_ID o.ä. Cloud-spezifische Env-Variablen gestartet
    Then  System startet (mit S3-kompatibler Alternative konfiguriert); keine Exception

# Ableitung aus: requirements/req/req-150-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + demonstration

@constraint @must @UC-02
Feature: REQ-150 – Multi-Datenbank-Unterstützung

  Das System **MUSS** mit PostgreSQL 14+, MySQL 8+, MariaDB 10.6+ und
  SQL Server 2019+ betrieben werden können, ohne Änderungen am
  Applikationscode; der Datenbanktyp wird ausschliesslich über
  Konfiguration gewählt.

  @AC1
  Scenario: AC1 – OEA-Docker-Image mit Env-Variable `SPRING_DATASOURCE_URL=jdbc:mysql://...`
    Given OEA-Docker-Image mit Env-Variable `SPRING_DATASOURCE_URL=jdbc:mysql://...`
    When  `docker compose up`
    Then  Flyway-Migrations laufen durch; API antwortet korrekt; alle CRUD-Operationen funktionieren

  @AC2
  Scenario: AC2 – Testumgebung mit H2 in-memory (CI)
    Given Testumgebung mit H2 in-memory (CI)
    When  Unit- und Integration-Tests laufen
    Then  Alle Tests grün ohne PostgreSQL-Instanz

  @AC3
  Scenario: AC3 – Schema-Migration V1 in SQL
    Given Schema-Migration V1 in SQL
    When  Flyway auf SQL Server 2019 ausführen
    Then  Keine syntax-spezifischen Fehler; alle Tabellen angelegt

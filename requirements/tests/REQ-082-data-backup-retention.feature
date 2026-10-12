# Ableitung aus: requirements/req/req-082-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: inspection (Dokumentation) + test (Restore-Drill)

@non-functional @performance @must @UC-02
Feature: REQ-082 – Datensicherung und Point-in-Time-Recovery

  Das System MUSS sicherstellen, dass PostgreSQL-Daten (alle
  ArchitectureEntities, Metamodell-Konfiguration, Audit-Events) täglich
  gesichert werden und innerhalb eines Recovery-Time-Objectives von **4
  Stunden** wiederhergestellt werden können. Das
  Recovery-Point-Objective (Datenverlust-Fenster) DARF **24 Stunden**
  nicht überschreiten.

  @AC1
  Scenario: AC1 – Backup-Prozedur dokumentiert – frische OEA-Installation via `docker compose up`
    Given frische OEA-Installation via `docker compose up`
    When  Betreiber die Dokumentation liest
    Then  Schritt-für-Schritt-Anleitung für `pg_dump`-basiertes Backup und Restore vorhanden; Zeitaufwand < 30 Minuten für Setup

  @AC2
  Scenario: AC2 – Restore-Test – Backup einer produktiven Instanz mit 10.000 Entitäten
    Given Backup einer produktiven Instanz mit 10.000 Entitäten
    When  Restore auf leere PostgreSQL-Instanz via dokumentierter Prozedur
    Then  Alle Entitäten, Metamodell-Konfiguration und Audit-Events vollständig vorhanden; API antwortet korrekt innerhalb von 4 Stunden

  @AC3
  Scenario: AC3 – RPO-Nachweis – tägliches Backup um 02:00 Uhr; Datenbankausfall um 23:59 Uhr
    Given tägliches Backup um 02:00 Uhr; Datenbankausfall um 23:59 Uhr
    When  Restore aus letztem Backup
    Then  Datenverlust ≤ 22 Stunden (< 24h RPO)

  @AC4
  Scenario: AC4 – Aufbewahrung – tägliche Backups über 31 Tage
    Given tägliche Backups über 31 Tage
    When  Betreiber prüft Backup-Archiv
    Then  mind. 30 Backup-Generationen vorhanden; ältestes Backup nicht automatisch gelöscht

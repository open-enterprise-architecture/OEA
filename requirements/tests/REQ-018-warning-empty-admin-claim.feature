# Ableitung aus: requirements/req/req-018-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-02
Feature: REQ-018 – Warnung bei leerem oder nicht-existierendem Remote-Admin-Claim

  Das System SOLL beim Konfigurieren eines
  Remote-Bootstrapping-Mappings (REQ-014) warnen, wenn die angegebene
  Gruppe/Rolle beim Identity-Provider zum Konfigurationszeitpunkt
  prüfbar nicht existiert oder keine Mitglieder hat.

  Background:
    Given eine frisch installierte OEA-Instanz ohne System-Admin-Account

  @AC1
  Scenario: AC1 – ein Remote-Mapping auf eine zum Konfigurationszeitpunkt leere oder nicht...
    Given ein Remote-Mapping auf eine zum Konfigurationszeitpunkt leere oder nicht existierende Gruppe
    When  Max die Konfiguration speichert
    Then  erhält er eine Warnung, dass dieses Mapping aktuell niemandem Zugriff verleiht

  @AC2
  Scenario: AC2 – dieselbe Situation
    Given dieselbe Situation
    When  Max die Warnung zur Kenntnis nimmt
    Then  kann er die Konfiguration trotzdem speichern (kein Hard-Block), idealerweise mit aktivem lokalem Fallback (REQ-013)

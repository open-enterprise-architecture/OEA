# Ableitung aus: requirements/req/req-089-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-11
Feature: REQ-089 – Realized-Plateau schreibgeschützt

  Plateaus mit `status=realized` MÜSSEN für alle Schreib-Operationen
  gesperrt sein (read-only). Versuche, Name, Beschreibung, `validFrom`
  oder verknüpfte Solutions eines realized-Plateaus zu ändern, MÜSSEN
  mit HTTP 422 abgewiesen werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – API-Schreibschutz – ein PUT-Request an `/plateaus/{id}` für ein realized-Plateau gesendet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein PUT-Request an `/plateaus/{id}` für ein realized-Plateau gesendet wird
    Then  antwortet die API mit HTTP 422 "Realisiertes Plateau ist read-only"

  @AC2
  Scenario: AC2 – UI-Schreibschutz – ein realized-Plateau in der Übersicht angezeigt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein realized-Plateau in der Übersicht angezeigt wird
    Then  ist keine Bearbeiten-Schaltfläche sichtbar

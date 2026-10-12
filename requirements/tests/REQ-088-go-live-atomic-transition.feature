# Ableitung aus: requirements/req/req-088-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-11
Feature: REQ-088 – Go-Live als atomare DB-Transaktion

  Die Go-Live-Transition MUSS als atomare DB-Transaktion ausgeführt
  werden und folgende Änderungen umfassen: (1) P1: `status→baseline`,
  `validFrom=jetzt(UTC)`; (2) P0: `status→realized`,
  `validTo=jetzt(UTC)`, read-only; (3) alle Entitäten in P1 mit
  `lifecycle_state=retiring` → `lifecycle_state=retired`. Bei
  technischem Fehler MUSS ein vollständiger Rollback erfolgen; kein
  Inkonsistenz-Zustand ist zulässig.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Rollback bei DB-Fehler – ein simulierter DB-Fehler nach dem P1-Update auftritt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein simulierter DB-Fehler nach dem P1-Update auftritt
    Then  erfolgt ein vollständiger Rollback; P0 und P1 sind unverändert

  @AC2
  Scenario: AC2 – Erfolgreicher Go-Live – Go-Live erfolgreich ausgeführt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Go-Live erfolgreich ausgeführt wird
    Then  hat P1 `status=baseline`, P0 hat `status=realized`; alle `retiring`-Entitäten in P1 haben `lifecycle_state=retired`; alle Zustände sind konsistent

# Ableitung aus: requirements/req/req-152-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@interface @must @UC-04 @UC-06
Feature: REQ-152 – Dynamische Labels mit ETag-Caching

  Das System **MUSS** konfigurierbare Labels (MetaTyp-Namen,
  Property-Labels, Enum-Werte) via `GET /api/v1/i18n/{locale}`
  bereitstellen und **MUSS** ETag-basiertes Caching unterstützen,
  sodass unveränderte Label-Sets mit HTTP 304 beantwortet werden.

  @AC1
  Scenario: AC1 – Label-Set für `de` nicht verändert seit letztem Request
    Given Label-Set für `de` nicht verändert seit letztem Request
    When  `GET /api/v1/i18n/de` mit `If-None-Match: "<etag>"`
    Then  304 Not Modified; kein Response-Body

  @AC2
  Scenario: AC2 – Admin ändert MetaTyp-Namen im Metamodell-Editor
    Given Admin ändert MetaTyp-Namen im Metamodell-Editor
    When  `GET /api/v1/i18n/de` ohne If-None-Match
    Then  200 OK; neuer ETag; Bundle enthält aktualisierten Label

  @AC3
  Scenario: AC3 – SSE, optional – Client hat `GET /api/v1/events/stream` offen
    Given Client hat `GET /api/v1/events/stream` offen
    When  Admin speichert Label-Änderung
    Then  SSE-Event `{"type":"i18n_invalidated","locale":"de"}` empfangen innerhalb 2s

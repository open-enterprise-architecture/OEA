# Ableitung aus: requirements/req/req-153-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-19
Feature: REQ-153 – Entitäten per external_id idempotent anlegen und aktualisieren

  Das System **MUSS** es ermöglichen, eine Entität unter einer
  stabilen, vom aufrufenden System vergebenen Kennung (`external_id`)
  anzulegen oder zu aktualisieren, sodass wiederholte Aufrufe mit
  identischen Daten keine doppelten Einträge erzeugen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Keine Entität mit `external_id = "git:acme/order-service"`
    Given Keine Entität mit `external_id = "git:acme/order-service"`
    When  `PUT /api/v1/entities/by-external-id/git%3Aacme%2Forder-service` mit gültigem Body
    Then  201 Created; Entität in DB; `external_id` gespeichert

  @AC2
  Scenario: AC2 – Entität mit `external_id = "git:acme/order-service"` existiert bereits
    Given Entität mit `external_id = "git:acme/order-service"` existiert bereits
    When  Gleicher Aufruf mit geändertem `name`
    Then  200 OK; `name` aktualisiert; keine zweite Entität angelegt

  @AC3
  Scenario: AC3 – Zwei Aufrufe mit identischen Daten in Folge
    Given Zwei Aufrufe mit identischen Daten in Folge
    When  Zweiter Aufruf
    Then  200 OK; Daten unverändert; Versionszähler nicht erhöht (kein no-op Write)

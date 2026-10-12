# Ableitung aus: requirements/req/req-147-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@interface @must @UC-04 @UC-05 @UC-06 @UC-11 @UC-14 @UC-17 @UC-18
Feature: REQ-147 – API-Vollständigkeit und Automations-Tauglichkeit (API-First)

  Jede modellverändernde Operation, die über die OEA-Benutzeroberfläche
  ausführbar ist, MUSS gleichwertig über einen dokumentierten,
  versionierten REST-Endpoint erreichbar sein. Der Endpoint MUSS so
  gestaltet sein, dass er von externen Systemen (CI/CD-Pipelines,
  Migrationsskripte, ITSM-Konnektoren, andere EA-Werkzeuge) ohne
  Kenntnis der UI konsumierbar ist.

  @AC1
  Scenario: AC1 – API-Vollständigkeit – Ein neues UI-Feature wird als *done* markiert
    Given Ein neues UI-Feature wird als *done* markiert
    When die beschriebene Aktion ausgeführt wird
    Then  Für jede zustandsverändernde Aktion des Features existiert ein dokumentierter Endpoint in der OpenAPI-Spec (`/api/docs`)

  @AC2
  Scenario: AC2 – Bulk-Import – `POST /api/v1/entities/bulk` mit 500 ApplicationComponent-Objekten aufge...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `POST /api/v1/entities/bulk` mit 500 ApplicationComponent-Objekten aufgerufen wird
    Then  Alle 500 Objekte werden angelegt oder eine atomare Rollback-Antwort zurückgegeben; Antwortzeit < 10 s; kein Timeout

  @AC3
  Scenario: AC3 – Idempotenz – `PUT /api/v1/entities/{id}` mit identischem Body zweimal aufgerufen wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `PUT /api/v1/entities/{id}` mit identischem Body zweimal aufgerufen wird
    And   Ein Endpoint nicht idempotent sein kann: er ist in der OpenAPI-Spec als `x-idempotent: false` dokumentiert
    Then  Zweiter Aufruf gibt 200 zurück ohne doppelten Eintrag im Audit-Log

  @AC4
  Scenario: AC4 – Keine UI-Kopplung – Ein externer Client alle Entitäten eines Katalogs abruft und modifiziert...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ein externer Client alle Entitäten eines Katalogs abruft und modifiziert, ohne je die UI zu laden
    Then  Alle Operationen (lesen, anlegen, aktualisieren, löschen, abfragen) sind vollständig via API durchführbar; kein Endpoint erfordert einen Browser-Session-Cookie

  @AC5
  Scenario: AC5 – Webhook-Notification – Eine Entität via `PUT /api/v1/entities/{id}` aktualisiert wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Eine Entität via `PUT /api/v1/entities/{id}` aktualisiert wird
    Then  Alle registrierten Webhooks für Event `entity.updated` erhalten innerhalb von 5 s einen HTTP-POST mit korrekter HMAC-Signatur

  @AC6
  Scenario: AC6 – Deprecation-Policy – Ein Endpoint in einer neuen API-Version entfernt oder inkompatibel geänd...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ein Endpoint in einer neuen API-Version entfernt oder inkompatibel geändert wird
    Then  Der alte Endpoint ist mind. 6 Monate nach Ankündigung noch erreichbar und antwortet mit `Deprecation`- und `Sunset`-Headern (RFC 8594)

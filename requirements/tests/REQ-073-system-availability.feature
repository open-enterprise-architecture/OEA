# Ableitung aus: requirements/req/req-073-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: monitoring (Produktivbetrieb)

@non-functional @performance @must @UC-01 @UC-05 @UC-06
Feature: REQ-073 – Systemverfügbarkeit (Uptime-SLA)

  Das System MUSS für produktive OEA-Instanzen eine
  Mindestverfügbarkeit von **99,5 %** pro Kalendermonat einhalten,
  gemessen am API-Endpunkt `GET /health`. Geplante Wartungsfenster
  (maximal 4 Stunden pro Monat, angekündigt ≥ 24 Stunden im Voraus)
  werden von der Berechnung ausgenommen.

  @AC1
  Scenario: AC1 – Uptime-Monitoring – Produktive OEA-Instanz läuft seit 30 Tagen
    Given Produktive OEA-Instanz läuft seit 30 Tagen
    When  Monitoring-Tool prüft `GET /health` alle 60 Sekunden
    Then  Verfügbarkeit ≥ 99,5 % des Messzeitraums (exkl. angekündigte Wartungsfenster)

  @AC2
  Scenario: AC2 – Automatischer Neustart – OEA-API-Prozess crasht unerwartet
    Given OEA-API-Prozess crasht unerwartet
    When  Container-Orchestrierung reagiert (Kubernetes/Docker Compose mit restart-policy)
    Then  `/health` antwortet spätestens nach 30 Minuten wieder mit HTTP 200

  @AC3
  Scenario: AC3 – Health-Endpunkt – `GET /health` aufgerufen
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `GET /health` aufgerufen
    Then  HTTP 200 bei voller Funktionalität; HTTP 503 bei Datenbank-Nichterreichbarkeit oder kritischem Fehler; Antwortzeit < 100ms

# Ableitung aus: requirements/req/req-042-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-05
Feature: REQ-042 – Konflikt-Warnung bei parallelen EntityDeltas

  Das System SOLL beim Erfassen oder Aktualisieren eines EntityDeltas
  prüfen, ob eine andere aktive Solution (status ∉ {`implemented`,
  `archived`}) bereits ein EntityDelta für dieselbe Entität enthält;
  falls ja, SOLL eine nicht-blockierende Warnung mit Name und Owner der
  konfliktbehafteten Solution angezeigt werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Solution A (draft, Owner: Maria) enthält ein Delta für Entity `CRM-Legacy`
    Given Solution A (draft, Owner: Maria) enthält ein Delta für Entity `CRM-Legacy`
    When  Michael in einer anderen Solution B ein Delta für `CRM-Legacy` anlegt
    Then  erscheint eine Warnung „Diese Entität wird auch in Solution 'Solution A' (Owner: Maria) geändert"; das Delta in Solution B wird trotzdem gespeichert

  @AC2
  Scenario: AC2 – Solution A (implemented) enthält ein Delta für Entity `CRM-Legacy`
    Given Solution A (implemented) enthält ein Delta für Entity `CRM-Legacy`
    When  Michael in Solution B ein Delta für `CRM-Legacy` anlegt
    Then  **keine** Warnung (implemented ist kein aktiver Konflikt)

  @AC3
  Scenario: AC3 – Solution A (archived) enthält ein Delta für Entity `CRM-Legacy`
    Given Solution A (archived) enthält ein Delta für Entity `CRM-Legacy`
    When  Michael in Solution B ein Delta für `CRM-Legacy` anlegt
    Then  **keine** Warnung (archived ist kein aktiver Konflikt)

  @AC4
  Scenario: AC4 – Zwei andere Solutions (A und B) haben jeweils ein Delta für `CRM-Legacy`
    Given Zwei andere Solutions (A und B) haben jeweils ein Delta für `CRM-Legacy`
    When  Michael in Solution C ein Delta für `CRM-Legacy` anlegt
    Then  erscheinen zwei Warnungen (eine pro konfliktbehafteter Solution)

  @AC5
  Scenario: AC5 – Warnung wurde für `CRM-Legacy` in Solution B angezeigt
    Given Warnung wurde für `CRM-Legacy` in Solution B angezeigt
    When  Michael die Solution B neu lädt oder die Delta-Liste öffnet
    Then  ist die Warnung weiterhin sichtbar (kein einmaliges Toast, persistente Anzeige am Delta)

  @AC6
  Scenario: AC6 – Solution A wird auf `implemented` gesetzt (Go-Live)
    Given Solution A wird auf `implemented` gesetzt (Go-Live)
    When  Michael Solution B mit dem Delta für `CRM-Legacy` öffnet
    Then  verschwindet die Warnung (Konflikt aufgelöst durch Go-Live von A)

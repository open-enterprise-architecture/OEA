# Ableitung aus: requirements/req/req-131-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: load-test (k6 oder Gatling; synthetischer Graph mit 500k Entities, 500 Scope-Connections pro Nutzer)

@non-functional @performance @must @UC-21
Feature: REQ-131 – Graph-Traversal-Latenz (ABAC connection-scoped)

  Der Graph-Traversal für die `connection-scoped`-Sichtbarkeitsprüfung
  (REQ-127) DARF die Antwortzeit einer Entity-Abfrage um maximal **200
  ms** (p95) erhöhen, gemessen als zusätzliche Latenz gegenüber einer
  identischen Abfrage ohne `connection-scoped`-Properties. Dies gilt
  für Entitäten mit bis zu **500 direkt verbundenen Scope-Entitäten**
  pro Nutzer über den konfigurierten `scopingConnectionType`.

  @AC1
  Scenario: AC1 – Kalt-Traversal ≤ 200 ms – Nutzer hat 500 direkt verbundene Scope-Entitäten; Cache leer
    Given Nutzer hat 500 direkt verbundene Scope-Entitäten; Cache leer
    When  Entity-Abfrage mit einem `connection-scoped` Property ausgeführt
    Then  p95-Latenz des Traversal-Anteils ≤ 200 ms (gemessen via APM-Trace)

  @AC2
  Scenario: AC2 – Warm-Cache ≤ 10 ms – Traversal-Ergebnis ist im Cache (TTL noch nicht abgelaufen)
    Given Traversal-Ergebnis ist im Cache (TTL noch nicht abgelaufen)
    When  Weitere Entity-Abfrage desselben Nutzers ausgeführt
    Then  Traversal-Anteil ≤ 10 ms (Cache-Lookup)

  @AC3
  Scenario: AC3 – Cache-Invalidierung – Nutzer hat gecachtes Traversal-Ergebnis
    Given Nutzer hat gecachtes Traversal-Ergebnis
    When  Neue `DomainAssignment`-Connection für diesen Nutzer angelegt wird
    Then  Spätestens nach 1 Sekunde liefert die nächste Entity-Abfrage das aktualisierte Traversal-Ergebnis

  @AC4
  Scenario: AC4 – Lasttest – 50 gleichzeitige Nutzer (REQ-074) mit je 500 Scope-Verbindungen
    Given 50 gleichzeitige Nutzer (REQ-074) mit je 500 Scope-Verbindungen
    When  Alle Nutzer gleichzeitig Entity-Abfragen mit connection-scoped Properties senden
    Then  p95-Latenz (gesamt, inkl. Traversal) ≤ REQ-071-Zielwert + 200 ms

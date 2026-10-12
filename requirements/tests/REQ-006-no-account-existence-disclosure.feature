# Ableitung aus: requirements/req/req-006-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@constraint @must @UC-01
Feature: REQ-006 – Keine Preisgabe der Account-Existenz bei fehlgeschlagenem Login

  Das System DARF NICHT durch unterschiedliche Fehlermeldungen oder
  Antwortzeiten erkennbar machen, ob eine angefragte Identität
  (E-Mail-Adresse, Subject-ID) im Repository existiert oder nicht.

  @AC1
  Scenario: AC1 – ein Login-Versuch mit nicht-existierender Identität
    Given ein Login-Versuch mit nicht-existierender Identität
    When  der Login fehlschlägt
    Then  ist die nach außen sichtbare Fehlermeldung identisch zu der bei einer existierenden, aber falsch authentifizierten Identität

  @AC2
  Scenario: AC2 – zwei Login-Fehlversuche, einmal mit existierender, einmal mit nicht-exis...
    Given zwei Login-Fehlversuche, einmal mit existierender, einmal mit nicht-existierender Identität
    When  die Antwortzeiten verglichen werden
    Then  unterscheiden sie sich nicht in einer für Angreifer auswertbaren Weise (kein Timing-Seitenkanal)

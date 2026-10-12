# Ableitung aus: requirements/req/req-096-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-13
Feature: REQ-096 – Navigationsbaum Drag & Drop mit Zyklus-Schutz

  Das System MUSS das Neuanordnen von Items (`sortOrder`) und das
  Verschieben von Ordnern und Items per Drag & Drop unterstützen. Beim
  Verschieben eines Ordners MUSS das System Zyklen erkennen (BR-03:
  Ordner darf nicht in einen eigenen Nachkommen verschoben werden) und
  das Drop-Target visuell als ungültig markieren; API-Versuche mit
  Zyklus MÜSSEN mit HTTP 422 abgewiesen werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Zyklus-Erkennung – Ordner A per Drag & Drop unter Ordner B gezogen wird und B ein Nachkomme...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ordner A per Drag & Drop unter Ordner B gezogen wird und B ein Nachkomme von A ist
    Then  wird das Drop-Target visuell als ungültig markiert; ein API-Versuch antwortet mit HTTP 422

  @AC2
  Scenario: AC2 – Neuanordnung von Items – Items innerhalb eines Ordners per Drag & Drop neu angeordnet werden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Items innerhalb eines Ordners per Drag & Drop neu angeordnet werden
    Then  wird `sortOrder` korrekt aktualisiert; die neue Reihenfolge ist persistent

  @AC3
  Scenario: AC3 – Item verschieben zwischen Ordnern – ein Item per Drag & Drop von Ordner A nach Ordner B verschoben wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Item per Drag & Drop von Ordner A nach Ordner B verschoben wird
    Then  ist das Item in B vorhanden und nicht mehr in A

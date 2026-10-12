# Ableitung aus: requirements/req/req-094-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-13
Feature: REQ-094 – Navigationsbaum Ordner-CRUD

  Das System MUSS das Anlegen, Umbenennen und Löschen von
  TreeNode-Ordnern im Navigationsbaum ermöglichen. Ordnernamen MÜSSEN
  unter Geschwistern eindeutig sein (BR-01).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Ordner anlegen – ein neuer Ordner unter einem Parent angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein neuer Ordner unter einem Parent angelegt wird
    Then  antwortet die API mit HTTP 201; der Ordner ist im Baum sichtbar

  @AC2
  Scenario: AC2 – Doppelter Name abweisen – ein Ordner mit einem unter demselben Parent bereits vorhandenen Namen an...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Ordner mit einem unter demselben Parent bereits vorhandenen Namen angelegt wird
    Then  antwortet die API mit HTTP 422 "Name bereits vorhanden"

  @AC3
  Scenario: AC3 – Rekursives Löschen – ein Ordner mit 3 Kind-Ordnern und 10 Items gelöscht wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Ordner mit 3 Kind-Ordnern und 10 Items gelöscht wird
    Then  werden Kind-Ordner und Items entfernt; referenzierte Entitäten bleiben unberührt

  @AC4
  Scenario: AC4 – Wurzelknoten-Schutz – ein DELETE-Request an `/tree-nodes/root` gesendet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein DELETE-Request an `/tree-nodes/root` gesendet wird
    Then  antwortet die API mit HTTP 422 "Wurzelknoten kann nicht gelöscht werden"

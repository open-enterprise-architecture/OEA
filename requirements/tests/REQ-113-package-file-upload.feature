# Ableitung aus: requirements/req/req-113-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-18
Feature: REQ-113 – Continuum-Paket per Datei-Upload importieren

  Das System MUSS den Upload von Continuum-Paketen als `.json`- oder
  `.yaml`-Datei ermöglichen. Nach dem Upload MUSS das System das Paket
  sofort gegen das OEA-Continuum-Paket-Schema validieren und bei
  Schema-Verletzungen HTTP 422 mit einer Fehlerliste (Zeile, Feld,
  Fehlerbeschreibung) zurückgeben — ohne einen einzigen Baustein zu
  importieren.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Valide Datei – Vorschau – eine valide Paket-Datei hochgeladen wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine valide Paket-Datei hochgeladen wird
    Then  zeigt die UI eine Vorschau mit Anzahl und Art der enthaltenen Bausteine

  @AC2
  Scenario: AC2 – Schema-Verletzung – eine Datei mit fehlendem Pflichtfeld hochgeladen wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Datei mit fehlendem Pflichtfeld hochgeladen wird
    Then  antwortet das System mit HTTP 422 und einer Fehlerliste (Zeile, Feld, Fehlerbeschreibung); kein Baustein wird importiert

  @AC3
  Scenario: AC3 – Import nach Bestätigung – die Vorschau bestätigt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Vorschau bestätigt wird
    Then  wird der Import ausgeführt; alle importierten Bausteine haben `scope=imported`

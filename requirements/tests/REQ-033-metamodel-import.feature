# Ableitung aus: requirements/req/req-033-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-04
Feature: REQ-033 – Metamodell aus Datei importieren

  Das System MUSS Personen mit Metamodell-Bearbeiter-Berechtigung
  ermöglichen, eine Metamodell-Konfigurationsdatei (YAML oder JSON)
  hochzuladen; das System MUSS die Datei vor dem Import validieren,
  eine Vorschau der Änderungen (Diff: neue Typen, Konflikte,
  unveränderte Typen) anzeigen und erst nach expliziter Bestätigung
  importieren.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – eine gültige YAML-Datei mit 3 neuen Custom EntityTypes
    Given eine gültige YAML-Datei mit 3 neuen Custom EntityTypes
    When  Kurt die Datei hochlädt und die Vorschau bestätigt
    Then  sind alle 3 Typen in der MetamodelConfiguration vorhanden; Audit-Log enthält 3 Einträge

  @AC2
  Scenario: AC2 – eine YAML-Datei, die einen Typ mit dem Namen `ApplicationComponent` (bui...
    Given eine YAML-Datei, die einen Typ mit dem Namen `ApplicationComponent` (built-in) definiert
    When  Kurt die Datei hochlädt
    Then  zeigt die Vorschau diesen Eintrag als rot/Konflikt an mit „nicht importierbar (built-in)"; er kann nicht bestätigt werden

  @AC3
  Scenario: AC3 – eine syntaktisch ungültige YAML-Datei
    Given eine syntaktisch ungültige YAML-Datei
    When  Kurt sie hochlädt
    Then  erscheint sofort eine Fehlermeldung mit Zeilen- und Spaltenangabe; keine Vorschau

  @AC4
  Scenario: AC4 – eine gültige Datei mit 2 neuen und 1 konfliktbehaftetem (überschreibbare...
    Given eine gültige Datei mit 2 neuen und 1 konfliktbehaftetem (überschreibbarem Custom-Typ)
    When  Kurt nur die 2 neuen Typen bestätigt und den Konflikt überspringt
    Then  werden nur die 2 neuen importiert; der bestehende Custom-Typ bleibt unverändert

  @AC5
  Scenario: AC5 – eine JSON-Datei mit identischer Struktur wie die YAML-Variante
    Given eine JSON-Datei mit identischer Struktur wie die YAML-Variante
    When  Kurt sie hochlädt
    Then  wird sie genauso behandelt (Format auto-detektiert)

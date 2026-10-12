# Ableitung aus: requirements/req/req-032-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + exploration (UI-Test)

@functional @must @UC-04
Feature: REQ-032 – Custom EntityTypes via GUI konfigurieren

  Das System MUSS Personen mit Metamodell-Bearbeiter-Berechtigung
  ermöglichen, Custom EntityTypes (inkl. Properties, Relationen,
  optionalem Basis-Typ und Stereotypen) über ein grafisches Formular zu
  erstellen, zu bearbeiten und zu löschen; Built-in EntityTypes MÜSSEN
  sichtbar, aber NICHT editierbar oder löschbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Kurt hat Metamodell-Bearbeiter-Berechtigung
    Given Kurt hat Metamodell-Bearbeiter-Berechtigung
    When  er einen neuen EntityType `SecurityZone` mit Property `trustLevel` (enum, pflicht) und Relation `contains → TechnologyComponent (0..n)` über das Formular anlegt
    Then  erscheint `SecurityZone` in der Typ-Liste; beim Anlegen einer neuen Entität ist `SecurityZone` auswählbar

  @AC2
  Scenario: AC2 – Kurt versucht, einen EntityType mit dem Namen `ApplicationComponent` (bu...
    Given Kurt versucht, einen EntityType mit dem Namen `ApplicationComponent` (built-in) anzulegen
    When  er speichert
    Then  erscheint eine Fehlermeldung „Name 'ApplicationComponent' bereits vergeben (built-in)"; kein Eintrag wird angelegt

  @AC3
  Scenario: AC3 – Custom EntityType `SecurityZone` hat 5 Entitäts-Instanzen im Repository
    Given Custom EntityType `SecurityZone` hat 5 Entitäts-Instanzen im Repository
    When  Kurt versucht, `SecurityZone` zu löschen
    Then  erscheint eine Fehlermeldung mit „5 Entitäten vom Typ SecurityZone müssen zuerst gelöscht oder migriert werden"; kein Löschen

  @AC4
  Scenario: AC4 – Kurt bearbeitet einen Custom EntityType (fügt ein optionales Property hi...
    Given Kurt bearbeitet einen Custom EntityType (fügt ein optionales Property hinzu)
    When  er speichert
    Then  ist das neue Property sichtbar; bestehende Entitäten dieses Typs werden nicht invalide (optionales Property, kein Breaking Change)

  @AC5
  Scenario: AC5 – Built-in EntityType `ApplicationComponent` in der Liste
    Given Built-in EntityType `ApplicationComponent` in der Liste
    When  Kurt darauf klickt
    Then  wird er als schreibgeschützt dargestellt; keine Edit- oder Delete-Buttons

# Ableitung aus: requirements/req/req-037-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-04
Feature: REQ-037 – Solution-spezifische Metamodell-Erweiterung

  Das System MUSS es ermöglichen, für eine
  [Solution](../../business-objects/solution.md) eine eigene
  `MetamodelConfiguration` mit `scope=solution` anzulegen, die das
  Instanz-Standardmetamodell erbt und um zusätzliche EntityTypes,
  Stereotypen und Constraint-Regeln erweitert; die Solution-Erweiterung
  MUSS einen eigenen `editMode` haben, der unabhängig vom `editMode`
  der Instanz-Konfiguration konfiguriert ist; Solution-eigene Typen
  DÜRFEN Instanz-Typen weder überschreiben noch entfernen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Instanz-Metamodell mit `editMode=import-only`; Solution „Cloud-Experimen...
    Given Instanz-Metamodell mit `editMode=import-only`; Solution „Cloud-Experiment" ohne Erweiterungs-Konfiguration
    When  Kurt für „Cloud-Experiment" eine neue Erweiterungs-Konfiguration mit `editMode=gui-and-import` anlegt und darin `CloudService` definiert
    Then  ist `CloudService` im effektiven Metamodell von „Cloud-Experiment" verfügbar; das Instanz-Standardmetamodell bleibt unverändert und gesperrt; `CloudService` erscheint nicht in anderen Solutions

  @AC2
  Scenario: AC2 – Solution „Cloud-Experiment" mit `CloudService` (Solution-Typ) und Instan...
    Given Solution „Cloud-Experiment" mit `CloudService` (Solution-Typ) und Instanz-Typ `ApplicationComponent`
    When  ein Architekt eine neue Entität innerhalb „Cloud-Experiment" anlegt
    Then  stehen beide Typen zur Auswahl: `ApplicationComponent` (aus Instanz) und `CloudService` (Solution-eigen)

  @AC3
  Scenario: AC3 – Solution-Erweiterung mit `editMode=gui-and-import`
    Given Solution-Erweiterung mit `editMode=gui-and-import`
    When  Kurt über die GUI einen neuen Typ hinzufügt
    Then  ist das möglich, obwohl das Instanz-Metamodell `import-only` ist (unabhängige `editMode`-Ebenen)

  @AC4
  Scenario: AC4 – Solution-Erweiterungs-Konfiguration soll einen Typ `ApplicationComponent...
    Given Solution-Erweiterungs-Konfiguration soll einen Typ `ApplicationComponent` definieren (bereits als Instanz-Typ vorhanden)
    When  Kurt speichert
    Then  erscheint Fehler "Typ 'ApplicationComponent' ist im Instanz-Metamodell bereits definiert"; kein Eintrag angelegt

  @AC5
  Scenario: AC5 – Solution hat keine Erweiterungs-Konfiguration (`metamodelExtensionId=null`)
    Given Solution hat keine Erweiterungs-Konfiguration (`metamodelExtensionId=null`)
    When  ein Architekt innerhalb dieser Solution eine Entität anlegt
    Then  stehen ausschliesslich Instanz-Typen zur Verfügung; kein Fehler

  @AC6
  Scenario: AC6 – Solution-Erweiterung mit `editMode=import-only`
    Given Solution-Erweiterung mit `editMode=import-only`
    When  Kurt die Solution-Erweiterung über eine YAML-Datei aktualisiert (REQ-033)
    Then  ist der Import möglich; GUI-Bearbeitung dieser Erweiterung ist blockiert

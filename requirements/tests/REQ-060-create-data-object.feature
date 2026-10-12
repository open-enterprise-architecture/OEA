# Ableitung aus: requirements/req/req-060-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-08
Feature: REQ-060 – Datenobjekt (data-object) als Entität anlegen und verwalten

  Das System MUSS es Personen mit Entitäts-Bearbeiter-Berechtigung
  ermöglichen, ArchitectureEntities mit dem EntityType `data-object`
  anzulegen, zu bearbeiten und zu löschen; das System MUSS beim Anlegen
  automatisch eine instanzweit eindeutige Integer-ID vergeben und
  `entityTypeId=data-object` als unveränderliche Metatyp-Bindung
  setzen. Für `data-object` MUSS der EntityType im Metamodell die
  folgenden PropertyDefinitions unterstützen: `dataClassification`
  (enum, mandatory), `personalDataCategories` (varchar, optional),
  `dataOwner` (varchar, warning).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Anlegen mit ID-Vergabe – Lukas legt Entität `Kundenstamm` (entityTypeId=`data-object`) an
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Lukas legt Entität `Kundenstamm` (entityTypeId=`data-object`) an
    Then  HTTP 201; `id` ist Integer ≥ 1; `entityTypeId=data-object` ist gesetzt; ID ist in der Instanz einmalig

  @AC2
  Scenario: AC2 – Mandatory Property erzwingt Wert – `dataClassification` ist mandatory
    Given `dataClassification` ist mandatory
    When  Lukas legt Entität ohne `dataClassification` an
    Then  HTTP 422; Fehlermeldung benennt `dataClassification` als fehlend

  @AC3
  Scenario: AC3 – Warning-Property speichert trotzdem – `dataOwner` ist warning
    Given `dataOwner` ist warning
    When  Lukas legt Entität ohne `dataOwner` an
    Then  HTTP 200; Entität gespeichert; Response enthält `warnings: ["dataOwner: Wert fehlt"]`

  @AC4
  Scenario: AC4 – entityTypeId unveränderlich – Entität mit id=42 und entityTypeId=`data-object` existiert
    Given Entität mit id=42 und entityTypeId=`data-object` existiert
    When  Lukas versucht via PUT `entityTypeId=application-component` zu setzen
    Then  HTTP 422; `entityTypeId` wurde nicht geändert

  @AC5
  Scenario: AC5 – Löschen entfernt Referenzen-Warnung – Entität id=42 wird von carries-data-Connection id=103 referenziert
    Given Entität id=42 wird von carries-data-Connection id=103 referenziert
    When  Lukas löscht Entität id=42
    Then  System warnt vor referenzierenden Connections; nach Bestätigung: Entität gelöscht; carries-data-Connection id=103 bekommt `targetEntityId=null` oder wird ebenfalls gelöscht (Kaskade TBD in ADR)

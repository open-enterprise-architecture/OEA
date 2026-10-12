# Ableitung aus: requirements/req/req-133-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-09
Feature: REQ-133 – Verschachtelung von DocumentItems (hierarchische Kapitelstruktur)

  Das System MUSS die hierarchische Verschachtelung von DocumentItems
  innerhalb einer DocumentCollection ermöglichen. Ein DocumentItem KANN
  über das Feld `parentId` auf ein übergeordnetes DocumentItem
  derselben Collection verweisen (`parentId=null` = Wurzel-Kapitel).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Unterelement anlegen – Nutzer in der Kapitelnavigation „Unterkapitel anlegen" wählt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer in der Kapitelnavigation „Unterkapitel anlegen" wählt
    Then  Neues DocumentItem mit `parentId` des ausgewählten Items wird erstellt; erscheint eingerückt in der Navigation

  @AC2
  Scenario: AC2 – Tiefenbegrenzung – Nutzer versucht, ein Unterkapitel auf Ebene 6 anzulegen
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer versucht, ein Unterkapitel auf Ebene 6 anzulegen
    Then  Aktion wird verweigert mit Hinweis „Maximale Tiefe (5) erreicht"

  @AC3
  Scenario: AC3 – Zyklus-Schutz – `parentId` eines Items so gesetzt würde, dass ein Zyklus entsteht (Item ...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `parentId` eines Items so gesetzt würde, dass ein Zyklus entsteht (Item A → B → A)
    Then  Speichern verweigert; Fehlerhinweis

  @AC4
  Scenario: AC4 – Cascade-Delete-Warnung – Nutzer ein Item mit Kindern löscht
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer ein Item mit Kindern löscht
    Then  Warnhinweis „Dieses Kapitel hat N Unterkapitel, die ebenfalls gelöscht werden"; Bestätigung erforderlich

  @AC5
  Scenario: AC5 – Drag & Drop – Nutzer ein Item in der Kapitelnavigation per Drag & Drop verschiebt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer ein Item in der Kapitelnavigation per Drag & Drop verschiebt
    Then  `parentId` und `sortOrder` werden aktualisiert; Navigation zeigt neue Struktur sofort

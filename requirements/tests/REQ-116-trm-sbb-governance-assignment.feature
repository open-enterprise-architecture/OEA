# Ableitung aus: requirements/req/req-116-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-19
Feature: REQ-116 – SBB-Governance-Zuordnung je TRM-Kategorie

  Das System MUSS für jede TRM-Kategorie (auch `scope=imported`) das
  Setzen von `preferredStandard` (Einzelauswahl eines SBBs),
  `acceptedAlternatives` (Multi-Select SBBs) und `deprecatedOptions`
  (Multi-Select SBBs) ermöglichen. Ein SBB DARF in derselben Kategorie
  nicht in mehr als einer der drei Listen erscheinen (mutually
  exclusive).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – preferredStandard setzen und Abweichungs-Hinweis – `preferredStandard=PostgreSQL 17` in der Kategorie „Database Management"...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `preferredStandard=PostgreSQL 17` in der Kategorie „Database Management" gesetzt wird
    Then  zeigen alle Entitäten in dieser Kategorie mit einem anderen `instanceOfSBBId` den Hinweis „Abweichung vom TRM-Standard"

  @AC2
  Scenario: AC2 – Mutually exclusive – derselbe SBB gleichzeitig in `preferredStandard` und `acceptedAlternativ...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  derselbe SBB gleichzeitig in `preferredStandard` und `acceptedAlternatives` gesetzt werden soll
    Then  antwortet das System mit HTTP 422 „SBB darf nur in einer Liste erscheinen"

  @AC3
  Scenario: AC3 – imported-Kategorie editierbar – eine TRM-Kategorie mit `scope=imported` geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine TRM-Kategorie mit `scope=imported` geöffnet wird
    Then  sind SBB-Zuordnungen editierbar; Name und Parent-Kategorie sind gesperrt

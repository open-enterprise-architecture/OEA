# Ableitung aus: requirements/req/req-111-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-17
Feature: REQ-111 – Schreibschutz für importierte und built-in Continuum-Bausteine

  Alle Continuum-Bausteine (ABB, SBB, Pattern, Reference Architecture)
  mit `scope=imported` oder `scope=built-in` MÜSSEN für alle
  Schreib-Operationen gesperrt sein. In der UI DÜRFEN Bearbeiten- und
  Löschen-Aktionen für diese Bausteine nicht sichtbar sein;
  API-Versuche MÜSSEN mit HTTP 422 abgewiesen werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – API-Schreibschutz – ein PUT- oder DELETE-Request auf einen ABB mit `scope=imported` abgesetz...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein PUT- oder DELETE-Request auf einen ABB mit `scope=imported` abgesetzt wird
    Then  antwortet das System mit HTTP 422

  @AC2
  Scenario: AC2 – UI-Schreibschutz und Badge – ein Baustein mit `scope=imported` in der UI angezeigt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Baustein mit `scope=imported` in der UI angezeigt wird
    Then  sind Bearbeiten- und Löschen-Buttons nicht sichtbar; ein Badge „Importiert – TOGAF 10 TRM" ist sichtbar

  @AC3
  Scenario: AC3 – SBB-Zuordnungen editierbar – eine TRM-Kategorie mit `scope=imported` geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine TRM-Kategorie mit `scope=imported` geöffnet wird
    Then  sind SBB-Zuordnungen (`preferredStandard`, `acceptedAlternatives`, `deprecatedOptions`) editierbar; Name und Parent-Kategorie sind gesperrt

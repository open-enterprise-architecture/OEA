# Ableitung aus: requirements/req/req-120-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-20
Feature: REQ-120 – Technology-Diversity-Analyse mit Executive-Summary-Export

  Das System MUSS eine Technology-Diversity-Analyse anbieten, die je
  TRM-Kategorie die Anzahl unterschiedlicher, tatsächlich genutzter
  SBBs (via `entity.instanceOfSBBId`) ausweist. TRM-Kategorien mit mehr
  als einem genutzten SBB MÜSSEN als „Standardisierungspotenzial"
  hervorgehoben werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Standardisierungspotenzial hervorheben – die TRM-Kategorie „Container Orchestration" 3 unterschiedliche genutzte ...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die TRM-Kategorie „Container Orchestration" 3 unterschiedliche genutzte SBBs aufweist
    Then  wird diese Kategorie als „Standardisierungspotenzial" hervorgehoben

  @AC2
  Scenario: AC2 – Fehlende Standards auflisten – TRM-Kategorien ohne konfigurierten `preferredStandard` vorhanden sind
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  TRM-Kategorien ohne konfigurierten `preferredStandard` vorhanden sind
    Then  erscheinen diese in einer separaten Liste „Fehlende Standards"

  @AC3
  Scenario: AC3 – PDF-Export mit Executive Summary – der Export als PDF ausgelöst wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  der Export als PDF ausgelöst wird
    Then  enthält das Dokument eine Executive-Summary-Sektion mit Top-3-Risiken (prohibited Entitäten, Gap-Quote, Diversity-Hotspots) und Detailtabellen je Analyse-Sektion

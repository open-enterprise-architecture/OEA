# Ableitung aus: requirements/req/req-119-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-20
Feature: REQ-119 – Standards-Drift-Analyse (prohibited und Abweichungs-Report)

  Das System MUSS eine Standards-Drift-Analyse anbieten, die alle
  Entitäten ausweist, deren `instanceOfSBBId` auf einen SBB mit
  `governanceStatus=prohibited` oder `deprecated` zeigt. Zusätzlich
  MUSS die Analyse Entitäten zeigen, deren `instanceOfSBBId` nicht dem
  `preferredStandard` der zugeordneten TRM-Kategorie entspricht
  (Abweichungs-Report).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – prohibited-Entitäten – 3 Entitäten `instanceOfSBBId=prohibited-SBB-UUID` haben
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  3 Entitäten `instanceOfSBBId=prohibited-SBB-UUID` haben
    Then  sind alle 3 in der Drift-Liste; `prohibited` ist rot hervorgehoben

  @AC2
  Scenario: AC2 – Abweichungs-Report – eine Entität einen SBB verwendet, der nicht dem `preferredStandard` ihre...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Entität einen SBB verwendet, der nicht dem `preferredStandard` ihrer TRM-Kategorie entspricht
    Then  erscheint die Entität in der Abweichungs-Liste

  @AC3
  Scenario: AC3 – Direkt-Link – ein Eintrag in der Drift-Liste angeklickt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Eintrag in der Drift-Liste angeklickt wird
    Then  öffnet sich die Entitäts-Detailansicht

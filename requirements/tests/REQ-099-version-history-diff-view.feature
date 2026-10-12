# Ableitung aus: requirements/req/req-099-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-14
Feature: REQ-099 – Versionshistorie feldweiser Diff

  Das System MUSS beim Anklicken einer Version einen feldweisen Diff
  zwischen dieser Version und ihrem Nachfolger anzeigen
  (Vorher/Nachher-Werte, farblich hervorgehoben: rot=entfernt,
  grün=hinzugefügt). Properties MÜSSEN als einzelne Felder verglichen
  werden (Dot-Notation), nicht als JSON-Blob.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Nur geänderte Felder – eine Version angeklickt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Version angeklickt wird
    Then  zeigt der Diff nur die geänderten Felder; identische Felder sind ausgeblendet

  @AC2
  Scenario: AC2 – Dot-Notation für Properties – `properties.owner` geändert wurde
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `properties.owner` geändert wurde
    Then  erscheint der Eintrag als "properties.owner: 'Kurt' → 'Michael'"

  @AC3
  Scenario: AC3 – Kumulierter Diff – Version v2 und v7 für den Vergleich ausgewählt werden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Version v2 und v7 für den Vergleich ausgewählt werden
    Then  zeigt das System den kumulierten Diff über alle 5 Übergänge (v2→v3, …, v6→v7)

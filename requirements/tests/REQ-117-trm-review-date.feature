# Ableitung aus: requirements/req/req-117-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-19
Feature: REQ-117 – TRM-Kategorie Review-Datum automatisch setzen und filtern

  Das System MUSS `lastReviewedAt` bei jedem Speichern eines
  TRM-Kategorie-Eintrags automatisch auf das aktuelle UTC-Datum setzen.
  Das System MUSS einen Filter „Zuletzt überprüft vor > N Monaten"
  (parametrierbar, default: 12 Monate) in der TRM-Übersicht anbieten,
  der Kategorien mit veraltetem oder fehlendem Review-Datum hervorhebt.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – automatisches lastReviewedAt – eine TRM-Kategorie gespeichert wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine TRM-Kategorie gespeichert wird
    Then  ist `lastReviewedAt` auf das aktuelle UTC-Datum gesetzt

  @AC2
  Scenario: AC2 – Filter nach Review-Datum – der Filter „Zuletzt überprüft vor > 12 Monaten" in der TRM-Übersicht akt...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  der Filter „Zuletzt überprüft vor > 12 Monaten" in der TRM-Übersicht aktiviert wird
    Then  werden Kategorien mit `lastReviewedAt` < heute − 12 Monate oder `lastReviewedAt = null` hervorgehoben

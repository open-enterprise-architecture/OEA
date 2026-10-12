# Ableitung aus: requirements/req/req-118-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-20
Feature: REQ-118 – ABB-Abdeckungs-Analyse (Gap-Quote)

  Das System MUSS eine ABB-Abdeckungs-Analyse anbieten, die für jeden
  ABB mit `governanceStatus=approved` ausweist: Anzahl konformer
  Entitäten (`conformsToABBId`), Anzahl implementierender SBBs und ob
  ein Gap vorliegt (keine konformen Entitäten). Die Gesamtquote (ABBs
  mit mindestens einer Instanz / alle approved ABBs) MUSS als
  Prozentzahl „Gap-Quote" sichtbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Gap-Quote-Berechnung – 5 approved ABBs vorhanden sind, davon 2 ohne Instanz
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  5 approved ABBs vorhanden sind, davon 2 ohne Instanz
    Then  zeigt die Analyse Gap-Quote = 40%; die Tabelle zeigt alle 5 ABBs mit ihren Instanz-Zählungen

  @AC2
  Scenario: AC2 – ABB-Detailansicht – ein ABB in der Analyse-Tabelle geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein ABB in der Analyse-Tabelle geöffnet wird
    Then  sind implementierende SBBs und konforme Entitäten als anklickbare Links sichtbar

  @AC3
  Scenario: AC3 – Leer-Zustand – noch keine ABBs konfiguriert sind
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  noch keine ABBs konfiguriert sind
    Then  zeigt das System den Hinweis „Noch keine ABBs konfiguriert; starte mit UC-17"

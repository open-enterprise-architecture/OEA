# Ableitung aus: requirements/req/req-038-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-05
Feature: REQ-038 – Solution anlegen und Architektur-Vision beschreiben

  Das System MUSS Personen mit Solution-Architekt-Berechtigung
  ermöglichen, eine Solution mit Name, Architektur-Vision (Freitext:
  Ziel und Scope der Initiative) und – im Plateau-Modus – den
  Plateau-Referenzen (`fromPlateauId`, `toPlateauId`) anzulegen; das
  System MUSS die Business Rules BR-01 und BR-02 der Solution
  erzwingen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Michael hat Solution-Architekt-Berechtigung; kein Plateau-Modus
    Given Michael hat Solution-Architekt-Berechtigung; kein Plateau-Modus
    When  er Name „ERP-Erweiterung Logistics" und einen Beschreibungstext eingibt und speichert
    Then  Solution existiert mit status=`draft`, `ownerId=Michael`, `fromPlateauId=null`, `toPlateauId=null`; in der Solution-Übersicht sichtbar

  @AC2
  Scenario: AC2 – Instanz im Plateau-Modus; Plateau P0 (baseline) und P1 (target) existieren
    Given Instanz im Plateau-Modus; Plateau P0 (baseline) und P1 (target) existieren
    When  Michael `fromPlateauId=P0` und `toPlateauId=P1` setzt und speichert
    Then  Solution angelegt mit beiden Plateau-Referenzen; BR-02 ist erfüllt

  @AC3
  Scenario: AC3 – Michael gibt `fromPlateauId=P0` ein, lässt `toPlateauId` leer
    Given Michael gibt `fromPlateauId=P0` ein, lässt `toPlateauId` leer
    When  er speichert
    Then  Validierungsfehler „Beide Plateau-Referenzen müssen gesetzt oder beide leer sein"; kein Objekt wird angelegt (BR-01)

  @AC4
  Scenario: AC4 – Eine Solution mit Name „ERP-Erweiterung Logistics" existiert bereits
    Given Eine Solution mit Name „ERP-Erweiterung Logistics" existiert bereits
    When  Michael eine zweite Solution mit demselben Namen anlegen will
    Then  Validierungsfehler „Name 'ERP-Erweiterung Logistics' ist bereits vergeben"

  @AC5
  Scenario: AC5 – Person ohne Solution-Architekt-Berechtigung
    Given Person ohne Solution-Architekt-Berechtigung
    When  sie versucht, eine Solution anzulegen
    Then  403 Forbidden; kein Objekt wird angelegt

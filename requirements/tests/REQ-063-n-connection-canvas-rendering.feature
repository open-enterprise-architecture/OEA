# Ableitung aus: requirements/req/req-063-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: manuell (UI-Test im Client App)

@functional @should @UC-08
Feature: REQ-063 – n-Connection Canvas-Darstellung (3-Punkte-Indikator + Verbindungs-Panel)

  Das System MUSS im Canvas-Editor der Client App (Electron, ADR-009)
  für jede Connection-Entity, die als `sourceEntityId` in mindestens
  einer weiteren Connection (n-Connection) referenziert wird, einen
  **kreisförmigen 3-Punkte-Indikator** (`•••`) auf der Verbindungslinie
  darstellen; Doppelklick auf diesen Indikator MUSS ein
  **Verbindungs-Panel** öffnen, das alle n-Connections dieser
  Connection auflistet, am oberen Rand nach Connection-Typ filterbar
  ist.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Indikator erscheint – DataFlow id=5 ist `sourceEntityId` von carries-data id=103
    Given DataFlow id=5 ist `sourceEntityId` von carries-data id=103
    When  Lukas öffnet ein Diagramm, das DataFlow id=5 enthält
    Then  Auf der Linie von DataFlow id=5 erscheint der 3-Punkte-Kreis-Indikator

  @AC2
  Scenario: AC2 – Kein Indikator ohne n-Connection – DataFlow id=7 ist in keiner n-Connection sourceEntityId
    Given DataFlow id=7 ist in keiner n-Connection sourceEntityId
    When  Diagramm mit DataFlow id=7 geöffnet
    Then  Kein Indikator auf dieser Linie

  @AC3
  Scenario: AC3 – Doppelklick öffnet Panel – Lukas doppelklickt den 3-Punkte-Indikator auf DataFlow id=5
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Lukas doppelklickt den 3-Punkte-Indikator auf DataFlow id=5
    Then  Verbindungs-Panel öffnet sich; Header zeigt „ERP → DWH (data-flow)"; Filter-Bar zeigt Chip `carries-data (2)`

  @AC4
  Scenario: AC4 – Filter-Chip schaltet Ansicht – Panel mit Chips `carries-data (2)` und `security-control (1)` (beide aktiv)
    Given Panel mit Chips `carries-data (2)` und `security-control (1)` (beide aktiv)
    When  Lukas klickt auf `security-control`
    Then  Nur `security-control`-Verbindungen sichtbar; `carries-data`-Einträge ausgeblendet

  @AC5
  Scenario: AC5 – Web Portal read-only – CIO öffnet im Web Portal ein Diagramm mit n-Connection-Indikator
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  CIO öffnet im Web Portal ein Diagramm mit n-Connection-Indikator
    Then  Indikator sichtbar; Doppelklick öffnet Panel in read-only; „Neue n-Connection anlegen"-Button fehlt

  @AC6
  Scenario: AC6 – Mehrere Indikatoren auf einer Linie – DataFlow id=5 hat 3 carries-data und 2 security-control Verbindungen
    Given DataFlow id=5 hat 3 carries-data und 2 security-control Verbindungen
    When  Diagramm geladen
    Then  Ein einziger Indikator (nicht 5 separate); Panel zeigt beide Typen in Filter-Bar; Gesamtzahl sichtbar (z.B. `•••  5`)

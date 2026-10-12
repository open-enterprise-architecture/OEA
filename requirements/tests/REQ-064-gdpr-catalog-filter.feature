# Ableitung aus: requirements/req/req-064-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-08
Feature: REQ-064 – DSGVO-Katalogfilter auf Datenflüsse (personalDataCategories)

  Das System MUSS es ermöglichen, in einem Katalog (UC-06) alle
  ArchitectureEntities vom Typ `data-flow` mit einem Filter auf
  `personalDataCategories ≠ leer` einzuschränken und das Ergebnis als
  CSV zu exportieren; dieser Filter MUSS auch mit den
  Katalog-Filteroperatoren aus REQ-047 kombinierbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Filter personalDataCategories ≠ leer – DataFlow id=5 mit `personalDataCategories="Name,Adresse,E-Mail"` und Dat...
    Given DataFlow id=5 mit `personalDataCategories="Name,Adresse,E-Mail"` und DataFlow id=7 ohne personalDataCategories
    When  Lukas öffnet Katalog mit Filter `entityTypeId=data-flow` + `personalDataCategories ≠ leer`
    Then  Nur id=5 erscheint; id=7 nicht

  @AC2
  Scenario: AC2 – Kombinierter Filter – Lukas fügt zusätzlich Filter `protocol=JDBC` hinzu
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Lukas fügt zusätzlich Filter `protocol=JDBC` hinzu
    Then  Nur DataFlows mit JDBC-Protokoll UND nicht-leerem personalDataCategories erscheinen

  @AC3
  Scenario: AC3 – CSV-Export – Katalog zeigt 3 DataFlows nach Filter
    Given Katalog zeigt 3 DataFlows nach Filter
    When  Lukas klickt „Als CSV exportieren"
    Then  CSV-Download mit 3 Zeilen + Header; Felder wie oben beschrieben; encoding UTF-8

  @AC4
  Scenario: AC4 – Leeres Ergebnis – Keine DataFlows haben personalDataCategories befüllt
    Given Keine DataFlows haben personalDataCategories befüllt
    When  Filter angewendet
    Then  Leere Tabelle; kein Fehler; Export liefert nur Header-Zeile

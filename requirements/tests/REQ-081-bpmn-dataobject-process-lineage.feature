# Ableitung aus: requirements/req/req-081-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should
Feature: REQ-081 – BPMN-DataObject-Integration und Prozess-Datenlineage

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – DataObject im Prozessdiagramm – Business Analyst kann `bpmn-data-object` und `bpmn-data-store` aus der B...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Business Analyst kann `bpmn-data-object` und `bpmn-data-store` aus der BPMN-Palette auf das Prozessdiagramm ziehen
    # - DataObjects können innerhalb von Lanes oder frei auf Prozessebene platziert werden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC2
  Scenario: AC2 – Input-/Output-Assoziation anlegen – Business Analyst zieht eine gestrichelte Linie (BPMN-Notation) von einem...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Business Analyst zieht eine gestrichelte Linie (BPMN-Notation) von einem DataObject zu einem Task (Input) oder vom Task zu einem DataObject (Output)
    # - UI fragt beim Anlegen: Richtung (Input / Output) und optional `transformationType` (create / enrich / overwrite / delete / read-only)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC3
  Scenario: AC3 – Anreicherungs-Annotation – Detail-Panel einer `bpmn-data-output-association` zeigt Felder: `transfo...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Detail-Panel einer `bpmn-data-output-association` zeigt Felder: `transformationType` (Dropdown), `affectedAttributes` (Tag-Eingabe), `condition` (Freitext)
    # - Diese Properties werden im Repository gespeichert und sind via API abfragbar
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC4
  Scenario: AC4 – Bestehendes data-object referenzieren – Business Analyst kann beim Anlegen eines `bpmn-data-object` ein bereits ...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Business Analyst kann beim Anlegen eines `bpmn-data-object` ein bereits im EA-Repository vorhandenes `data-object` auswählen (Autocomplete via `[[`-Trigger, REQ-070)
    # - Das BPMN-Element zeigt dann Name und ID des referenzierten Objekts; kein Duplikat wird erstellt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC5
  Scenario: AC5 – DataStore mit technischer Infrastruktur verknüpfen – `bpmn-data-store` kann via `realizes`-Connection mit einem `data-compone...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - `bpmn-data-store` kann via `realizes`-Connection mit einem `data-component` aus dem EA-Repository verknüpft werden
    # - Verknüpfung: Kontextmenü „Realisiert durch" → Suche im EA-Repository
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC6
  Scenario: AC6 – Lineage-Query durch Prozess – `GET /api/v1/entities/{id}/lineage?direction=downstream` für ein `data-o...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - `GET /api/v1/entities/{id}/lineage?direction=downstream` für ein `data-object` gibt auch BPMN-Tasks zurück, die dieses Objekt über `bpmn-data-output-association` anreichern
    # - Analog upstream: Tasks, die es als Input lesen
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC7
  Scenario: AC7 – Katalog-Join auf Prozess-Daten – Katalog mit `data-object` als primaryEntityType kann via Join auf `bpmn-...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Katalog mit `data-object` als primaryEntityType kann via Join auf `bpmn-data-output-association` zeigen, welche Prozess-Tasks jedes DataObject erzeugen oder anreichern
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC8
  Scenario: AC8 – Web Portal read-only – BPMN-Diagramm mit DataObjects ist im Web Portal lesbar; DataObject-Names...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - BPMN-Diagramm mit DataObjects ist im Web Portal lesbar; DataObject-Names und transformationType-Annotationen sichtbar
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

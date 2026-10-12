# US-045: Neue Entität im Diagramm als Delta anlegen

**ID**: US-045
**Story Points**: 8
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich im Diagramm eine neue Entität direkt auf dem Canvas platzieren und benennen können, wobei mir während der Namenseingabe die bestehenden Komponenten aus der IT-Landschaft angezeigt werden, damit ich Namenskonflikte sofort erkenne und zwischen „wirklich neu" und „bereits existierend (modified/retiring)" unterscheiden kann.

## Bezug

**Use Case**: [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**Requirement**: [REQ-040: EntityDeltas einer Solution erfassen](../req/REQ-040-entity-deltas-erfassen.md)

## Akzeptanzkriterien

**AC1** (neue Entität, kein Treffer):
- Gegeben: Die IT-Landschaft enthält `CRM-Legacy` und `ERP-Core`; Michael arbeitet im Diagramm
- Wenn: er ein neues Element auf dem Canvas platziert und „Salesforce" eintippt
- Dann: erscheint eine Vorschlagsliste mit Treffern aus der Landschaft (Teilstring-Suche); „Salesforce" findet keinen Treffer; Michael bestätigt → `Salesforce` wird als `deltaType=new` angelegt

**AC2** (bestehende Entität erkannt):
- Gegeben: Die Landschaft enthält `CRM-Legacy (ApplicationComponent)`
- Wenn: Michael „CRM" eintippt
- Dann: erscheint `CRM-Legacy` in der Vorschlagsliste; wählt Michael sie aus, wird das Element als `deltaType=retiring` (oder `modified`) angelegt, **nicht** als `new`

**AC3** (Vorschlagsliste enthält immer Landschafts-Einträge):
- Gegeben: Die Landschaft enthält 30 Entitäten
- Wenn: Michael ein neues Diagramm-Element anlegt und den Namen-Eingabebereich fokussiert (noch bevor er tippt)
- Dann: zeigt die Vorschlagsliste sofort alle Entitäten der Landschaft (ungefiltert), sortiert nach EntityType und Name

**AC4** (Diagramm-Element ohne Namenswahl bleibt new):
- Gegeben: Michael tippt „NewApp" und die Vorschlagsliste zeigt keine Treffer
- Wenn: er die Auswahl schliesst und mit dem eingetippten Namen bestätigt
- Dann: wird `NewApp` als `deltaType=new` mit dem eingegebenen Namen angelegt

**AC5** (Konsistenz mit Katalog-Pfad):
- Gegeben: Michael hat im Diagramm `Salesforce (new)` angelegt
- Wenn: er zur Katalog-Ansicht wechselt
- Dann: ist `Salesforce` in der Delta-Liste sichtbar – identisch mit einem via US-040 angelegten Delta

**AC6** (Duplikat-Schutz greift auch im Diagramm):
- Gegeben: `Salesforce (new)` existiert bereits als Delta in dieser Solution
- Wenn: Michael versucht, im Diagramm eine weitere Entität mit demselben Namen anzulegen
- Dann: erscheint eine Warnung „Entität 'Salesforce' existiert bereits als Delta in dieser Solution"; kein zweites Delta wird angelegt

## Technische Hinweise

- Betroffene Komponenten: Diagramm-Canvas (neues UI-Modul), Backend `GET /api/v1/solutions/{id}/landscape?q={suchbegriff}` (Vorschlagsliste), `POST /api/v1/solutions/{id}/deltas` (gleicher Endpunkt wie US-040)
- Vorschlagsliste: debounced Typeahead (min. 0 Zeichen → zeige Gesamtliste; Aktualisierung bei jeder Eingabe)
- Auswahl aus Vorschlagsliste: System fragt nach dem gewünschten deltaType (`retiring` oder `modified`); Standard-Annahme `retiring` wenn die Entität abgelöst wird, `modified` wenn sie verändert wird
- Namens-Eingabe direkt auf dem Canvas-Element (In-Place-Edit), nicht in einem separaten Dialog
- Canvas-Bibliothek: TBD (ADR nötig bevor Implementierung startet)

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC6 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: neue Entität (kein Treffer in Landschaft), bestehende Entität aus Vorschlag auswählen → retiring/modified, Vorschlagsliste bei leerem Input, Duplikat-Schutz, Konsistenz mit Katalog-Ansicht
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-038 (Solution), US-039 (Landschafts-Endpunkt für Vorschlagsliste), US-040 (Backend-Endpunkt für Deltas muss existieren), [ADR-007](../../adrs/ADR-007-canvas-bibliothek.md) (Canvas-Bibliothek)
- Blockiert: keine

## Notizen

8 SP wegen der Canvas-Komponente (neues UI-Modul) und der Typeahead-Logik. Die Kernidee ist, dass der Diagramm-Kontext dem Architekten sofort Feedback gibt: „Gibt es das schon?" – ohne eine separate Suche öffnen zu müssen. Das verhindert den häufigen Fehler, eine bereits existierende Komponente ein zweites Mal als `new` anzulegen.

[ADR-007](../../adrs/ADR-007-canvas-bibliothek.md) (React Flow) muss vor Implementierung dieser Story auf `accepted` gesetzt sein.

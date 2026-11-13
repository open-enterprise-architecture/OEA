---
title: Lösungen
covers:
  screens: [SCR-060, SCR-061, SCR-062]
  stories: [US-038, US-039, US-040, US-041, US-042, US-043, US-044]
---

# Lösungen

Eine Lösung (Solution) ist der Container eines Veränderungsvorhabens, etwa eines neuen Moduls, einer ersetzten Schnittstelle oder einer Plattformmigration. Ihre **Architekturvision** hält fest, was das Vorhaben erreichen will und was es umfasst, damit alle Beteiligten dasselbe Bild haben. Die Lösung enthält auch die geplanten Änderungen an der Landschaft: die Entitäten, die das Vorhaben einführt, die es ändert und die es ausser Betrieb nimmt.

Um Lösungen zu sehen, brauchst du die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`). Zum Anlegen brauchst du `solution:write`: Die Rollen `solution-architect` und `enterprise-architect` haben sie (siehe [Rollen und Berechtigungen](../administration/roles-and-permissions)).

## Eine Lösung anlegen

1. Wähle in der Kopfzeile *Lösungen* und dann *Neue Lösung*.
2. Fülle den Dialog *Neue Lösung* aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | Pflicht, bis 255 Zeichen; eindeutig in der Instanz, unabhängig von Gross- und Kleinschreibung und von Leerzeichen am Anfang und Ende |
   | Architekturvision | optional, bis 2000 Zeichen: Ziel, Umfang und Begründung des Vorhabens. Zeilenumbrüche bleiben erhalten |

<Screenshot name="solution-create" alt="Dialog Neue Lösung mit Name und Architekturvision" />

3. Wähle *Lösung anlegen*. Die Seite der neuen Lösung öffnet sich.

Ist der Name schon vergeben, sagt der Dialog das, zum Beispiel „Der Name 'ERP Extension Logistics' ist bereits vergeben“; wähle einen anderen Namen. Ohne `solution:write` siehst du die Lösungen, aber nicht *Neue Lösung*.

Eine neue Lösung hat den Status *Entwurf*, und du bist verantwortlich für sie. Sie entsteht im **Projektmodus**: ohne Ausgangs- und Zielplateau. Der Plateaumodus folgt mit den Plateaus.

## Lösungen ansehen

Die Seite *Lösungen* listet jede Lösung mit Status, verantwortlicher Person und Anlagedatum, nach Name sortiert:

<Screenshot name="solutions" alt="Liste der Lösungen" />

Öffne eine Lösung, um ihre Architekturvision, ihre Änderungen und ihre Ausgangslage zu sehen:

<Screenshot name="solution" alt="Seite einer Lösung mit verantwortlicher Person, Anlagezeit, Architekturvision, Änderungen und Ausgangslage" />

Name, Vision und Status einer Lösung lassen sich noch nicht ändern.

## Ausgangslage

Der Bereich *Ausgangslage* (Baseline) einer Lösung zeigt die aktuelle IT-Landschaft, auf der das Vorhaben aufbaut: jede Entität, die heute im Katalog steht und nicht gelöscht ist, mit Name und Metatyp, nach Name sortiert. Verbindungen wie Datenflüsse erscheinen nicht. Die Ausgangslage wird beim Öffnen der Lösung gelesen und zeigt deshalb immer den Katalog, wie er jetzt ist.

- **Suche:** Gib einen Teil des Namens ein, bis 255 Zeichen; Gross- und Kleinschreibung spielen keine Rolle.
- **Filter:** Wähle einen Metatyp, um nur seine Entitäten zu sehen.
- **Seiten:** Die Ausgangslage zeigt 50 Entitäten pro Seite.

Enthält der Katalog noch keine Entitäten, steht im Bereich „Keine Entitäten in der aktuellen Landschaft – du kannst nur neue Entitäten anlegen“. Findet nur deine Suche oder dein Filter nichts, steht stattdessen das.

## Änderungen

Der Bereich *Änderungen* listet, was das Vorhaben an der Landschaft ändert: zuerst die neuen Entitäten, dann die geänderten Entitäten mit der Zahl ihrer Änderungen, dann die Entitäten, die es ausser Betrieb nimmt, jeweils nach Name. Er zeigt 50 Änderungen und die Gesamtzahl; *Weitere laden* fügt die nächsten 50 hinzu. Mit `solution:write` fügst du Änderungen in jeder Lösung hinzu und entfernst sie, nicht nur in deinen eigenen.

- **Entität ändern:** Wähle *Ändern* in ihrer Zeile der Ausgangslage. Der Dialog *Entität ändern* zeigt jedes Feld mit seinem aktuellen Wert und einem Eingabefeld für den neuen: den Namen, die Beschreibung und die Eigenschaften. Gib nur ein, was sich ändert; leerst du eine Eigenschaft, wird ihr Wert entfernt. Beim Speichern werden die aktuellen Werte als Werte vor der Änderung festgehalten, und sie bleiben so, auch wenn sich der Katalog später ändert. Ein Feld mit seinem aktuellen Wert ist keine Änderung, und eine Änderung braucht mindestens ein geändertes Feld. Die Entität erscheint unter *Änderungen* als *Geändert*, und ihre Zeile in der Ausgangslage zeigt *Geändert*.
- **Änderung bearbeiten:** Wähle *Bearbeiten* in ihrer Zeile unter *Änderungen*. Der Dialog beginnt mit den neuen Werten; was du speicherst, ersetzt sie. Ein Feld, das schon Teil der Änderung war, behält seinen festgehaltenen Wert vor der Änderung. Die Änderung einer eingeschränkten Eigenschaft, die du nicht lesen darfst, bleibt, wie sie ist.
- **Entität ausser Betrieb nehmen:** Wähle *Ausser Betrieb nehmen* in ihrer Zeile der Ausgangslage. Die Entität erscheint unter *Änderungen* als *Ausser Betrieb*, und ihre Zeile in der Ausgangslage zeigt *Ausser Betrieb* statt der Aktion.
- **Neue Entität anlegen:** Wähle *Neue Entität* und fülle den Dialog aus: den Metatyp, einen Namen mit bis zu 255 Zeichen, optional eine Beschreibung mit bis zu 2000 Zeichen und die Eigenschaften des Metatyps. Als Pflicht markierte Eigenschaften brauchen einen Wert. Die neue Entität wird noch nicht in den Katalog geschrieben: Sie kommt mit dem Go-live der Lösung in den Katalog und ist bis dahin nicht Teil der Ausgangslage.
- **Änderung entfernen:** Wähle *Entfernen* in ihrer Zeile und bestätige. Eine Entität kann danach erneut ausser Betrieb genommen werden, etwa nach einem Versehen.

Die Regeln:

- Eine Lösung enthält eine Änderung pro Entität: Dieselbe Entität kann nicht zweimal ausser Betrieb genommen oder zugleich geändert und ausser Betrieb genommen werden, und zwei neue Entitäten desselben Metatyps können nicht gleich heissen (Gross- und Kleinschreibung spielen keine Rolle). Eine andere Lösung kann dieselbe Entität ändern; beide zeigen dann eine Warnung (siehe [Parallele Änderungen](#parallel-changes)).
- Ein Wert muss zu seiner Eigenschaft passen, zum Beispiel eine ganze Zahl bei einer Ganzzahl-Eigenschaft, und der Wert einer Pflicht-Eigenschaft lässt sich nicht entfernen. Wird ein Wert abgelehnt, zeigt der Dialog beim Feld, warum.
- Eine eingeschränkte Eigenschaft kannst du nur setzen, wenn eine deiner Rollen sie schreiben darf (siehe [Sichtbarkeit von Eigenschaften](../administration/property-visibility)). Eine verbindungsbasierte Eigenschaft lässt sich bei einer neuen Entität nicht setzen, weil ihre Verbindungen noch nicht existieren; bei einer bestehenden Entität muss diese in deinem Bereich liegen. Werte, die du nicht lesen darfst, erscheinen verborgen.
- Eine Lösung kann beliebig viele Änderungen enthalten.
- Eine umgesetzte oder archivierte Lösung ist abgeschlossen: Sie lässt sich nicht mehr ändern, und ihre Änderungen bleiben, wie sie sind.

## Vergleichen

*Vergleichen* im Bereich *Änderungen* öffnet den Vergleich der Lösung. Für jede Änderung zeigt er die Entität, wie sie jetzt im Katalog steht, den *Ist-Stand*, neben ihrem Zustand nach der Lösung, dem *Soll-Stand*. Entitäten ohne Änderung erscheinen nicht. Auf einem schmalen Bildschirm stehen die beiden Stände untereinander.

<Screenshot name="solution-diff" alt="Vergleich einer Lösung mit neuen, geänderten und auslaufenden Entitäten" />

- **Neu:** Der Ist-Stand sagt "Existiert noch nicht"; der Soll-Stand zeigt die neue Entität mit ihren Werten.
- **Geändert:** Beide Stände zeigen jedes Feld mit einem Wert. Die Felder, die die Lösung ändert, sind hervorgehoben und als geändert markiert; die übrigen Felder erscheinen gedämpft und bleiben, wie sie sind.
- **Ausser Betrieb:** Der Soll-Stand sagt "Ausser Betrieb" und zeigt die Entität durchgestrichen.
- **Im Katalog seit dem Erfassen geändert:** Eine Änderung hält beim Speichern die Werte vor der Änderung fest. Steht heute ein anderer Wert im Katalog, zeigt der Ist-Stand den heutigen Wert mit diesem Hinweis. Prüfe, ob die Änderung noch passt.
- **Nicht mehr im Katalog:** Die Entität wurde gelöscht, nachdem die Änderung erfasst wurde; der Ist-Stand ist leer.

Werte, die du nicht lesen darfst, erscheinen als verborgen. Der Vergleich wird bei jedem Öffnen neu berechnet; *Aktualisieren* lädt ihn erneut, etwa wenn jemand anderes eine Änderung erfasst hat. Er zeigt jeweils 50 Änderungen; *Weitere laden* fügt die nächsten 50 hinzu.

## Parallele Änderungen {#parallel-changes}

Oft laufen mehrere Vorhaben gleichzeitig. Ändert eine andere aktive Lösung eine Entität oder nimmt sie ausser Betrieb, die auch deine Lösung ändert oder ausser Betrieb nimmt, warnt dich OEA, damit du dich vor dem Go-live mit ihrer verantwortlichen Person abstimmen kannst. Eine Lösung ist aktiv, bis sie umgesetzt oder archiviert ist. Neue Entitäten kollidieren nie, weil sie noch nicht im Katalog stehen.

- **An der Änderung:** Ihre Zeile unter *Änderungen* sagt für jede andere Lösung "Wird auch in '…' geändert (verantwortlich: …)"; der Name öffnet diese Lösung. Der Vergleich zeigt denselben Hinweis auf der Karte der Entität.
- **Oben auf der Lösung:** Ein Hinweis zählt die Änderungen, die auch andere aktive Lösungen betreffen.
- **Beim Speichern:** Nimmst du eine Entität ausser Betrieb oder änderst sie, und eine andere Lösung ändert sie auch, zeigt ein Hinweis die Namen der anderen Lösungen. Schliesse ihn, wenn du ihn gelesen hast; die Warnung an der Änderung bleibt.
- **In der Ausgangslage:** Eine Entität, die eine andere aktive Lösung ändert, ist mit "In … anderen Lösungen geändert" und deren Namen markiert, schon bevor du etwas erfasst. Diese Markierung erscheint nur für die Entitätstypen, für die deine Metamodell-Administration sie eingeschaltet hat (siehe [Parallele Änderungen in der Ausgangslage](../administration/metamodel-editing#parallel-changes)).

Die Warnung blockiert nie eine Änderung. Sie wird bei jedem Öffnen der Lösung neu berechnet und verschwindet daher, sobald die andere Lösung umgesetzt oder archiviert ist oder ihre Änderung entfernt.

## Über die API

```bash
curl "${auth[@]}" -d '{"name": "ERP Extension Logistics", "description": "Goal: warehouse logistics in the ERP."}' \
  "$OEA/solutions"
curl "${auth[@]}" "$OEA/solutions"
# $SOLUTION ist die "id" einer Lösung aus der Liste
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape?q=erp&pageSize=50"
# Entität 42 der Ausgangslage ausser Betrieb nehmen, dann die Änderungen auflisten und entfernen
curl "${auth[@]}" -d '{"deltaType": "retiring", "entityId": 42}' "$OEA/solutions/$SOLUTION/deltas"
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/deltas?pageSize=50"
curl "${auth[@]}" -X DELETE "$OEA/solutions/$SOLUTION/deltas/$DELTA"
# Entität 43 ändern: ihre aktuellen Werte, dann der neue Wert einer Eigenschaft
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape/43"
curl "${auth[@]}" -d '{"deltaType": "modified", "entityId": 43, "properties": [{"propertyDefId": "'"$RELEASE"'", "value": "v5"}]}' \
  "$OEA/solutions/$SOLUTION/deltas"
# der Vergleich von Ist- und Soll-Stand
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/diff?pageSize=50"
```

`POST /solutions` antwortet mit 201 und der Lösung, mit 422 und `name` bei einem leeren oder vergebenen Namen und mit 403 ohne `solution:write`. Jedes Anlegen wird im Audit-Log als `solution.created` festgehalten.

`GET /solutions/{id}/landscape` liefert eine Seite der Ausgangslage mit der Gesamtzahl und, falls es eine weitere Seite gibt, einem `nextCursor`; schicke ihn als `cursor` zusammen mit `page` + 1, um die nächste Seite zu laden. `metatypeId` filtert nach Metatyp.

`POST /solutions/{id}/deltas` erfasst eine Änderung: `{"deltaType": "retiring", "entityId": …}` oder `{"deltaType": "new", "metatypeId": …, "name": …, "description": …, "properties": [{"propertyDefId": …, "value": "…"}]}` mit den Werten als Text, wie beim Schreiben einer Entität. Die Antwort ist 201 mit der Änderung, 422 mit `invalidFields` und einer Meldung pro Feld in `errors`, 403 bei einem eingeschränkten Wert, den du nicht schreiben darfst, und 409 bei einer abgeschlossenen Lösung. Hinzufügen und Entfernen werden im Audit-Log als `solution.delta-added` und `solution.delta-removed` festgehalten, ohne die Eigenschaftswerte.

Eine Änderung einer bestehenden Entität ist `{"deltaType": "modified", "entityId": …, "name": …, "description": …, "properties": […]}` mit nur den neuen Werten; die Antwort enthält `changes` mit dem festgehaltenen Wert `before` und dem neuen Wert `after` jedes Felds. `PUT /solutions/{id}/deltas/{deltaId}` mit `name`, `description` und `properties` ersetzt die Änderungen und wird als `solution.delta-updated` festgehalten. `GET /solutions/{id}/landscape/{entityId}` liefert eine Entität der Ausgangslage mit ihren aktuellen Werten.

`GET /solutions/{id}/deltas` und `GET /solutions/{id}/diff` liefern eine Seite, mit `page` und `pageSize` (1 bis 200, Standard 50), der Zahl der Änderungen pro Typ in `counts` und, falls es eine weitere Seite gibt, einem `nextCursor`, den du als `cursor` zusammen mit `page` + 1 schickst. Jeder Eintrag des Vergleichs enthält die `fields` der Entität mit ihrem Wert `current` und `target`, `changed` für ein Feld, das die Lösung ändert, und `catalogChanged` für ein Feld, dessen festgehaltener Wert vom Katalog abweicht; `inCatalog` ist falsch für eine neue Entität und für eine inzwischen gelöschte.

Jede Änderung und jeder Eintrag des Vergleichs enthält `conflicts`: die anderen aktiven Lösungen, die dieselbe Entität ändern, jeweils mit `solutionId`, `solutionName`, `status`, `ownerId`, `ownerName` und dem `deltaType` ihrer Änderung, nach Name sortiert; für eine neue Entität ist die Liste leer. Auch die Antworten von `POST` und `PUT` enthalten sie. `conflicted` in der Liste der Änderungen zählt die Änderungen mit mindestens einem Konflikt, über alle Seiten. In der Ausgangslage listet `parallelChanges` sie für die Entitätstypen, die parallele Änderungen zeigen, und ist sonst leer.

---
title: Lösungen
covers:
  screens: [SCR-060, SCR-061]
  stories: [US-038, US-039, US-040, US-042]
---

# Lösungen

Eine Lösung (Solution) ist der Container eines Veränderungsvorhabens, etwa eines neuen Moduls, einer ersetzten Schnittstelle oder einer Plattformmigration. Ihre **Architekturvision** hält fest, was das Vorhaben erreichen will und was es umfasst, damit alle Beteiligten dasselbe Bild haben. Die Lösung enthält auch die geplanten Änderungen an der Landschaft: die Entitäten, die das Vorhaben einführt, und die, die es ausser Betrieb nimmt.

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

Der Bereich *Änderungen* listet, was das Vorhaben an der Landschaft ändert: zuerst die neuen Entitäten, dann die Entitäten, die es ausser Betrieb nimmt, jeweils nach Name. Mit `solution:write` fügst du Änderungen in jeder Lösung hinzu und entfernst sie, nicht nur in deinen eigenen.

- **Entität ausser Betrieb nehmen:** Wähle *Ausser Betrieb nehmen* in ihrer Zeile der Ausgangslage. Die Entität erscheint unter *Änderungen* als *Ausser Betrieb*, und ihre Zeile in der Ausgangslage zeigt *Ausser Betrieb* statt der Aktion.
- **Neue Entität anlegen:** Wähle *Neue Entität* und fülle den Dialog aus: den Metatyp, einen Namen mit bis zu 255 Zeichen, optional eine Beschreibung mit bis zu 2000 Zeichen und die Eigenschaften des Metatyps. Als Pflicht markierte Eigenschaften brauchen einen Wert. Die neue Entität wird noch nicht in den Katalog geschrieben: Sie kommt mit dem Go-live der Lösung in den Katalog und ist bis dahin nicht Teil der Ausgangslage.
- **Änderung entfernen:** Wähle *Entfernen* in ihrer Zeile und bestätige. Eine Entität kann danach erneut ausser Betrieb genommen werden, etwa nach einem Versehen.

Die Regeln:

- Eine Lösung enthält eine Änderung pro Entität: Dieselbe Entität kann nicht zweimal ausser Betrieb genommen werden, und zwei neue Entitäten desselben Metatyps können nicht gleich heissen (Gross- und Kleinschreibung spielen keine Rolle). Eine andere Lösung kann dieselbe Entität ändern.
- Ein Wert muss zu seiner Eigenschaft passen, zum Beispiel eine ganze Zahl bei einer Ganzzahl-Eigenschaft. Wird ein Wert abgelehnt, zeigt der Dialog beim Feld, warum.
- Eine eingeschränkte Eigenschaft kannst du nur setzen, wenn eine deiner Rollen sie schreiben darf (siehe [Sichtbarkeit von Eigenschaften](../administration/property-visibility)). Eine verbindungsbasierte Eigenschaft lässt sich bei einer neuen Entität nicht setzen, weil ihre Verbindungen noch nicht existieren. Werte, die du nicht lesen darfst, erscheinen verborgen.
- Eine Lösung enthält höchstens 1000 Änderungen.
- Eine umgesetzte oder archivierte Lösung ist abgeschlossen: Sie lässt sich nicht mehr ändern, und ihre Änderungen bleiben, wie sie sind.

## Über die API

```bash
curl "${auth[@]}" -d '{"name": "ERP Extension Logistics", "description": "Goal: warehouse logistics in the ERP."}' \
  "$OEA/solutions"
curl "${auth[@]}" "$OEA/solutions"
# $SOLUTION ist die "id" einer Lösung aus der Liste
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape?q=erp&pageSize=50"
# Entität 42 der Ausgangslage ausser Betrieb nehmen, dann die Änderungen auflisten und entfernen
curl "${auth[@]}" -d '{"deltaType": "retiring", "entityId": 42}' "$OEA/solutions/$SOLUTION/deltas"
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/deltas"
curl "${auth[@]}" -X DELETE "$OEA/solutions/$SOLUTION/deltas/$DELTA"
```

`POST /solutions` antwortet mit 201 und der Lösung, mit 422 und `name` bei einem leeren oder vergebenen Namen und mit 403 ohne `solution:write`. Jedes Anlegen wird im Audit-Log als `solution.created` festgehalten.

`GET /solutions/{id}/landscape` liefert eine Seite der Ausgangslage mit der Gesamtzahl und, falls es eine weitere Seite gibt, einem `nextCursor`; schicke ihn als `cursor` zusammen mit `page` + 1, um die nächste Seite zu laden. `metatypeId` filtert nach Metatyp.

`POST /solutions/{id}/deltas` erfasst eine Änderung: `{"deltaType": "retiring", "entityId": …}` oder `{"deltaType": "new", "metatypeId": …, "name": …, "description": …, "properties": [{"propertyDefId": …, "value": "…"}]}` mit den Werten als Text, wie beim Schreiben einer Entität. Die Antwort ist 201 mit der Änderung, 422 mit `invalidFields` und einer Meldung pro Feld in `errors`, 403 bei einem eingeschränkten Wert, den du nicht schreiben darfst, und 409 bei einer abgeschlossenen Lösung. Hinzufügen und Entfernen werden im Audit-Log als `solution.delta-added` und `solution.delta-removed` festgehalten, ohne die Eigenschaftswerte.

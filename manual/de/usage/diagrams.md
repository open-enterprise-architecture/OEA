---
title: Diagramme
covers:
  screens: [SCR-067]
  stories: [US-074]
---

# Diagramme

Ein Diagramm modelliert einen Geschäftsprozess oder eine andere Sicht auf die Architektur mit der Notation eines [Viewpoints](../administration/metamodel-editing#viewpoints), zum Beispiel *Process Model (BPMN 2.0)*. Diese Version von OEA verwaltet Diagramme als Objekte: Du legst sie an, findest, benennst um, löschst und stellst sie wieder her. Die Zeichenfläche, auf der du Elemente platzierst, folgt in einer späteren Version; bis dahin zeigt die Seite eines Diagramms seinen Namen, seine Beschreibung und seinen Viewpoint.

Zum Ansehen brauchst du die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`). Anlegen, Umbenennen und Löschen braucht `entity:write`, Wiederherstellen `entity:restore` (Rolle `enterprise-architect`). Service-Accounts mit `entity:write` können Diagramme über die API wie Personen anlegen und ändern.

## Diagramme finden

Du findest Diagramme an drei Stellen:

- **Die Seite *Diagramme*:** Wähle in der Kopfzeile *Diagramme*. Die Seite listet die Diagramme nach Namen, unabhängig von Gross- und Kleinschreibung, jeweils mit ihrem Viewpoint, der Notation des Viewpoints und dem Zeitpunkt der letzten Änderung. Gib einen Namensteil in *Nach Name filtern* ein, um die Liste einzugrenzen; Zeichen wie `%` und `_` gelten, wie eingegeben. Die Liste zeigt 50 Diagramme auf einmal; wähle *Mehr laden* für die nächsten. Ohne Diagramm meldet die Seite *Noch keine Diagramme.*
- **Der Browser:** Die Kategorie *Diagramme* unter *Aktueller Stand* in der Seitenleiste listet die Diagramme mit der Notation als kleiner Markierung (siehe [Navigationsbaum](./navigation#browser)).
- **Der Navigationsbaum:** Diagramme, die in einen Ordner eingeordnet wurden (siehe [Diagramm einordnen](./navigation#place-a-diagram)).

Wähle ein Diagramm, um seine Seite zu öffnen.

## Diagramm anlegen

1. Wähle auf der Seite *Diagramme* *Diagramm anlegen*.
2. Fülle den Dialog aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | erforderlich, 1 bis 200 Zeichen; eindeutig unter den Diagrammen, unabhängig von Gross- und Kleinschreibung; umgebende Leerzeichen werden entfernt |
   | Beschreibung | optional, bis zu 2000 Zeichen |
   | Viewpoint | erforderlich; der Viewpoint des Diagramms und seine Notation |

3. Wähle *Anlegen*. Das neue, leere Diagramm öffnet sich.

Der Viewpoint wird beim Anlegen festgelegt und lässt sich später nicht ändern. Ist der Name vergeben, meldet der Dialog *Ein Diagramm mit diesem Namen gibt es schon.*

## Diagramm umbenennen und löschen

*Umbenennen* ändert Namen und Beschreibung eines Diagramms, auf der Seite *Diagramme* und auf der Seite des Diagramms. Hat jemand anderes das Diagramm inzwischen geändert, meldet der Dialog das; lade die Seite neu und wiederhole deine Änderung.

*Löschen* fragt nach einer Bestätigung. Ein gelöschtes Diagramm erscheint nicht mehr in der Liste, im Browser und im Navigationsbaum, und sein Name kann wieder verwendet werden. **Die Entitäten des Repositorys bleiben.** Das Diagramm selbst lässt sich wiederherstellen (siehe unten).

## Diagramm wiederherstellen

Mit der Berechtigung `entity:restore` listet der Schalter *Gelöschte anzeigen* auf der Seite *Diagramme* die gelöschten Diagramme mit Zeitpunkt und Person des Löschens. Wähle *Wiederherstellen*, um ein Diagramm samt seinen Einträgen im Navigationsbaum zurückzuholen. Hat inzwischen ein anderes Diagramm seinen Namen bekommen, meldet die Seite das, und das Diagramm bleibt gelöscht; benenne zuerst das andere Diagramm um.

## Viewpoint löschen

Diagramme können ohne ihren Viewpoint nicht bestehen. Löschst du einen Viewpoint, den Diagramme nutzen, werden sie mit ihm **endgültig** gelöscht, auch die bereits gelöschten; die Entitäten bleiben. Die Bestätigung nennt die Anzahl der Diagramme, die gelöscht werden (siehe [Viewpoints](../administration/metamodel-editing#viewpoints)). Dasselbe geschieht, wenn eine neue Version eines [Metamodell-Pakets](../administration/metamodel-packages) einen systemdefinierten Viewpoint nicht mehr enthält.

## Über die API

Die Diagramme sind über `/diagrams` verfügbar (siehe die [API-Referenz](../api/)):

- `GET /diagrams` listet die Diagramme nach Namen. `q` ist ein Namensteil (höchstens 200 Zeichen), `pageSize` ist 1 bis 200 (Standard 50), und `page` beginnt bei 0. Die Antwort enthält `nextCursor`; sende ihn als `cursor` zusammen mit `page` um 1 erhöht, um fortzufahren. Ein Cursor einer anderen Abfrage wird mit 400 abgelehnt, das `cursor` nennt.
- `POST /diagrams` legt ein Diagramm aus `name`, `description` und `viewpointId` an; `GET /diagrams/{id}` liest es; `PUT /diagrams/{id}` ändert `name` und `description` (eine `viewpointId` wird ignoriert); `DELETE /diagrams/{id}` löscht es. Jede Änderung braucht den Header `If-Match` mit der gelesenen Version (`"3"`); ohne ihn lautet die Antwort 428, mit einer anderen Version 412. Die Antworten tragen die neue Version als `ETag`.
- `GET /diagrams/deleted` listet die gelöschten Diagramme, und `POST /diagrams/{id}/restore` mit `If-Match` stellt eines wieder her; beides braucht `entity:restore`. Ein Diagramm, das nicht gelöscht ist, antwortet mit 409 (`urn:oea:problem:diagram-not-deleted`).
- Ein vergebener Name antwortet mit 422 und dem Problemtyp `urn:oea:problem:diagram-name-taken`; sonstige ungültige Eingaben antworten mit 422 und nennen das Feld in `invalidFields` (`name`, `description` oder `viewpointId`). Ein unbekanntes oder gelöschtes Diagramm antwortet mit 404.
- Jede Änderung steht im Audit-Trail als `diagram.created`, `diagram.updated`, `diagram.deleted` oder `diagram.restored`.

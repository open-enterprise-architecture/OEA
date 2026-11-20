---
title: Diagramme
covers:
  screens: [SCR-021, SCR-067]
  stories: [US-074]
---

# Diagramme

Ein Diagramm modelliert einen Geschäftsprozess oder eine andere Sicht auf die Architektur mit der Notation eines [Viewpoints](../administration/metamodel-editing#viewpoints), zum Beispiel *Process Model (BPMN 2.0)*. Du legst Diagramme als Objekte an, findest, benennst um, löschst und stellst sie wieder her, und du platzierst die Elemente des Viewpoints auf der Zeichenfläche eines Diagramms.

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

## Die Zeichenfläche

Die Seite eines Diagramms zeigt Name, Beschreibung und Viewpoint und darunter die Zeichenfläche mit den Formen des Diagramms. Jede Form ist ein Kasten mit dem Namen der Entität, der Abkürzung ihres Typs und, falls der Viewpoint eine vorgibt, einem Rand in der Farbe ihres Typs. **Eine Form ist eine Ansicht einer Entität: Die Entität selbst bleibt im Repository.** Dieselbe Entität darf in einem Diagramm mehrfach vorkommen. Ein Diagramm zeigt nur Entitäten der Typen, die sein Viewpoint erlaubt. Notationsspezifische Formen, zum Beispiel die von BPMN, folgen in einer späteren Version; bis dahin ist jede Form ein Kasten.

Verschiebe die Zeichenfläche, indem du den Hintergrund ziehst, und zoome mit dem Mausrad oder mit den Schaltflächen *Vergrößern*, *Verkleinern* und *Ansicht einpassen* über der Zeichenfläche. Mit der Berechtigung `entity:read` kannst du das Diagramm so ansehen; die Zeichenfläche ist dann mit *Nur lesen* markiert und hat keine Palette, kein *Vorhandene hinzufügen* und kein *Speichern*.

Mit der Berechtigung `entity:write` bearbeitest du auch das Layout:

- **Neue Entität platzieren:** Die Palette über der Zeichenfläche listet die Entitätstypen, die der Viewpoint erlaubt. Klicke einen Typ an, um eine neue Form in der Mitte der sichtbaren Zeichenfläche zu platzieren, oder ziehe den Typ auf die Zeichenfläche. Tippe den Namen in die neue Form und drücke Enter. Ohne Namen verschwindet die Form wieder (auch mit Escape). Die Form erhält die Standardgröße ihres Typs im Viewpoint in Punkt (pt); ohne gespeicherte Größe sind das 90 × 30 pt.
- **Vorhandene Entität platzieren:** *Vorhandene hinzufügen* öffnet eine Suche. Tippe einen Teil des Namens ein (ohne Unterscheidung von Groß- und Kleinschreibung; Zeichen wie `%` und `_` gelten, wie getippt) und wähle die Entität; *Mehr laden* zeigt weitere Treffer. Die Suche findet nur Entitäten der Typen, die der Viewpoint erlaubt. Die neue Form erscheint in der Mitte der sichtbaren Zeichenfläche.
- **Verschieben und Größe ändern:** Ziehe eine Form, und ziehe die Anfasser einer ausgewählten Form, um ihre Größe zu ändern. Mit der Tastatur wählst du eine Form (Tab oder Klick) und verschiebst sie mit den Pfeiltasten um 1 pt, mit Umschalt um 10 pt. Positionen liegen innerhalb von ±100 000 pt, Größen zwischen 1 und 2000 pt, jeweils mit höchstens zwei Nachkommastellen.
- **Aus Diagramm entfernen:** Wähle eine Form und dann *Aus Diagramm entfernen* über der Zeichenfläche, drücke Entf oder öffne mit der rechten Maustaste das Kontextmenü der Form. Die Form verschwindet aus dem Diagramm; **die Entität bleibt im Repository.**
- **Details einer Entität:** Wähle eine Form, um ihre Details neben der Zeichenfläche zu öffnen (siehe [Details einer Entität](#details-of-an-entity)).
- **Speichern:** *Speichern* sichert alle Änderungen in einem Schritt, auch die Änderungen von Entitäten in den Details. Bis dahin zeigt die Zeichenfläche *Ungespeicherte Änderungen*, und neue oder geänderte Formen tragen einen kleinen Punkt. Ein Diagramm enthält höchstens 2000 Formen, und ein Speichern legt höchstens 500 neue Entitäten an; die Zeichenfläche meldet, wenn eine Grenze erreicht ist.

Verlässt du die Seite mit ungespeicherten Änderungen, fragt OEA, ob sie verworfen werden sollen. Hat jemand anderes (oder du in einem anderen Fenster) das Diagramm geändert, seit du es geöffnet hast, antwortet *Speichern* mit *Jemand anderes hat das Diagramm geändert*; wähle *Neu laden und meine Änderungen verwerfen*, um mit dem aktuellen Stand weiterzuarbeiten, oder *Weiter bearbeiten*, um deine Arbeit zuvor anderswo zu sichern. Auch das Umbenennen des Diagramms zählt als Änderung.

Lässt sich eine Form nicht speichern, zum Beispiel weil der Viewpoint ihren Typ nicht mehr erlaubt oder ihre Entität inzwischen gelöscht wurde, markiert die Zeichenfläche die Form mit dem Grund und behält alle deine Änderungen; gespeichert wird erst, wenn jede Form gültig ist.

### Entitäten mit fehlenden Werten

Eine neue Entität von der Zeichenfläche hat nur einen Namen und einen Typ. Hat ihr Typ eine Pflichteigenschaft ohne Standardwert, wird die Entität trotzdem angelegt und gilt als **unvollständig**, bis der Wert geschrieben ist. Jede Form einer unvollständigen Entität zeigt *Unvollständig:* mit den Namen der fehlenden Eigenschaften. Andere Änderungen der Entität, zum Beispiel über die Batch-API, verlangen die Pflichtwerte weiterhin. Du vervollständigst die Entität in ihren Details (siehe unten) oder schreibst die Werte über die API (siehe [API-Referenz](../api/)).

Wird eine Entität gelöscht, sind ihre Formen im Diagramm ausgeblendet; sie erscheinen wieder, wenn die Entität wiederhergestellt wird.

## Details einer Entität {#details-of-an-entity}

Wähle eine Form, um das Detailfenster ihrer Entität neben der Zeichenfläche zu öffnen. Es zeigt Namen, Beschreibung, Typ und Version der Entität und für jede Eigenschaft ihres Typs ein Feld, in der Reihenfolge des Typs; eine Pflichteigenschaft ist markiert. Schließe das Fenster mit der Schaltfläche zum Schließen oder mit Escape; mit Tab gelangst du von der gewählten Form in das Fenster.

- **Lesen:** Jede Person mit `entity:read` sieht die Werte. Einen Wert, den du nicht lesen darfst, zum Beispiel einen, der auf eine Rolle beschränkt ist, zeigt das Fenster als *Eingeschränkt*. Ohne `entity:write` ist das Fenster nur lesbar.
- **Ändern:** Mit `entity:write` änderst du den Namen, die Beschreibung und die Werte der Eigenschaften, die du schreiben darfst. Eine Eigenschaft, die auf eine Rolle beschränkt ist, die du nicht hast, zeigt ihren Wert ohne Eingabefeld. Bei einer Referenzeigenschaft suchst du die Entitäten des referenzierten Typs nach Namen. Eine leere Eingabe entfernt den Wert.
- **Unvollständige Entität vervollständigen:** Das Fenster nennt die fehlenden Pflichtwerte (*Pflichtwerte fehlen: …*). Trage sie ein und wähle *Speichern*; die Form verliert dann die Markierung *Unvollständig:*. Eine Pflichteigenschaft, die du nicht ändern darfst, bleibt Pflicht; die Entität bleibt unvollständig, bis jemand mit Schreibrecht sie schreibt.
- **Speichern:** Deine Änderungen in den Details zählen als ungespeicherte Änderungen des Diagramms, mit der Markierung *Ungespeicherte Änderungen*, und werden mit dem Layout durch *Speichern* in einem Schritt gesichert: Entweder wird alles gespeichert oder nichts. Ein Speichern ändert höchstens 500 Entitäten; die Zeichenfläche meldet, wenn die Grenze erreicht ist. Wer eine noch unvollständige Entität ändert, braucht ihre Pflichtwerte; vervollständige sie also im selben Schritt.
- **Neue Entität:** Auch die Details einer neuen Form nehmen eine Beschreibung und Werte auf; sie werden mit der Entität angelegt.

Hat jemand anderes eine Entität geändert, seit du ihre Details geöffnet hast, antwortet *Speichern* mit *… wurde zwischenzeitlich von jemand anderem geändert* unter Nennung der Entität und speichert nichts. Deine Änderungen bleiben auf der Zeichenfläche; wähle *… neu laden und meine Änderungen daran verwerfen*, um die Entität erneut zu lesen (das verwirft nur deine Änderungen an dieser Entität), wiederhole dann deine Änderung und speichere erneut. Eine Entität mit offenen Punkten einer Lösung lässt sich nicht ändern; die Meldung nennt die Entität und ihre offenen Punkte, und deine Änderungen bleiben. Einen Wert, den der Server ablehnt, markiert das Fenster am Feld mit dem Grund.

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
- `GET /views/diagrams/{id}` liefert die Ansicht eines Diagramms: das Diagramm mit seiner Version (`ETag`), seinen Viewpoint mit den wirksamen Standardgrößen und die Formen in Zeichenreihenfolge (die letzte liegt oben) mit Form-ID, Entität, Position und Größe in pt sowie `missingMandatory`, den Namen der Pflichteigenschaften ohne Wert. Formen einer gelöschten Entität fehlen.
- `PUT /views/diagrams/{id}` speichert das Layout mit `If-Match`: alle `shapes` in Zeichenreihenfolge (`id`, genau eines von `entityId` und `newEntityKey`, `x`, `y`, `width`, `height`) und die `newEntities` (`key`, `metatypeId`, `name`), auf die die Formen mit `newEntityKey` verweisen. Das Speichern ist atomar und erhöht die Version. Neue Entitäten werden wie Entitäten des Batch-Schreibens angelegt (`entity.created`), eine Entität mit fehlender Pflichteigenschaft wird aber unvollständig angelegt statt abgelehnt. Grenzen: 2000 Formen, 500 neue Entitäten, Positionen innerhalb von ±100 000 pt, Größen 1 bis 2000 pt, höchstens zwei Nachkommastellen, ein Body von 2 MB; Verstöße werden mit 400 oder 413 beantwortet. Eine fehlerhafte Form oder neue Entität wird mit 422 samt `invalidFields` und `errors` beantwortet (zum Beispiel `shapes[3].entityId` oder `newEntities[1].name`); dann wird nichts gespeichert. Auch eine Form-ID eines anderen Diagramms wird abgelehnt.
- `GET /views/diagrams/{id}/entities` durchsucht die Entitäten, die der Viewpoint erlaubt (`q` bis 255 Zeichen, `metatypeId`, `page`, `pageSize` 1 bis 200); ein Metatyp, den der Viewpoint nicht erlaubt, wird mit 422 und `metatypeId` beantwortet.
- Jedes gespeicherte Layout steht im Audit-Trail als `diagram.layout-saved` mit den Anzahlen hinzugefügter, geänderter und entfernter Formen und angelegter Entitäten.
- `GET /views/diagrams/{id}/entities/{entityId}` liefert die Details einer Entität, die eine Form des Diagramms zeigt: `name`, `description`, `metatype`, `version`, die Werte in `properties` (ein Wert, den du nicht lesen darfst, hat `redacted: true`), in `fields` für jede zugeordnete Eigenschaft, ob du sie lesen (`readable`) und ändern (`writable`) darfst, und `missingMandatory`. Eine Entität ohne Form im Diagramm, eine gelöschte Entität und ein gelöschtes Diagramm antworten mit 404. Datentypen, Aufzählungswerte und Grenzen der Eigenschaften stammen vom Metatyp (`GET /metamodel/metatypes/{id}`).
- `GET /views/diagrams/{id}/reference-targets?propertyDefId=…` listet die Entitäten, auf die eine Referenzeigenschaft zeigen darf: die Entitäten genau des referenzierten Typs, nach Namen (`q` bis 255 Zeichen, `page`, `pageSize` 1 bis 200). Eine Eigenschaft, die keine Referenzeigenschaft ist, antwortet mit 422 und nennt `propertyDefId`.
- `PUT /views/diagrams/{id}` trägt auch `entityUpdates`, höchstens 500: Jede nennt eine `entityId`, die eine Form derselben Anfrage zeigt (einmal), die gelesene `expectedVersion` (mindestens 1) und optional `name` (nicht leer, bis 255 Zeichen), `description` (bis 2000) und `properties` (`propertyDefId` und `value`; ein leerer `value` entfernt den Wert). Nicht genannte Felder und Eigenschaften behalten ihren Wert. Die Änderungen werden in derselben Transaktion wie das Layout geschrieben, mit den Prüfungen und dem Audit einer Batch-Änderung (`entity.updated`); eine geänderte Entität braucht ihre Pflichtwerte (422 `MANDATORY_MISSING` auf `entityUpdates[i].properties`), und eine eingeschränkte Eigenschaft ohne Schreibzugriff führt für das ganze Speichern zu 403. Ein Eintrag von `newEntities` kann ebenfalls `description` und `properties` tragen. Eine Entität mit anderer aktueller Version antwortet mit 409 und dem Problemtyp `urn:oea:problem:entity-version-conflict` samt `conflicts` (`entityId` und `currentVersion` jeder veralteten Entität); eine Entität mit einem offenen Punkt einer Lösung antwortet mit 409 `urn:oea:problem:entity-blocked`. Es wird dann nichts geändert. Die Batch-API (`POST /entities/batch`) schreibt weiterhin ohne Version.

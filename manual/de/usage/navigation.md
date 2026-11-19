---
title: Navigationsbaum
covers:
  screens: [SCR-055]
  stories: [US-054, US-094, US-095, US-096, US-097, US-132, US-133, US-135, US-136]
---

# Navigationsbaum

Der Navigationsbaum ordnet Kataloge, Entitäten, Lösungen und Diagramme in Ordnern, zum Beispiel *IT Landscape > Application Inventory*, damit du findest, was du für deine Aufgabe brauchst, ohne zu suchen. Alle mit der Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`) sehen den Baum. Ändern lässt er sich mit den Berechtigungen `viewpoint:manage` und `entity:read` (Rolle `enterprise-architect`).

## Die Seitenleiste

Nach der Anmeldung hat die Seitenleiste links auf jeder Seite zwei Tabs: *Browser* und *Navigationsbaum*. *Browser* ist beim Anmelden aktiv; der gewählte Tab bleibt aktiv, während du zwischen den Seiten wechselst (siehe [Browser](#browser)). *Navigationsbaum* zeigt den folgenden Baum. Er beginnt beim Ordner *Repository*.

- **Ordner auf- und zuklappen:** Wähle den Pfeil oder den Namen des Ordners. Die Seitenleiste merkt sich die offenen Ordner, bis du den Browser-Tab schliesst.
- **Eintrag öffnen:** Wähle ihn. Ein Katalog öffnet sich mit seiner Standardansicht, falls er eine hat (siehe [Ansichten](./catalogs#views)); eine Entität öffnet ihre Seite, eine Lösung ihre Lösungsseite, ein Diagramm seine Seite.
- **Schmale Bildschirme:** Die Seitenleiste ist ausgeblendet; die Menü-Schaltfläche links in der Kopfzeile öffnet sie. Wählst du einen Eintrag, schliesst sie sich wieder.

Ordner und Einträge behalten die Reihenfolge, in der du sie anordnest (siehe [Baum umorganisieren](#reorganize-the-tree)); neue Ordner und Einträge kommen ans Ende. Ein Eintrag zeigt seinen Anzeigenamen, falls er einen hat, sonst den Namen des Katalogs, der Entität, der Lösung oder des Diagramms. Ein Symbol zeigt die Art des Eintrags, ein Personen-Symbol kennzeichnet einen persönlichen Katalog.

## Browser {#browser}

Der Tab *Browser* braucht keine Handarbeit: Er listet auf, was das Repository enthält, geordnet nach Lösungen. Alle mit der Berechtigung `entity:read` sehen ihn. Von oben nach unten zeigt er:

1. **Ist-Zustand:** die Objekte des Repositorys, wie sie jetzt sind, in den Kategorien *Komponenten* (Entitäten, die keine Verbindungen und nicht gelöscht sind), *Verbindungen*, *Kataloge* (die Kataloge, die du siehst: gemeinsame und deine eigenen persönlichen) und *Diagramme* (alle [Diagramme](./diagrams), die nicht gelöscht sind).
2. **Plateaus:** ein Knoten für jedes Plateau im Status *transition* oder *target*, geordnet nach dem Datum, ab dem es gilt, dann nach Name. Das Baseline-Plateau und realisierte Plateaus haben keinen Knoten. Unter einem Plateau stehen die Lösungen, die es als Ziel haben, nach Name sortiert.
3. **Projekte:** die Lösungen ohne Plateaus, nach Name sortiert.

Es erscheinen nur aktive Lösungen: Eine Lösung im Status *implemented* oder *archived* wird nicht aufgeführt. Die Entitäten einer umgesetzten Lösung zählen unter *Ist-Zustand*. Ein Plateau oder *Projekte* ohne Lösungen bleibt sichtbar und erscheint als *(0)*.

Jede Lösung hat die Kategorie *Komponenten*: ihre Änderungen am Repository, gekennzeichnet als *Neu*, *Geändert* oder *Auslaufend*. Jede Kategorie zeigt, wie viele Objekte sie enthält. Eine leere Kategorie ist ausgegraut, nicht ausgeblendet.

- **Knoten aufklappen:** Wähle den Pfeil oder den Namen. Eine Kategorie lädt ihre Objekte, wenn du sie zum ersten Mal aufklappst; sie listet sie nach Name, je 50. Mit *Mehr laden* folgen die nächsten 50.
- **Objekt öffnen:** Eine Entität öffnet ihre Änderungshistorie, ein Katalog seine Seite, eine Änderung einer Lösung (und das Link-Symbol neben einer Lösung) die Lösungsseite.
- **Metatyp:** Ein kleines Label neben einer Entität zeigt die Abkürzung ihres Metatyps, falls er eine hat.
- **Notation:** Ein kleines Label neben einem Diagramm zeigt die Notation seines Viewpoints, zum Beispiel *bpmn2*. *Diagramme* listet sie nach Name, unabhängig von Gross- und Kleinschreibung, je 50; eine Lösung hat keine Kategorie *Diagramme*.

*Komponenten* des *Ist-Zustands* listet die oberste Ebene: die Entitäten ohne Eltern. Eine Entität mit Kindern zeigt einen Pfeil und klappt zu ihnen auf, nach Name, je 50. Wie du eine Komponente unter eine andere einordnest, steht unter [Komponenten anordnen](#arrange-components). Komponenten einer Lösung bleiben eine flache Liste.

Die Ordner des Baums ordnest du im Tab *Navigationsbaum*. Funktionale und Lösungs-Bausteine folgen mit ihren Inhalten.

## Komponenten anordnen {#arrange-components}

Reale Architekturen sind hierarchisch: Das Modul *Finanzmodul* gehört zum System *SAP S/4HANA*. Unter *Ist-Zustand > Komponenten* ordnest du eine Komponente unter einer anderen ein. Das ändert die Entität selbst im Katalog, nicht nur die Seitenleiste: Es schreibt eine neue Version des Kindes mit dem Feld *Parent* in seiner [Änderungshistorie](./change-history), und es braucht die Berechtigung `entity:write` (Rollen `enterprise-architect`, `solution-architect`). Alle anderen sehen die Hierarchie, können sie aber nicht ändern.

- **Ziehen und Ablegen:** Ziehe eine Komponente auf eine andere Komponente oder auf *Komponenten*, um sie auf der obersten Ebene einzuordnen. Ein Ziel, das sie nicht aufnehmen kann (die Komponente selbst, eines ihrer in der Seitenleiste gezeigten Kinder), zeigt *Kann hier nicht abgelegt werden*, und es wird nichts gesendet.
- **Ohne Maus:** Die Schaltfläche *…* neben einer Komponente bietet *Verschieben nach…*. Der Dialog listet die Komponenten Ebene für Ebene; mit dem Pfeil siehst du die Kinder einer Komponente, mit dem Filter engst du die geladenen Namen ein, *Oberste Ebene* wählt die oberste Ebene, und *Hierher* wählt das Ziel.
- **Regeln:** Die Eltern sind eine andere Komponente, die nicht gelöscht ist. Ein Zyklus (eine Komponente unter ihrem eigenen Kind) und mehr als 10 Ebenen werden mit *Die Komponente kann dort nicht eingeordnet werden.* abgelehnt. Eine Komponente mit einem offenen Problem einer Lösung lässt sich nicht ändern. Hat jemand die Komponente zwischenzeitlich geändert, sagt das die Seitenleiste und liest die Liste neu; versuche es dann erneut.

Kennzeichnet das Metamodell einen [Kompositions-Verbindungstyp](../administration/metamodel-editing#composition), legt das Einordnen einer Komponente unter eine andere auch eine Verbindung dieses Typs von den Eltern zum Kind an, wenn der Typ beide Entitätstypen erlaubt. Verschieben oder Lösen der Komponente löscht die so angelegte Verbindung; Verbindungen, die du selbst geschrieben hast, bleiben. *Verbindungen* zählt eine Verbindung mehr.

### Ordnungshilfen {#structuring-aids}

Eine Ordnungshilfe ist ein Ordner in der Hierarchie ohne Bedeutung: Sie gruppiert Komponenten für die Navigation, zum Beispiel *Finanzlandschaft*, ohne dem Modell eine Verbindung hinzuzufügen. Sie hat ein Ordnersymbol, keine Eigenschaften und keine Verbindungen und gehört zu keiner Lösung.

- **Neue Ordnungshilfe:** Die Schaltfläche neben *Komponenten* legt eine auf der obersten Ebene an; der Eintrag *Neue Ordnungshilfe* einer Komponente legt eine darunter an. Gib einen Namen mit 1 bis 255 Zeichen und optional eine Beschreibung mit bis zu 2000 Zeichen ein. Das braucht `entity:write`.
- **Löschen:** Der Eintrag *Löschen* einer Ordnungshilfe braucht `entity:write` und `entity:delete` (Rolle `enterprise-architect`). Der Dialog sagt zuerst, wie viele Kinder auf die oberste Ebene verschoben werden; sie werden nicht gelöscht. Eine Ordnungshilfe mit einem Kind, das ein offenes Problem hat, lässt sich nicht löschen.

## Ordner

Mit den Berechtigungen zum Ändern des Baums bietet die Schaltfläche *…* neben einem Ordner:

- **Neuer Ordner:** legt einen Ordner darin an. Gib einen Namen ein und wähle *Erstellen*.
- **Umbenennen:** ändert den Namen des Ordners, auch des Ordners *Repository*. Seine Einträge und Unterordner bleiben.
- **Nach oben**, **Nach unten** und **Verschieben nach…:** siehe [Baum umorganisieren](#reorganize-the-tree).
- **Löschen:** fragt zuerst nach und nennt, wie viele Unterordner und Einträge das Löschen entfernt. Der Ordner wird mit allen Unterordnern und Einträgen gelöscht. **Kein Katalog, keine Entität und keine Lösung wird gelöscht**; sie bleiben verfügbar und in anderen Ordnern. Der Ordner *Repository* lässt sich nicht löschen.

Ein Ordnername hat 1 bis 255 Zeichen und ist in seinem Ordner eindeutig, unabhängig von Gross- und Kleinschreibung; sonst meldet der Dialog *Ein Ordner mit diesem Namen existiert bereits*. Der Baum hat höchstens 10 Ebenen unter *Repository* und höchstens 500 Ordner.

## Katalog, Entität, Lösung oder Diagramm einordnen {#place-a-diagram}

1. Wähle *In Navigation einordnen*:
   - für einen Katalog auf der Seite des Katalogs;
   - für eine Entität in ihrer Zeile eines Katalogs (nicht in Katalogen von Verbindungen);
   - für eine Lösung in der Kopfzeile der Lösungsseite;
   - für ein Diagramm in der Kopfzeile der Seite des Diagramms.
2. Wähle unter *Ordner* den Ordner über seinen Pfad, zum Beispiel *Repository / IT Landscape*.
3. Gib bei Bedarf einen *Anzeigenamen* ein. Der Baum zeigt ihn dann statt des Namens, zum Beispiel *System Inventory* für *Application Inventory*. Katalog, Entität oder Lösung behalten ihren Namen.
4. Wähle *Einordnen*.

Jedes davon kann in mehreren Ordnern stehen, aber nur einmal pro Ordner. Ein Ordner hat höchstens 200 Einträge. Enthält der Ordner es bereits oder ist er voll, meldet der Dialog das, zum Beispiel *Der Katalog ist bereits in diesem Ordner, oder der Ordner ist voll*. Ein gelöschtes Diagramm lässt sich nicht einordnen; seine Einträge im Baum bleiben verborgen, bis das Diagramm [wiederhergestellt](./diagrams#diagramm-wiederherstellen) wird.

## Baum umorganisieren {#reorganize-the-tree}

Mit den Berechtigungen zum Ändern des Baums ordnest du Ordner und Einträge in der Seitenleiste an:

- **Ziehen und Ablegen:** Ziehe einen Ordner oder Eintrag an eine andere Stelle im selben Ordner oder in einen anderen Ordner. Wo er nicht hin kann, zeigt die Seitenleiste *Kann hier nicht abgelegt werden*, zum Beispiel bei einem Ordner, der in seinen eigenen Unterordner gezogen wird.
- **Ohne Maus:** Die Schaltfläche *…* neben einem Ordner oder Eintrag bietet *Nach oben*, *Nach unten* und *Verschieben nach…*. *Verschieben nach…* fragt nach dem Zielordner und verschiebt ans Ende dieses Ordners.

Eine Verschiebung hält die Regeln des Baums ein: höchstens 10 Ebenen, höchstens 200 Einträge pro Ordner, eindeutige Ordnernamen und ein Eintrag nur einmal pro Ordner. Umsortieren innerhalb eines vollen Ordners ist möglich. Sonst meldet die Seitenleiste *Das kann nicht dorthin verschoben werden. Der Baum hat sich eventuell inzwischen geändert.* Gibt es einen Ordner oder Eintrag nicht mehr, weil jemand anderes den Baum inzwischen geändert hat, liest die Seitenleiste den aktuellen Baum neu.

## Eintrag aus dem Baum entfernen

Die Schaltfläche *×* neben einem Eintrag entfernt ihn aus diesem Ordner. Nur dieser Eintrag wird entfernt: Katalog, Entität oder Lösung selbst und ihre Einträge in anderen Ordnern bleiben.

## Gelöschte Entitäten

Wird eine Entität gelöscht, bleiben ihre Einträge im Baum und zeigen die Markierung *Gelöscht*. Entferne sie mit *×*, oder stelle die Entität wieder her; dann verschwindet die Markierung.

## Persönliche Kataloge

Deinen eigenen persönlichen Katalog kannst du wie einen geteilten einordnen. Nur du siehst seinen Eintrag: Der Baum zeigt nie die persönlichen Kataloge anderer, er führt also nie zu einem Katalog, den du nicht öffnen kannst. Teilst du den Katalog später, sehen alle den Eintrag.

Jede Änderung des Baums, auch jede Verschiebung, wird im Audit-Trail festgehalten.

## Über die API

- `GET /views/browser` liefert die oberste Ebene des Browsers: `currentState` mit den Zahlen `components`, `connections`, `catalogs` und `diagrams`, `plateaus` (jedes mit seinen `solutions`) und `projects`; eine Lösung hat `id`, `name`, `status` und die Zahl ihrer Änderungen in `components`.
- `GET /views/browser/items` liefert eine Seite einer Kategorie, nach Name geordnet: `scope` (`current-state` oder `solution`), `category` (`components`, `connections`, `catalogs` oder `diagrams`; eine Lösung hat nur `components`; ein Eintrag eines Diagramms hat die `notation` seines Viewpoints), `solutionId` für eine Lösung sowie `page` und `pageSize` (1 bis 200, Standard 50). Eine `pageSize` ausserhalb von 1 bis 200 wird mit 400 abgewiesen; eine Kategorie, die der Bereich nicht hat, oder eine `solutionId`, die nicht zum Bereich passt, mit 422 und dem Namen `category` oder `solutionId`; eine unbekannte Lösung mit 404.
- `GET /views/explorer` liefert den Baum ab der Wurzel so, wie du ihn siehst, in seiner manuellen Reihenfolge: jeden Ordner mit `id`, `name`, `parentId`, `sortOrder`, `items` und `children`; jeden Eintrag mit `itemType` (`catalog`, `entity`, `solution` oder `diagram`), `referenceId` (Katalog-, Lösungs- oder Diagramm-ID) oder `entityId`, `displayLabel`, `name`, `scope` und `deleted`.
- `POST /tree-nodes` mit `name` und `parentId` legt einen Ordner an; `PATCH /tree-nodes/{nodeId}` mit `name` benennt ihn um; `DELETE /tree-nodes/{nodeId}` löscht ihn.
- `POST /tree-nodes/{nodeId}/items` mit `itemType`, `referenceId` oder `entityId` und einem optionalen `displayLabel` ordnet einen Eintrag ein; `DELETE /tree-nodes/{nodeId}/items/{itemId}` entfernt ihn.
- `PUT /tree-nodes/{nodeId}/position` mit `parentId` und `position` verschiebt einen Ordner; `PUT /tree-nodes/{nodeId}/items/{itemId}/position` mit `nodeId` und `position` verschiebt einen Eintrag. Die Position zählt die Einträge, die du siehst, ab 0.

Jede Änderung antwortet mit dem ganzen Baum. Ungültige Eingaben werden mit 422 abgelehnt und nennen das Feld in `invalidFields`, zum Beispiel `name` für einen vergebenen Ordnernamen, `parentId` für eine Verschiebung, die einen Zyklus bilden oder zu tief gehen würde, oder `nodeId` für einen Zielordner, der den Eintrag schon enthält oder voll ist.

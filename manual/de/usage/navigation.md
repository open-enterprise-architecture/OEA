---
title: Navigationsbaum
covers:
  screens: [SCR-055]
  stories: [US-054, US-094, US-095, US-096, US-097]
---

# Navigationsbaum

Der Navigationsbaum ordnet Kataloge, Entitäten und Lösungen in Ordnern, zum Beispiel *IT Landscape > Application Inventory*, damit du findest, was du für deine Aufgabe brauchst, ohne zu suchen. Alle mit der Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`) sehen den Baum. Ändern lässt er sich mit den Berechtigungen `viewpoint:manage` und `entity:read` (Rolle `enterprise-architect`).

## Die Seitenleiste

Nach der Anmeldung zeigt die Seitenleiste links auf jeder Seite den Baum. Er beginnt beim Ordner *Repository*.

- **Ordner auf- und zuklappen:** Wähle den Pfeil oder den Namen des Ordners. Die Seitenleiste merkt sich die offenen Ordner, bis du den Browser-Tab schliesst.
- **Eintrag öffnen:** Wähle ihn. Ein Katalog öffnet sich mit seiner Standardansicht, falls er eine hat (siehe [Ansichten](./catalogs#views)); eine Entität öffnet ihre Seite, eine Lösung ihre Lösungsseite.
- **Schmale Bildschirme:** Die Seitenleiste ist ausgeblendet; die Menü-Schaltfläche links in der Kopfzeile öffnet sie. Wählst du einen Eintrag, schliesst sie sich wieder.

Ordner und Einträge behalten die Reihenfolge, in der du sie anordnest (siehe [Baum umorganisieren](#reorganize-the-tree)); neue Ordner und Einträge kommen ans Ende. Ein Eintrag zeigt seinen Anzeigenamen, falls er einen hat, sonst den Namen des Katalogs, der Entität oder der Lösung. Ein Symbol zeigt die Art des Eintrags, ein Personen-Symbol kennzeichnet einen persönlichen Katalog.

## Ordner

Mit den Berechtigungen zum Ändern des Baums bietet die Schaltfläche *…* neben einem Ordner:

- **Neuer Ordner:** legt einen Ordner darin an. Gib einen Namen ein und wähle *Erstellen*.
- **Umbenennen:** ändert den Namen des Ordners, auch des Ordners *Repository*. Seine Einträge und Unterordner bleiben.
- **Nach oben**, **Nach unten** und **Verschieben nach…:** siehe [Baum umorganisieren](#reorganize-the-tree).
- **Löschen:** fragt zuerst nach und nennt, wie viele Unterordner und Einträge das Löschen entfernt. Der Ordner wird mit allen Unterordnern und Einträgen gelöscht. **Kein Katalog, keine Entität und keine Lösung wird gelöscht**; sie bleiben verfügbar und in anderen Ordnern. Der Ordner *Repository* lässt sich nicht löschen.

Ein Ordnername hat 1 bis 255 Zeichen und ist in seinem Ordner eindeutig, unabhängig von Gross- und Kleinschreibung; sonst meldet der Dialog *Ein Ordner mit diesem Namen existiert bereits*. Der Baum hat höchstens 10 Ebenen unter *Repository* und höchstens 500 Ordner.

## Katalog, Entität oder Lösung einordnen

1. Wähle *In Navigation einordnen*:
   - für einen Katalog auf der Seite des Katalogs;
   - für eine Entität in ihrer Zeile eines Katalogs (nicht in Katalogen von Verbindungen);
   - für eine Lösung in der Kopfzeile der Lösungsseite.
2. Wähle unter *Ordner* den Ordner über seinen Pfad, zum Beispiel *Repository / IT Landscape*.
3. Gib bei Bedarf einen *Anzeigenamen* ein. Der Baum zeigt ihn dann statt des Namens, zum Beispiel *System Inventory* für *Application Inventory*. Katalog, Entität oder Lösung behalten ihren Namen.
4. Wähle *Einordnen*.

Jedes davon kann in mehreren Ordnern stehen, aber nur einmal pro Ordner. Ein Ordner hat höchstens 200 Einträge. Enthält der Ordner es bereits oder ist er voll, meldet der Dialog das, zum Beispiel *Der Katalog ist bereits in diesem Ordner, oder der Ordner ist voll*.

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

- `GET /views/explorer` liefert den Baum ab der Wurzel so, wie du ihn siehst, in seiner manuellen Reihenfolge: jeden Ordner mit `id`, `name`, `parentId`, `sortOrder`, `items` und `children`; jeden Eintrag mit `itemType` (`catalog`, `entity` oder `solution`), `referenceId` (Katalog- oder Lösungs-ID) oder `entityId`, `displayLabel`, `name`, `scope` und `deleted`.
- `POST /tree-nodes` mit `name` und `parentId` legt einen Ordner an; `PATCH /tree-nodes/{nodeId}` mit `name` benennt ihn um; `DELETE /tree-nodes/{nodeId}` löscht ihn.
- `POST /tree-nodes/{nodeId}/items` mit `itemType`, `referenceId` oder `entityId` und einem optionalen `displayLabel` ordnet einen Eintrag ein; `DELETE /tree-nodes/{nodeId}/items/{itemId}` entfernt ihn.
- `PUT /tree-nodes/{nodeId}/position` mit `parentId` und `position` verschiebt einen Ordner; `PUT /tree-nodes/{nodeId}/items/{itemId}/position` mit `nodeId` und `position` verschiebt einen Eintrag. Die Position zählt die Einträge, die du siehst, ab 0.

Jede Änderung antwortet mit dem ganzen Baum. Ungültige Eingaben werden mit 422 abgelehnt und nennen das Feld in `invalidFields`, zum Beispiel `name` für einen vergebenen Ordnernamen, `parentId` für eine Verschiebung, die einen Zyklus bilden oder zu tief gehen würde, oder `nodeId` für einen Zielordner, der den Eintrag schon enthält oder voll ist.

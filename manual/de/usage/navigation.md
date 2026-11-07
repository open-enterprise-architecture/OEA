---
title: Navigationsbaum
covers:
  screens: [SCR-055]
  stories: [US-054]
---

# Navigationsbaum

Der Navigationsbaum ordnet Kataloge in Ordnern, zum Beispiel *IT Landscape > Application Inventory*, damit du den Katalog für deine Aufgabe findest, ohne die Katalogliste zu durchsuchen. Alle mit der Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`) sehen den Baum. Ändern lässt er sich mit der Berechtigung `viewpoint:manage` (Rolle `enterprise-architect`).

## Die Seitenleiste

Nach der Anmeldung zeigt die Seitenleiste links auf jeder Seite den Baum. Er beginnt beim Ordner *Repository*.

- **Ordner auf- und zuklappen:** Wähle den Pfeil oder den Namen des Ordners. Die Seitenleiste merkt sich die offenen Ordner, bis du den Browser-Tab schliesst.
- **Katalog öffnen:** Wähle seinen Eintrag. Der Katalog öffnet sich mit seiner Standardansicht, falls er eine hat (siehe [Ansichten](./catalogs#views)).
- **Schmale Bildschirme:** Die Seitenleiste ist ausgeblendet; die Menü-Schaltfläche links in der Kopfzeile öffnet sie. Wählst du einen Katalog, schliesst sie sich wieder.

Ordner und Kataloge sind nach Namen sortiert, unabhängig von Gross- und Kleinschreibung. Ein Eintrag zeigt seinen Anzeigenamen, falls er einen hat, sonst den Namen des Katalogs. Ein Personen-Symbol kennzeichnet einen persönlichen Katalog.

## Ordner

Mit `viewpoint:manage` bietet die Schaltfläche *…* neben einem Ordner:

- **Neuer Ordner:** legt einen Ordner darin an. Gib einen Namen ein und wähle *Erstellen*.
- **Umbenennen:** ändert den Namen des Ordners, auch des Ordners *Repository*. Seine Einträge und Unterordner bleiben.
- **Löschen:** fragt zuerst nach und nennt, wie viele Unterordner und Einträge das Löschen entfernt. Der Ordner wird mit allen Unterordnern und Einträgen gelöscht. **Kein Katalog wird gelöscht**; die Kataloge bleiben in der Katalogliste und in anderen Ordnern. Der Ordner *Repository* lässt sich nicht löschen.

Ein Ordnername hat 1 bis 255 Zeichen und ist in seinem Ordner eindeutig, unabhängig von Gross- und Kleinschreibung; sonst meldet der Dialog *Ein Ordner mit diesem Namen existiert bereits*. Der Baum hat höchstens 10 Ebenen unter *Repository* und höchstens 500 Ordner.

## Katalog einordnen

1. Öffne den Katalog und wähle *In Navigation einordnen* (braucht `viewpoint:manage`).
2. Wähle unter *Ordner* den Ordner über seinen Pfad, zum Beispiel *Repository / IT Landscape*.
3. Gib bei Bedarf einen *Anzeigenamen* ein. Der Baum zeigt ihn dann statt des Katalognamens, zum Beispiel *System Inventory* für *Application Inventory*. Der Katalog behält seinen Namen.
4. Wähle *Einordnen*.

Ein Katalog kann in mehreren Ordnern stehen, aber nur einmal pro Ordner; ein zweites Einordnen in denselben Ordner meldet *Der Katalog ist bereits in diesem Ordner*. Ein Ordner hat höchstens 200 Einträge.

## Katalog aus dem Baum entfernen

Mit `viewpoint:manage` entfernt die Schaltfläche *×* neben einem Eintrag diesen aus dem Ordner. Nur dieser Eintrag verschwindet: Der Katalog selbst und seine Einträge in anderen Ordnern bleiben.

## Persönliche Kataloge

Deinen eigenen persönlichen Katalog kannst du wie einen geteilten einordnen. Nur du siehst seinen Eintrag: Der Baum zeigt nie persönliche Kataloge anderer Personen und führt so nie zu einem Katalog, den du nicht öffnen kannst. Teilst du den Katalog später, sehen alle den Eintrag.

Jede Änderung des Baums wird im Audit-Trail festgehalten.

## Über die API

- `GET /views/explorer` liefert den Baum ab der Wurzel, so wie du ihn siehst: jeden Ordner mit `id`, `name`, `parentId`, `items` und `children`; jeden Eintrag mit `referenceId` (der Katalog-ID), `displayLabel`, `name` (dem aktuellen Namen des Katalogs) und `scope`.
- `POST /tree-nodes` mit `name` und `parentId` legt einen Ordner an; `PATCH /tree-nodes/{nodeId}` mit `name` benennt ihn um; `DELETE /tree-nodes/{nodeId}` löscht ihn.
- `POST /tree-nodes/{nodeId}/items` mit `itemType: catalog`, `referenceId` und optional `displayLabel` ordnet einen Katalog ein; `DELETE /tree-nodes/{nodeId}/items/{itemId}` entfernt den Eintrag.

Jede Änderung antwortet mit dem ganzen Baum. Ungültige Eingaben werden mit 422 abgelehnt und nennen das Feld in `invalidFields`, zum Beispiel `name` für einen vergebenen Ordnernamen oder `referenceId` für einen zweiten Eintrag im selben Ordner.

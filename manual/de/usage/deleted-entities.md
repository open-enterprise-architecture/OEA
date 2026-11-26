---
title: Gelöschte Entitäten
covers:
  screens: [SCR-068]
  stories: [US-074]
---

# Gelöschte Entitäten

Wenn du eine Entität aus dem Repository löschst (zum Beispiel mit *Aus Repository löschen* auf der Zeichenfläche eines [Diagramms](./diagrams#the-canvas)), entfernt OEA sie nicht: Die Entität, ihre Kinder und deren Verbindungen werden als gelöscht markiert und können wiederhergestellt werden. Die Seite *Gelöschte Entitäten* listet sie auf. Sie braucht die Berechtigung `entity:restore` (Rolle `enterprise-architect`); ohne sie hat die Kopfzeile keinen Eintrag *Gelöschte Entitäten*, und die Seite zeigt *Keine Berechtigung*.

## Gelöschte Entitäten finden

Wähle in der Kopfzeile *Gelöschte Entitäten*. Die Seite listet die gelöschten Entitäten und Verbindungen, die zuletzt gelöschten zuerst. Jeder Eintrag zeigt:

- den Namen und bei einer Verbindung ihre Quelle und ihr Ziel (auch wenn diese selbst gelöscht sind), den Entitätstyp sowie Person und Zeitpunkt des Löschens;
- *Gelöscht mit …* und den Namen einer Entität, wenn der Eintrag mitgenommen wurde, als diese Entität gelöscht wurde, zum Beispiel ein Kind oder eine Verbindung von ihr.

Gib einen Namensteil in *Nach Name filtern* ein (bis zu 255 Zeichen; Zeichen wie `%` und `_` gelten, wie eingegeben) und wähle einen Entitätstyp, um die Liste einzugrenzen. Die Liste zeigt 50 Einträge auf einmal; wähle *Mehr laden* für die nächsten. Ohne gelöschte Entität meldet die Seite *Keine gelöschten Entitäten.*

## Eine Entität wiederherstellen {#restore-an-entity}

1. Wähle *Wiederherstellen* an einem Eintrag.
2. Bestätige den Dialog. Die Entität kommt zusammen mit dem zurück, was ihre Löschung mitgenommen hat: ihre Kinder und deren Verbindungen, die mit ihr gelöscht wurden.

Danach nennt ein Hinweis die Entitäten, die wiederhergestellt wurden, und die, die gelöscht geblieben sind. Eine Verbindung bleibt gelöscht, wenn ihr anderer Endpunkt noch gelöscht ist; stelle auch diese Entität wieder her und danach die Verbindung. Ein Element, dessen Elternelement noch gelöscht ist, kommt auf der obersten Ebene zurück, und der Hinweis sagt das.

- **Ein einzelner Eintrag:** Ein Eintrag, der mitgenommen wurde, zum Beispiel ein Kind, kann für sich wiederhergestellt werden. Der Dialog nennt die Entität, mit der er gelöscht wurde.
- **Eine Verbindung mit gelöschtem Endpunkt:** Sie allein wiederherzustellen wird abgelehnt. Die Seite meldet das; stelle zuerst den Endpunkt wieder her.
- **Jemand war schneller:** Wurde die Entität inzwischen wiederhergestellt oder geändert, meldet die Seite das und lädt die Liste neu.

Das Wiederherstellen ändert keine Version und wird für jede wiederhergestellte Entität als `entity.undeleted` im Audit-Trail festgehalten.

## Gelöschte Zeilen in einem Katalog

Ein Katalog kann gelöschte Zeilen ebenfalls zeigen, mit dem Schalter *Gelöschte anzeigen* (siehe [Kataloge](./catalogs#show-deleted)). *Wiederherstellen* funktioniert dort genauso.

## Über die API

- `GET /entities/deleted` listet die gelöschten Entitäten und Verbindungen (`entity:restore`): die zuletzt gelöschten zuerst, dann nach ID. `q` ist ein Namensteil (höchstens 255 Zeichen), `metatypeId` ein Entitätstyp, `pageSize` ist 1 bis 200 (Standard 50) und `page` beginnt bei 0. Die Antwort enthält `nextCursor`; sende ihn als `cursor` zusammen mit `page` um 1 erhöht, um fortzufahren. Ein Cursor einer anderen Abfrage wird mit 400 abgelehnt, der `cursor` nennt; andere ungültige Werte beantwortet die API mit 400.
- Jeder Eintrag hat `id`, `name`, `metatype`, `isConnection`, bei einer Verbindung `source` und `target` (`id` und `name`), `version`, `deletedAt`, `deletedBy` und `deletedWith` (`id` und `name`, oder `null` für eine Entität, die für sich gelöscht wurde). Er enthält keine Eigenschaftswerte.
- `POST /entities/{id}/undelete` mit dem Header `If-Match` und der `version` des Eintrags stellt die Entität wieder her (siehe [Diagramme](./diagrams#through-the-api)). Die Liste wird nur gelesen und nicht auditiert.

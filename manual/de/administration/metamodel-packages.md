---
title: Metamodell-Pakete
covers:
  screens: []
  stories: [US-033, US-057]
---

# Metamodell-Pakete

Eine neue Instanz hat keine Entitätstypen. Bevor jemand Entitäten erfassen kann, importierst du ein Metamodell: das mitgelieferte Starterpaket oder ein eigenes Paket.

Der Import von Paketen verlangt die Berechtigung `metamodel:write`, die nur die Rolle `metamodel-admin` hat – nicht das lokale Systemadmin-Konto. Ordne eine IdP-Gruppe `metamodel-admin` zu (siehe [Rollen und Berechtigungen](./roles-and-permissions)); als privilegierte Rolle braucht sie einen zweiten Faktor beim Identity-Provider.

## Starterkonfiguration importieren

OEA liefert das Starterpaket `oea-starter-togaf-classic` mit den Entitätstypen Application Component, Application Service, Data Object, Technology Component, Technology Service, System Software und Network Node, jeweils mit der Eigenschaft *Status*. Version 1.4.0 fügt die vier [Schichten](./metamodel-editing#layers) *Geschäft*, *Daten*, *Anwendung* und *Technologie* (englisch *Business*, *Data*, *Application* und *Technology*) hinzu und ordnet sie den Entitätstypen zu: *Application Component* und *Application Service* der Schicht Anwendung, *Data Object* der Schicht Daten und *Technology Component*, *Technology Service*, *System Software* und *Network Node* der Schicht Technologie (*Komposition* hat keine). Der erneute Import in eine Instanz mit einer eigenen Schicht desselben Namens, zum Beispiel *Business*, wird als Konflikt abgelehnt; benenne diese Schicht zuerst um oder lösche sie. Version 1.3.0 fügt den Verbindungstyp *Komposition* hinzu, gekennzeichnet als [Komposition](./metamodel-editing#composition) der Hierarchie von Komponenten. Version 1.2.0 fügt die zwei systemdefinierten Viewpoints *Application Landscape* und *Technology Landscape* in ArchiMate hinzu (siehe [Viewpoints in Paketen](#viewpoints-in-packages)). Version 1.1.0 brachte deutsche Labels für seine Entitätstypen, *Status* und dessen Werte sowie englische Labels für die Werte mit (siehe [Labels übersetzen](./metamodel-editing#translating-labels)). Eine Instanz, die eine frühere Version importiert hat, erhält die Labels und Viewpoints durch einen erneuten Import des Pakets.

Solange die Instanz keinen Entitätstyp hat, zeigt die Startseite einer Person mit `metamodel:write` den Bereich *Startkonfiguration importieren*. Wähle *Importieren* neben dem Paket; die Seite bestätigt, wie viele Entitätstypen jetzt verfügbar sind.

<Screenshot name="starter-import" alt="Startseite mit der Starterkonfiguration zum Import" />

Über die API:

```bash
curl "${auth[@]}" "$OEA/metamodel/starter-packages"                    # Liste mit Zusammenfassung
curl "${auth[@]}" -X POST "$OEA/metamodel/starter-packages/oea-starter-togaf-classic/import"
curl "${auth[@]}" "$OEA/metamodel/metatypes"                                       # importierte Entitätstypen
```

## Eigenes Paket importieren

Eigene Pakete verwenden dasselbe JSON-Format (`MetamodelPackage`, mit `packageId`, `packageVersion`, `publisher` und stabilen UUIDs pro Element). Importiere sie mit `POST /metamodel/import`:

- Ein Paket wird vollständig oder gar nicht importiert. Importierte Inhalte bekommen den Scope `imported` und das Paket als Herkunft.
- Ein erneuter Import desselben Pakets aktualisiert dessen eigene Inhalte. Inhalte eines anderen Pakets oder deiner Organisation werden nie überschrieben; die Antwort ist `409` mit dem Problemtyp `urn:oea:problem:package-conflict` und der Liste der Konflikte in `conflictList`, und nichts vom Paket wird gespeichert.
- Ein Paket kann für einen Entitätstyp einen Basistyp nennen (`baseMetatypeId`): einen Entitätstyp desselben Pakets oder einen eingebauten. Deine eigenen Eigenschaften an seinen Entitätstypen bleiben oder werden entfernt, je nach Einstellung für den erneuten Import (siehe [Metamodell bearbeiten](./metamodel-editing)).
- Pakete, die größer als `oea.metamodel.import.max-size` sind (Standard 10 MB), werden mit `413` abgelehnt; fehlerhaftes JSON oder Inhalte, die nicht zum Format passen, mit `422` und der Stelle des Problems.
- Ein Paket kann in `i18nBundles` Labels pro Sprache mitbringen, mit Schlüsseln wie unter [Labels übersetzen](./metamodel-editing#translating-labels), aber nur für seine eigenen Elemente. Ein erneuter Import ersetzt sie; deine eigenen Labels bleiben.
- Die Strategie `replace` und Shape-Zuordnungen werden noch nicht unterstützt (`422`).

Jeder Import schreibt die Audit-Events `metamodel.package-imported` und `metamodel.metatype-imported` mit einer gemeinsamen Batch-ID.

## Komposition in Paketen {#composition-in-packages}

Ein Verbindungstyp eines Pakets kann `isComposition: true` tragen; ein Paket hat höchstens einen. Der Import wird abgelehnt, wenn die Instanz bereits einen anderen gekennzeichneten Verbindungstyp hat. Ein erneuter Import desselben Pakets verschiebt oder entfernt die Kennzeichnung mit dem Paket.

## Schichten in Paketen {#layers-in-packages}

Ein Paket kann in `architectureLayers` Architekturschichten mitbringen: jede mit einer stabilen `id`, einem `name` mit bis zu 100 Zeichen und einer optionalen `sortOrder`. Die Entitätstypen des Pakets verweisen mit `architectureLayerId` darauf. Der Import installiert sie als schreibgeschützte Schichten mit dem Paket als Herkunft (siehe [Schichten](./metamodel-editing#layers)).

- Ein Paket hat höchstens 20 Schichten, und zusammen mit den Schichten deiner Organisation hat die Instanz höchstens 20 (sonst `422`). Namen sind im Paket unabhängig von der Schreibweise eindeutig; ein Entitätstyp, der auf eine Schicht ausserhalb des Pakets verweist, wird mit `422` abgelehnt.
- Eine Schicht, deren ID oder Name zu einer Schicht deiner Organisation oder eines anderen Pakets gehört, ergibt `409` mit `package-conflict`, und nichts wird importiert.
- Neue Schichten stehen hinter den vorhandenen, in der Reihenfolge von `sortOrder`; vorhandene Schichten behalten ihre Position.
- Ein erneuter Import desselben Pakets aktualisiert seine Schichten. Eine Schicht, die die neue Version nicht mehr hat, wird gelöscht, und jeder Entitätstyp, der sie hatte – auch die deiner Organisation –, verliert seine Schicht. Schichten deiner Organisation bleiben.
- Das Importergebnis zählt die angelegten, aktualisierten und gelöschten Schichten (`layersCreated`, `layersUpdated`, `layersDeleted`) und die Entitätstypen, die eine Schicht verloren haben (`layerAssignmentsCleared`). Labels für eine Schicht stehen in `i18nBundles` unter dem Schlüssel `architecture-layer.{id}.name`.

## Viewpoints in Paketen {#viewpoints-in-packages}

Ein Paket kann in `viewpoints` Viewpoints mitbringen: jeden mit einer stabilen `id`, `name`, optionaler `description`, `notation`, den erlaubten `metatypeIds` und `connectionTypeIds` (Typen des Pakets oder der Instanz), optionalen `mappings` und optionalen Standardgrössen `sizes` in pt (`metatypeId` mit `width` und `height` oder `length`; siehe [Viewpoints](./metamodel-editing#viewpoints)). Der Import installiert sie als systemdefinierte, schreibgeschützte Viewpoints mit dem Paket als Herkunft (siehe [Viewpoints](./metamodel-editing#viewpoints)).

- Ein Viewpoint hat dieselben Grenzen wie ein eigener: Name bis 255 Zeichen, Beschreibung bis 1000, höchstens 500 Entitätstypen, 500 Verbindungstypen, 1000 Zuordnungen und 1000 Grössen, Grössen von 1 bis 2000 pt und Farben als `#rrggbb`. Ein Verstoss oder ein unbekannter Typ wird mit `422` abgelehnt und nennt den Viewpoint, eine fehlerhafte Grösse auch `sizes`. `defaultWidth` und `defaultHeight` in einer Zuordnung werden ignoriert.
- Ein erneuter Import desselben Pakets aktualisiert seine Viewpoints, auch wenn einer umbenannt wurde, und löscht die, die das Paket nicht mehr enthält, samt ihren [Diagrammen](../usage/diagrams), auch den gelöschten; die Entitäten bleiben. Viewpoints deiner Organisation oder eines anderen Pakets werden nie überschrieben: Dieselbe ID oder derselbe Name ergibt `409`.
- Das Importergebnis zählt die angelegten, aktualisierten und gelöschten Viewpoints und, als `diagramsDeleted`, die Diagramme, die mit ihnen gelöscht wurden. Ein abgelehnter Import löscht nichts.

---
title: Metamodell-Pakete
covers:
  screens: []
  stories: [US-033, US-057, US-063, US-064, US-075, US-076, US-083]
---

# Metamodell-Pakete

Eine neue Instanz hat keine Entitätstypen. Bevor jemand Entitäten erfassen kann, importierst du ein Metamodell: das mitgelieferte Starterpaket oder ein eigenes Paket.

Der Import von Paketen verlangt die Berechtigung `metamodel:write`, die nur die Rolle `metamodel-admin` hat – nicht das lokale Systemadmin-Konto. Ordne eine IdP-Gruppe `metamodel-admin` zu (siehe [Rollen und Berechtigungen](./roles-and-permissions)); als privilegierte Rolle braucht sie einen zweiten Faktor beim Identity-Provider.

## Starterkonfiguration importieren

OEA liefert das Starterpaket `oea-starter-togaf-classic` mit den Entitätstypen Application Component, Application Service, Data Object, Technology Component, Technology Service, System Software und Network Node, jeweils mit der Eigenschaft *Status*. Version 1.5.0 fügt die Typen der Datenherkunft hinzu (siehe [Lineage-Typen](#lineage-types)). Version 1.4.0 fügt die vier [Schichten](./metamodel-editing#layers) *Geschäft*, *Daten*, *Anwendung* und *Technologie* (englisch *Business*, *Data*, *Application* und *Technology*) hinzu und ordnet sie den Entitätstypen zu: *Application Component* und *Application Service* der Schicht Anwendung, *Data Object* der Schicht Daten und *Technology Component*, *Technology Service*, *System Software* und *Network Node* der Schicht Technologie (*Komposition* hat keine). Der erneute Import in eine Instanz mit einer eigenen Schicht desselben Namens, zum Beispiel *Business*, wird als Konflikt abgelehnt; benenne diese Schicht zuerst um oder lösche sie. Version 1.3.0 fügt den Verbindungstyp *Komposition* hinzu, gekennzeichnet als [Komposition](./metamodel-editing#composition) der Hierarchie von Komponenten. Version 1.2.0 fügt die zwei systemdefinierten Viewpoints *Application Landscape* und *Technology Landscape* in ArchiMate hinzu (siehe [Viewpoints in Paketen](#viewpoints-in-packages)). Version 1.1.0 brachte deutsche Labels für seine Entitätstypen, *Status* und dessen Werte sowie englische Labels für die Werte mit (siehe [Labels übersetzen](./metamodel-editing#translating-labels)). Eine Instanz, die eine frühere Version importiert hat, erhält die Labels und Viewpoints durch einen erneuten Import des Pakets.

Neben dem TOGAF-Paket liefert OEA das Starterpaket `oea-starter-bpmn` (siehe [Das BPMN-Starterpaket](#bpmn-package)). Solange die Instanz keinen Entitätstyp hat, zeigt die Startseite einer Person mit `metamodel:write` den Bereich *Startkonfiguration importieren*. Wähle *Importieren* neben dem Paket; die Seite bestätigt, wie viele Entitätstypen jetzt verfügbar sind.

<Screenshot name="starter-import" alt="Startseite mit der Starterkonfiguration zum Import" />

Über die API:

```bash
curl "${auth[@]}" "$OEA/metamodel/starter-packages"                    # Liste mit Zusammenfassung
curl "${auth[@]}" -X POST "$OEA/metamodel/starter-packages/oea-starter-togaf-classic/import"
curl "${auth[@]}" "$OEA/metamodel/metatypes"                                       # importierte Entitätstypen
```

## Ein Starterpaket später importieren {#starter-packages-section}

Ein Starterpaket lässt sich auch in eine Instanz importieren, die schon Entitätstypen hat, zum Beispiel das BPMN-Paket neben dem TOGAF-Paket. Wähle in der Kopfzeile *Metamodell* und den Bereich *Starterpakete* (für Inhaber von `metamodel:write`). Er listet die mitgelieferten Pakete mit Name, Version, Beschreibung, der Zahl der Entitätstypen und Eigenschaften und der Angabe, ob das Paket importiert ist, mit der Version des letzten Imports. Wähle *Importieren* neben einem Paket, das noch nicht importiert ist, und bestätige. Ein Paket, das in einer älteren als der mitgelieferten Version importiert ist, zeigt stattdessen *Auf* und die ausgelieferte Version *aktualisieren*, zum Beispiel *Auf 1.5.0 aktualisieren*: Es importiert die mitgelieferte Version mit der Strategie `merge`, ergänzt den neuen Inhalt und behält die IDs der vorhandenen Typen und des Viewpoints, sodass Diagramme, Layouts und Entitäten unverändert bleiben und keine Konflikte erhalten. Die Seite zeigt danach, wie viele Elemente angelegt wurden; die Entitätstypen erscheinen sofort im Metamodell. Ist ein Name des Pakets schon vergeben, von deiner Organisation oder von einem anderen Paket, wird nichts importiert, und der Bereich listet die kollidierenden Namen; benenne dein eigenes Element um oder lösche es und versuche es erneut. Der Bereich ist auch bei [gesperrtem](./metamodel-editing#lock-mode) Metamodell verfügbar, wie jeder Paketimport.

## Lineage-Typen im TOGAF-Paket {#lineage-types}

Version 1.5.0 von `oea-starter-togaf-classic` ergänzt, was die [Datenherkunft](../usage/data-lineage) braucht; das Paket listet dann zehn Entitäts- und Verbindungstypen:

- den Verbindungstyp *Data Flow* (Datenfluss) zwischen *Application Component*, *Application Service*, *Technology Component* und *System Software* (einschließlich ihrer Untertypen), in beide Richtungen, mit den optionalen Eigenschaften *Protocol* (`REST`, `SOAP`, `JDBC`, `FTP`, `Kafka`, `SFTP`, `other`), *Frequency* (`realtime`, `hourly`, `daily`, `weekly`, `on-demand`), *Personal data categories* (bis zu 500 Zeichen) und *Transformation description* (bis zu 1000 Zeichen);
- den Verbindungstyp *Carries Data* (transportiert Daten) von einem *Data Flow* zu einem *Data Object*;
- drei optionale Eigenschaften am *Data Object*: *Data classification* (`public`, `internal`, `confidential`, `restricted`), *Personal data categories* und *Data owner* (bis zu 255 Zeichen). Nichts ist verpflichtend, sodass kein vorhandenes Datenobjekt unvollständig wird;
- *Data Flow* in den Viewpoints *Application Landscape* und *Technology Landscape* (ArchiMate *Flow*); *Carries Data* steht in keinem Viewpoint;
- deutsche Labels und den Abschnitt `lineageModel`, der das [Lineage-Modell](./metamodel-editing#lineage-model) mit *Data Object*, *Data Flow* und *Carries Data* füllt.

**Namen:** Hat deine Organisation einen eigenen Entitäts- oder Verbindungstyp *Data Flow* oder *Carries Data* oder eine eigene Eigenschaft *Protocol* oder *Frequency*, wird die Aktualisierung auf 1.5.0 mit `409` abgelehnt und nichts importiert, weil Inhalte anderer nie überschrieben werden. Benenne dein eigenes Element zuerst um und aktualisiere dann. Die Aktualisierung behält alle Entitäten, Werte und Diagramme.

Das BPMN-Paket 1.2.0 bringt keine neuen Typen; sein Abschnitt `lineageModel` nennt die *Data Input Association* und die *Data Output Association* als Dateneingabe und Datenausgabe.

Ein eigenes Paket kann die Teile genauso mit `lineageModel` füllen: je Teil die ID eines Entitätstyps des Pakets (*dataObject*) oder eines Verbindungstyps (*dataFlow*, *carriesData*, *dataInput*, *dataOutput*); ein anderer Typ oder einer der falschen Art antwortet mit `422` und nennt `/lineageModel/<Teil>`. Der Import füllt nur Teile, die nicht gesetzt sind, listet sie in `lineageModelFilled` und protokolliert sie als `metamodel.lineage-model-updated` mit `cause: package-import`.

## Das BPMN-Starterpaket {#bpmn-package}

Das Paket `oea-starter-bpmn` (*BPMN 2.0 process modeling*) bringt die Typen von Geschäftsprozessmodellen:

- 28 Entitätstypen: das *BPMN Data Object* (mit der optionalen Eigenschaft *Collection*) und der *BPMN Data Store* (siehe [Datenobjekte und Datenspeicher](../usage/diagrams#data-objects)), *Process*, *Pool*, *Lane*, die Aktivitäten *Task*, *User Task*, *Service Task*, *Send Task*, *Receive Task*, *Business Rule Task*, *Script Task*, *Sub-Process* und *Call Activity*, die Ereignisse *Start Event*, *Message Start Event*, *Timer Start Event*, *End Event*, *Message End Event*, *Terminate End Event*, *Intermediate Catch Event* und *Intermediate Throw Event*, die Gateways *Exclusive Gateway*, *Parallel Gateway*, *Inclusive Gateway* und *Event-Based Gateway* sowie die Artefakte *Text Annotation* und *Group*. Die Aufgabenvarianten haben den Basistyp *Task*, die Startereignis-Varianten *Start Event* und die Endereignis-Varianten *End Event*, sodass eine Regel für *Task* auch *User Task* und die Untertypen deiner Organisation umfasst.
- die Verbindungstypen *Sequence Flow*, *Message Flow*, *Association* (von einer Textanmerkung aus), *Calls* (von einer Aufrufaktivität zu einem Prozess) und *References Process* (von einem Pool zu einem Prozess), jeweils auf BPMN-Elemente als Quelle und Ziel beschränkt; die *Data Input Association* (von jedem Entitätstyp zu einer Aufgabe) und die *Data Output Association* (von einer Aufgabe zu jedem Entitätstyp), deren offene Enden das TOGAF-*Data Object* ohne Abhängigkeit zwischen den Paketen teilnehmen lassen, mit den Eigenschaften *Transformation type* (`create`, `enrich`, `overwrite`, `delete`, `read-only`), *Affected attributes* (bis zu 5049 Zeichen, eines pro Zeile) und *Condition* (bis zu 2000 Zeichen) an der Ausgabeassoziation; und *Realized By* (von jedem Entitätstyp zu jedem Entitätstyp, zum Beispiel von einem Datenspeicher zu einer *System Software*). Die Eigenschaften des Pakets sind *Association direction* (`none`, `one`, `both`), *Group category* (bis zu 255 Zeichen), *Collection*, *Transformation type*, *Affected attributes* und *Condition*;
- deutsche Bezeichnungen für alle diese Elemente und der systemdefinierte Viewpoint *Process Model (BPMN 2.0)* mit der Notation BPMN 2.0, allen diesen Typen, BPMN-Symbolen und Standardgrößen. Der Viewpoint behält in jeder Sprache seinen englischen Namen.

**Was das Paket auslässt:** Organisationseinheiten, Rollen und die [Zuordnung von Bahnen](../usage/diagrams#assignments) kommen mit dem [Organisations-Paket](#organization-package). Version 1.2.0 ergänzt den Abschnitt `lineageModel`; Version 1.1.0 hat die Datentypen ergänzt; Version 1.0.0 hatte keine. Der Viewpoint des Pakets ist schreibgeschützt: Um das TOGAF-*Data Object* als Datenobjekt zu zeichnen, lege einen Viewpoint der Notation BPMN 2.0 an, der es erlaubt (siehe [Datenobjekte und Datenspeicher](../usage/diagrams#data-objects)). **Einschluss ist die Hierarchie der Entitäten:** Eine Bahn ist Kind ihres Pools, eine Aufgabe Kind ihrer Bahn; das Paket definiert keine Kompositionsverbindung, sodass mit diesem Paket allein für die Hierarchie keine Verbindung angelegt wird. Ist das TOGAF-Paket importiert, wird seine Verbindung *Composition* wie bei anderen Entitäten angelegt. Die Typen sind importierte Typen wie jede andere: schreibgeschützt, aber du kannst Eigenschaften und Untertypen hinzufügen. Importiere das Paket über die Startseite oder über den Bereich *Starterpakete*; dieses Paket und das TOGAF-Paket lassen sich in beliebiger Reihenfolge importieren.

Über die API ist die Liste der Pakete nach `packageId` sortiert; suche ein Paket daher über seine Kennung, nicht über seine Position.

## Das Organisations-Starterpaket {#organization-package}

Das Paket `oea-starter-organization` (*Organisationsmodell*) benennt, wer ausführt und wer verantwortlich ist, in einem BPMN-Diagramm und für jedes Architekturobjekt:

- die Entitätstypen *Organizational Unit*, *Role* und *Person* (die Eigenschaft *Account ID* der Person hat bis zu 255 Zeichen; sie ist die Eigenschaft, [die ein Konto mit der Person verknüpft](./property-visibility#account-mapping)) und die Verbindungstypen *Belongs To* (zu einer Organisationseinheit), *Performs Role* (zu einer Rolle) und *Responsible* (zu einer Person), jeweils von **jedem** Entitätstyp aus – einer Bahn, einer *Application Component* oder einem anderen –, sodass das Paket weder das BPMN- noch das TOGAF-Paket braucht. *Responsible* nennt nur Personen, keine Rollen oder Organisationseinheiten. Die Hierarchie der Organisationseinheiten ist die Hierarchie der Entitäten;
- deutsche Labels, keinen Viewpoint;
- den Abschnitt `organizationModel`, der das [Organisationsmodell](./metamodel-editing#organization-model) der Instanz füllt: Der Import setzt die sechs Teile, die noch nicht gesetzt sind, und überschreibt nie einen gesetzten Teil. Das Importergebnis listet sie in `organizationModelFilled`, und ein Audit-Ereignis `metamodel.organization-model-updated` mit `cause: package-import` hält sie fest.

Das Paket lässt sich allein oder neben den anderen Paketen importieren. Ein eigenes Paket kann die Teile genauso mit `organizationModel` füllen: je Teil die ID eines Entitätstyps des Pakets (*orgUnit*, *role*, *person*) oder eines Verbindungstyps (*belongsTo*, *performsRole*, *responsible*); ein anderer Typ oder einer der falschen Art antwortet mit `422` und nennt `/organizationModel/<Teil>`.

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

Ein Paket kann in `viewpoints` Viewpoints mitbringen: jeden mit einer stabilen `id`, `name`, optionaler `description`, `notation`, den erlaubten `metatypeIds` und `connectionTypeIds` (Typen des Pakets oder der Instanz), optionalen `mappings` und optionalen Standardgrössen `sizes` in pt (`metatypeId` mit `width` und `height` oder `length`; siehe [Viewpoints](./metamodel-editing#viewpoints)) und dem optionalen `askNewParent` (`true` oder `false`; ohne Angabe gilt die globale Einstellung). Der Import installiert sie als systemdefinierte, schreibgeschützte Viewpoints mit dem Paket als Herkunft (siehe [Viewpoints](./metamodel-editing#viewpoints)).

- Ein Viewpoint hat dieselben Grenzen wie ein eigener: Name bis 255 Zeichen, Beschreibung bis 1000, höchstens 500 Entitätstypen, 500 Verbindungstypen, 1000 Zuordnungen und 1000 Grössen, Grössen von 1 bis 2000 pt und Farben als `#rrggbb`. Ein Verstoss oder ein unbekannter Typ wird mit `422` abgelehnt und nennt den Viewpoint, eine fehlerhafte Grösse auch `sizes`. `defaultWidth` und `defaultHeight` in einer Zuordnung werden ignoriert.
- Ein erneuter Import desselben Pakets aktualisiert seine Viewpoints, auch wenn einer umbenannt wurde, und löscht die, die das Paket nicht mehr enthält, samt ihren [Diagrammen](../usage/diagrams), auch den gelöschten; die Entitäten bleiben. Viewpoints deiner Organisation oder eines anderen Pakets werden nie überschrieben: Dieselbe ID oder derselbe Name ergibt `409`.
- Das Importergebnis zählt die angelegten, aktualisierten und gelöschten Viewpoints und, als `diagramsDeleted`, die Diagramme, die mit ihnen gelöscht wurden. Ein abgelehnter Import löscht nichts.

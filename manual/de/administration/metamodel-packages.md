---
title: Metamodell-Pakete
covers:
  screens: []
  stories: [US-033]
---

# Metamodell-Pakete

Eine neue Instanz hat keine Entitätstypen. Bevor jemand Entitäten erfassen kann, importierst du ein Metamodell: das mitgelieferte Starterpaket oder ein eigenes Paket.

Der Import von Paketen verlangt die Berechtigung `metamodel:write`, die nur die Rolle `metamodel-admin` hat – nicht das lokale Systemadmin-Konto. Ordne eine IdP-Gruppe `metamodel-admin` zu (siehe [Rollen und Berechtigungen](./roles-and-permissions)); als privilegierte Rolle braucht sie einen zweiten Faktor beim Identity-Provider.

## Starterkonfiguration importieren

OEA liefert das Starterpaket `oea-starter-togaf-classic` mit den Entitätstypen Application Component, Application Service, Data Object, Technology Component, Technology Service, System Software und Network Node, jeweils mit der Eigenschaft *Status*.

Solange die Instanz keinen Entitätstyp hat, zeigt die Startseite einer Person mit `metamodel:write` den Bereich *Import starter configuration*. Wähle *Import* neben dem Paket; die Seite bestätigt, wie viele Entitätstypen jetzt verfügbar sind.

<Screenshot name="starter-import" alt="Startseite mit der Starterkonfiguration zum Import" />

Über die API:

```bash
curl "${auth[@]}" "$OEA/admin/metamodel/starter-packages"                          # Liste mit Zusammenfassung
curl "${auth[@]}" -X POST "$OEA/admin/metamodel/starter-packages/oea-starter-togaf-classic/import"
curl "${auth[@]}" "$OEA/metamodel/metatypes"                                       # importierte Entitätstypen
```

## Eigenes Paket importieren

Eigene Pakete verwenden dasselbe JSON-Format (`MetamodelPackage`, mit `packageId`, `packageVersion`, `publisher` und stabilen UUIDs pro Element). Importiere sie mit `POST /admin/metamodel/import`:

- Ein Paket wird vollständig oder gar nicht importiert. Importierte Inhalte bekommen den Scope `imported` und das Paket als Herkunft.
- Ein erneuter Import desselben Pakets aktualisiert dessen eigene Inhalte. Inhalte eines anderen Pakets oder deiner Organisation werden nie überschrieben; die Antwort ist `409` mit der Liste der Konflikte.
- Ein Paket kann für einen Entitätstyp einen Basistyp nennen (`baseMetatypeId`): einen Entitätstyp desselben Pakets oder einen eingebauten. Deine eigenen Eigenschaften an seinen Entitätstypen bleiben oder werden entfernt, je nach Einstellung für den erneuten Import (siehe [Metamodell bearbeiten](./metamodel-editing)).
- Pakete, die größer als `oea.metamodel.import.max-size` sind (Standard 10 MB), werden mit `413` abgelehnt; fehlerhaftes JSON oder Inhalte, die nicht zum Format passen, mit `422` und der Stelle des Problems.
- Die Strategie `replace`, Viewpoints, Shape-Zuordnungen und Übersetzungspakete werden noch nicht unterstützt (`422`).

Jeder Import schreibt die Audit-Events `metamodel.package-imported` und `metamodel.metatype-imported` mit einer gemeinsamen Batch-ID.

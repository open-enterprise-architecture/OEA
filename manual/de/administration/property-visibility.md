---
title: Sichtbarkeit von Eigenschaften
covers:
  screens: [SCR-032, SCR-040]
  stories: [US-098, US-124, US-125, US-126, US-127, US-128]
---

# Sichtbarkeit von Eigenschaften

Manche Eigenschaften sollen nicht alle sehen, die eine Entität lesen dürfen, etwa Investitionskosten oder eine Sicherheitseinstufung. Die Sicherheitsadministration legt für jede Eigenschaft fest, wer ihre Werte sehen und schreiben darf. Du brauchst die Rolle `security-admin` (Berechtigung `visibility:configure`); Metamodell-Administratoren können die Sichtbarkeit nicht ändern (siehe [Rollen und Berechtigungen](./roles-and-permissions)).

## Was die Sichtbarkeit bewirkt

- **Rollenbasierte** Eigenschaften werden in Katalogen, im Portal und beim Schreiben von Entitäten durchgesetzt:
  - Ohne Leserolle zeigt eine Spalte zur Eigenschaft ein Schloss und leere Zellen und lässt sich weder sortieren noch filtern.
  - Ohne Schreibrolle wird ein Batch oder eine Verbindung, die ihren Wert setzt oder entfernt, mit `403` abgelehnt.
  - Alte und neue Werte einer solchen Eigenschaft werden verschlüsselt im Audit-Log gespeichert.
- **Verbindungsbasierte** Eigenschaften werden auf denselben Kanälen pro Entität durchgesetzt:
  - Eine Person sieht und schreibt den Wert nur bei Entitäten, die über den gewählten Verbindungstyp mit dem Element verbunden sind, das mit ihrem Konto verknüpft ist, in beide Richtungen und über eine Verbindung (siehe [Der Entitätstyp von Personen](#person-metatype)).
  - In einem Katalog zeigt die Spalte den Wert in den Zeilen solcher Entitäten und in allen anderen eine leere Zelle. Sortierung und Filter behandeln die leeren Zellen wie Einträge ohne Wert, so hängt das Ergebnis nie von einem verborgenen Wert ab.
  - Ohne verknüpftes Element oder für das lokale Systemadministrator-Konto verhält sich die Spalte wie eine rollenbasierte Spalte ohne Leserolle.
  - Ein Batch oder eine Verbindung, die den Wert einer Entität außerhalb des Bereichs oder einer im selben Batch angelegten Entität setzt oder entfernt, wird mit `403` abgelehnt; lege zuerst die Verbindung an, die den Bereich festlegt.
  - Eine neue oder entfernte Verbindung dieses Typs gilt ab der nächsten Anfrage.
- **Override-Rollen** lesen jede Eigenschaft, unabhängig von ihrer Sichtbarkeit (siehe [Override-Rollen](#override-roles)). Sonst niemand: Weder `system-admin` noch `security-admin` lesen eingeschränkte Werte ohne Leserolle.

## Die Seite

Öffne *Sichtbarkeit von Eigenschaften* in der Kopfzeile. Die Seite listet jede Eigenschaft mit den Entitätstypen, die sie verwenden, ihrer Sichtbarkeit und ihrer Einschränkung; eingeschränkte Eigenschaften tragen das Badge *Eingeschränkt*. Filtere die Liste nach Name.

<Screenshot name="property-visibility" alt="Dialog für die Sichtbarkeit einer Eigenschaft mit Lese- und Schreibrollen" />

## Der Entitätstyp von Personen {#person-metatype}

Verbindungsbasierte Sichtbarkeit geht von dem Element aus, das eine Person im Modell darstellt, zum Beispiel einem *Business Actor*. Wähle diesen Entitätstyp in *Entitätstyp von Personen* oberhalb der Liste; *Keiner* schaltet verbindungsbasierte Sichtbarkeit für alle ab. Wählbar sind nur Entitätstypen, die keine Verbindungstypen sind, und ein hier verwendeter Entitätstyp lässt sich nicht löschen. Die Änderung wird als `visibility.person-metatype-changed` ins Audit-Log geschrieben.

Solange es verbindungsbasierte Eigenschaften gibt, aber kein Entitätstyp gesetzt ist, zeigt die Seite eine Warnung: Die Werte dieser Eigenschaften sieht niemand.

Das Konto einer Person wird über die API mit ihrem Element verknüpft (siehe [Identity-Provider und Personen](./identity-providers#link-an-account-to-an-element)). Eine Verknüpfung zählt nur, solange das Element existiert und vom gewählten Entitätstyp oder einem seiner Untertypen ist; ein Wechsel des Entitätstyps behält die Verknüpfungen, aber Verknüpfungen zu Elementen eines anderen Typs zählen nicht mehr.

## Override-Rollen {#override-roles}

Ausnahmen wie eine Fehleranalyse oder eine Datenmigration verlangen manchmal, dass jemand alle eingeschränkten Werte sieht, ohne dass die Leserollen jeder Eigenschaft geändert werden. Dafür setzt die Sicherheitsadministration eine Rolle auf die Override-Liste: Öffne *Override-Rollen* in der Kopfzeile oder folge dem Link auf der Seite *Sichtbarkeit von Eigenschaften*.

<Screenshot name="override-roles" alt="Override-Rollen mit dem Audit-Hinweis und der Rollenauswahl" />

- Hake die Rollen an und wähle *Speichern*. Nach der Installation ist die Liste leer, und sie fasst höchstens 20 Rollen. Meist nimmst du dafür eine [eigene Rolle](./roles-and-permissions#custom-roles) wie `migration-support` und vergibst sie nur für die Dauer der Arbeit.
- Wer eine Override-Rolle hat, sieht in Katalogen und im Portal jeden Wert und kann danach sortieren und filtern, aber nur bei Entitäten, die ohnehin lesbar sind: Die Override-Rolle gibt keinen Zugriff auf Entitäten und erlaubt nicht, eingeschränkte Werte zu schreiben.
- Jede Abfrage, die einen Wert nur wegen einer Override-Rolle zeigt, schreibt einen Eintrag `property-override-access` ins Audit-Log, mit den Eigenschaften und den Entitäten, deren Werte gezeigt wurden. Ein Wert, den die Person über eine Leserolle oder eine Verbindung lesen dürfte, zählt nicht.
- Jede Änderung der Liste wird als `visibility.override-changed` mit alter und neuer Liste im Audit-Log festgehalten; fügst du eine deiner eigenen Rollen hinzu, wird die Änderung als selbstbegünstigend markiert.
- Eine Änderung gilt ab der nächsten Anfrage.

## Sichtbarkeit ändern

Klicke auf den Stift einer Eigenschaft und wähle die Sichtbarkeit:

- **Öffentlich:** Alle, die die Entität lesen dürfen, sehen den Wert. Das ist der Standard jeder Eigenschaft.
- **Rollenbasiert:** Nur Inhaber der gewählten Leserollen sehen den Wert, und nur Inhaber der gewählten Schreibrollen dürfen ihn ändern. Wähle mindestens eine Leserolle; jede Schreibrolle muss auch eine Leserolle sein. Ohne Schreibrolle darf niemand den Wert ändern.
- **Verbindungsbasiert:** Nur Personen, die über den gewählten Verbindungstyp mit der Entität verbunden sind, sehen und ändern den Wert, zum Beispiel eine Domänenarchitektin, die über *Domain Assignment* verbunden ist. Wählbar sind nur Verbindungstypen.

Der Dialog zeigt nur die Felder der gewählten Sichtbarkeit und weist auf eine fehlende Auswahl hin, bevor er speichert. Jede Änderung wird als `visibility.changed` ins Audit-Log geschrieben; eine Änderung, die eine deiner eigenen Rollen begünstigt, wird als selbstbegünstigend markiert.

Solange ein Verbindungstyp für eine verbindungsbasierte Eigenschaft verwendet wird, braucht das Anlegen von Verbindungen dieses Typs die Berechtigung `visibility-scope:assign` (Rolle `security-admin`), damit sich niemand selbst Zugriff verschafft. Ein solcher Verbindungstyp lässt sich nicht löschen.

In der Metamodell-Konfiguration tragen eingeschränkte Eigenschaften das Badge *Eingeschränkt*, und der Dialog einer Eigenschaft zeigt ihre Sichtbarkeit nur lesend (siehe [Metamodell bearbeiten](./metamodel-editing)).

## Änderungshistorie {#change-history}

Die [Änderungshistorie](../usage/change-history) einer Entität nennt die Felder, die jede Version geändert hat, nie ihre Werte. Für ein Feld, das die lesende Person nicht lesen darf, entscheidet der Abschnitt *Änderungshistorie* unter der Liste, wie es erscheint:

1. Wähle den Entitätstyp.
2. Wähle für jede Rolle *Eingeschränkt* (das Feld erscheint mit dem Badge *Eingeschränkt*) oder *Ausgeblendet* (das Feld fehlt).
3. Wähle *Speichern*. Nur die geänderten Rollen werden gespeichert; ein abgelehnter Eintrag wird in seiner Zeile markiert, und dann wird nichts gespeichert.

Ohne Einstellung zeigt jede Rolle *Eingeschränkt*. Eine Person mit mehreren Rollen sieht die offenere Einstellung: Das Feld fehlt nur, wenn jede ihrer Rollen es für den Entitätstyp ausblendet. Eine Einstellung gilt für genau einen Entitätstyp, nicht für seine Untertypen, und ein Paketimport behält sie. Jeder geänderte Eintrag wird als `visibility.history-changed` ins Audit-Log geschrieben; eine eigene Rolle wieder auf *Eingeschränkt* zu setzen wird als selbstbegünstigend markiert.

## Über die API

- `GET /admin/property-visibility` listet die Sichtbarkeit jeder Eigenschaft mit `visibilityMode`, `readRoles`, `writeRoles`, `scopingConnectionType` und den Entitätstypen in `metatypes`.
- `PUT /admin/property-visibility` ändert die aufgeführten Eigenschaften in einem Schritt, zum Beispiel:

  ```bash
  jq -n --arg id "$PROPERTY_ID" '[{propertyDefId: $id, visibilityMode: "role-restricted",
    readRoles: ["enterprise-architect", "auditor"], writeRoles: ["enterprise-architect"]}]' |
    curl "${auth[@]}" -X PUT -d @- "$OEA/admin/property-visibility"
  ```

  Ein ungültiger Eintrag antwortet mit `422` und dem Feld in `invalidFields`, etwa `[0].readRoles`, und nichts wird geändert.

- `GET` und `PUT /admin/property-visibility/person-metatype` lesen und setzen den Entitätstyp von Personen, zum Beispiel `{"metatypeId": "<ID des Entitätstyps>"}`, oder `{"metatypeId": null}` für keinen. Ein Verbindungstyp oder ein unbekannter Entitätstyp antwortet mit `422` und `metatypeId` in `invalidFields`.

- `GET` und `PUT /admin/property-visibility/override-roles` lesen und ersetzen die Override-Rollen, zum Beispiel `{"roles": ["migration-support"]}`. Eine unbekannte Rolle antwortet mit `422` und `roles` in `invalidFields`; mehr als 20 Rollen antworten mit `400`.

- `GET` und `PUT /admin/history-visibility` lesen und ändern die Einstellungen der Änderungshistorie, zum Beispiel `{"entries": [{"roleId": "viewer", "metatypeId": "<ID des Entitätstyps>", "mode": "hide"}]}`. `show-restricted` entfernt einen Eintrag. Eine Änderung enthält 1 bis 500 Einträge; eine unbekannte Rolle, ein unbekannter Entitätstyp oder Modus antwortet mit `422` und dem Eintrag in `invalidFields`, etwa `entries[0].roleId`, und nichts wird geändert.

Alle brauchen `visibility:configure`.

---
title: Datenherkunft
covers:
  screens: []
  stories: [US-063, US-064, US-065, US-068]
---

# Datenherkunft

Die Datenherkunft (Data Lineage) zeigt, wie Daten zwischen Systemen fließen: woher ein Datenobjekt kommt und wohin es geht. Du modellierst sie mit gewöhnlichen Entitäten und Verbindungen und fragst sie über die API ab. Die Seite, die die Herkunft zeichnet, folgt in einem späteren Release; diese Seite behandelt das Modellieren und die Abfrage.

Du brauchst `entity:read` (zum Beispiel die Rolle `viewer`), um die Herkunft abzufragen und Kataloge zu filtern, und `entity:write` (Rolle `enterprise-architect`), um sie zu modellieren. Die Typen kommen mit dem TOGAF-Starterpaket 1.5.0 (siehe [Lineage-Typen](../administration/metamodel-packages#lineage-types)); welche Typen welche Rolle spielen, legt das [Lineage-Modell](../administration/metamodel-editing#lineage-model) fest.

## Datenflüsse und ihre Daten modellieren

1. Lege das *Data Object* an, zum Beispiel *Customer Master*, und gib ihm optionale Eigenschaften: *Data classification*, *Personal data categories* und *Data owner*.
2. Verbinde zwei Systeme durch einen *Data Flow*, zum Beispiel von *SAP ERP* nach *DWH*. Ein Datenfluss läuft zwischen Anwendungskomponenten, Anwendungsdiensten, Technologiekomponenten und Systemsoftware; seine optionalen Eigenschaften sind *Protocol*, *Frequency*, *Personal data categories* und *Transformation description*.
3. Verbinde den Datenfluss mit dem Datenobjekt durch *Carries Data*. Die Quelle dieser Verbindung ist der **Datenfluss**, nicht ein System: eine Verbindung, die an einer Verbindung beginnt. Ein Fluss kann mehrere Datenobjekte transportieren, und ein Datenobjekt kann von mehreren Flüssen transportiert werden.

Ein *Carries Data* von einer Anwendungskomponente wird mit `422 SOURCE_NOT_ALLOWED` abgelehnt, eines zu einem Datenfluss mit `422 TARGET_NOT_ALLOWED`. Verbindungen von Verbindungen gehen **eine Ebene tief**: Eine neue Verbindung, deren Quelle oder Ziel selbst eine Verbindung an einer Verbindung ist (zum Beispiel ein *Realized By* an einem *Carries Data*), wird mit `422 CONNECTION_DEPTH_EXCEEDED` abgelehnt, das Quelle oder Ziel nennt. Vorhandene Verbindungen bleiben.

Aufgaben (Tasks) kommen über die BPMN-Datenassoziationen in die Herkunft: Eine Aufgabe mit einer *Data Output Association* zu einem Datenobjekt ist ein **Schreiber**, flussaufwärts mit ihrem *Transformation type*; eine Aufgabe mit einer *Data Input Association* vom Datenobjekt ist ein **Leser**, flussabwärts.

## Die Herkunft abfragen

```bash
curl "${auth[@]}" -X POST "$OEA/views/lineage/query" -H 'Content-Type: application/json' \
  -d '{"startEntityId": 42, "direction": "downstream", "maxDepth": 50}'
```

| Feld | Bedeutung |
|---|---|
| `startEntityId` | Pflicht: ein Datenobjekt oder ein System (keine Verbindung) |
| `direction` | `downstream` (Verbraucher), `upstream` (Quellen), `both` (Standard, die Vereinigung beider Läufe) oder `impact` (`downstream` mit `impact: true` an jedem Knoten) |
| `maxDepth` | 1 bis 100, Standard 50 (`400` außerhalb) |
| `metatypeIds` | höchstens 50 Entitätstypen (`400` darüber); behält nur die Knoten dieser Typen und ihrer Untertypen, die Kanten bleiben |
| `dataObjectId` | nur für einen Start, der ein System ist: folgt nur den Flüssen, die dieses Datenobjekt transportieren |

Von einem **Datenobjekt** folgt die Abfrage den Flüssen, die es transportieren: `downstream` von ihren Ursprüngen, `upstream` von ihren Senken. Von einem **System** folgt sie den Flüssen von oder zu ihm, Schritt für Schritt. Die Antwort listet die `nodes` (Systeme und Aufgaben, mit `depth` und `roles`: origin, intermediate, sink, reader, writer; der Start ist nie ein Knoten), die `edges` (Datenflüsse, jeweils mit den transportierten Datenobjekten in `carriedBy`, höchstens 50 mit `carriedByTruncated`; und die Datenassoziationen der Aufgaben), die `paths` (höchstens 100) und die Kennzeichen `cycleDetected` und `truncated`. Es werden höchstens 5000 Knoten und 10000 Kanten geliefert. Gelöschte Entitäten fehlen.

Fehler: `404` für einen unbekannten oder gelöschten Start, `422` für eine Verbindung als Start, einen unbekannten Entitätstyp im Filter oder eine `dataObjectId`, die kein Datenobjekt ist, und `422 LINEAGE_MODEL_INCOMPLETE`, wenn Datenobjekt, Datenfluss oder *Carries Data* im Lineage-Modell nicht gesetzt ist. Der einzige gelieferte Eigenschaftswert ist der *Transformation type* einer Datenausgabe, und nur wenn du ihn lesen darfst; sonst hat die Kante `transformationRestricted: true`.

## Datenflüsse mit personenbezogenen Daten finden

Lege einen [Katalog](./catalogs) von *Data Flow*-Entitäten an, zeige die Spalten *Personal data categories* und *Protocol* und filtere nach *Personal data categories ist nicht leer* und zum Beispiel *Protocol ist JDBC*. Ein Filter ohne Treffer zeigt eine leere Tabelle, keinen Fehler. Ein CSV-Export eines Katalogs gehört nicht zu diesem Release.

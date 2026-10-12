# ADR-026: Externe System-Integration — Schreibzugriff via API (optional)

**Status**: accepted
**Datum**: 2026-06-29
**Entscheider**: [Lukas – Repository-Inhaber](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Kontext

OEA beschreibt Ist- und Soll-Zustände einer IT-Landschaft. Diese Daten können manuell
durch Architekten gepflegt werden — oder automatisch durch externe Systeme, die den
Ist-Zustand aus Quellsystemen heraus befüllen:

- **Code-Scanner**: liest Repository-Struktur und legt Application Components an
- **CI/CD-Pipelines**: meldet Deployment-Statusänderungen als Lifecycle-Update
- **Import-Skripte**: migrieren Legacy-Daten aus CMDB, ServiceNow, Alfabet, etc.
- **Regelbasierte Engines**: prüfen Konformität und legen Befunde als Entitäten an
- **KI-gestützte Analyse-Werkzeuge**: extrahieren Architektur aus Artefakten

Allen gemeinsam ist: Sie schreiben strukturiert und wiederholt auf dieselben Entitäten —
ohne vorherige Kenntnis interner Datenbankids und mit der Anforderung, mehrfach
idempotent ausführbar zu sein.

Diese Schreibfähigkeit ist **optional**. OEA funktioniert vollständig ohne sie.
Sie wird aktiviert, wenn eine Installation den `integration`-Feature-Flag setzt.

---

## Entscheidung

### 1. Externes Identifier-Feld (`external_id`)

Jede Entität erhält ein optionales Feld `external_id TEXT` — eine stabile, vom
aufrufenden System vergebene Kennung (z.B. Maven Artifact-ID, Kubernetes Service-Name,
CMDB-CI-ID).

```sql
ALTER TABLE entities ADD COLUMN external_id TEXT;
CREATE UNIQUE INDEX uq_entities_external_id ON entities (external_id)
  WHERE external_id IS NOT NULL;
```

Regeln:
- Vergabe durch das schreibende System, nicht durch OEA
- Eindeutig pro OEA-Instanz (Namespace-Konvention empfohlen: `{system}:{id}`)
- Nie automatisch generiert; bleibt NULL wenn manuell angelegt

### 2. Upsert-Endpoint

```
PUT /api/v1/entities/by-external-id/{externalId}
```

Semantik: Create-or-Update anhand des `external_id`. Liefert `201 Created` bei Neuanlage,
`200 OK` bei Update. Idempotent — wiederholter Aufruf mit identischen Daten ist sicher.

Kein separater "check if exists"-Roundtrip nötig.

### 3. Flat Write-Schema

Schreibende Systeme kennen interne Property-Typen nicht zuverlässig. Alle
Property-Werte werden daher als `string` übermittelt — das Backend konvertiert
gemäss `property_definitions.data_type` und gibt strukturierte Validierungsfehler zurück.

```json
{
  "name": "Order Service",
  "metatypeId": "uuid-des-metatyps",
  "externalId": "git:org/repo/order-service",
  "properties": [
    { "propertyDefId": "uuid", "value": "2.4.1" },
    { "propertyDefId": "uuid", "value": "active" }
  ]
}
```

Vorteil: Das schreibende System muss kein Typ-Mapping kennen.
Nachteil: Backend-Validierungsfehler müssen pro Property klar adressiert sein.

### 4. Batch-Write

```
POST /api/v1/entities/batch
```

Nimmt eine Liste von Entitäten und Verbindungen entgegen. Strategie wählbar:

| Strategie | Verhalten |
|---|---|
| `upsert` (default) | Create-or-Update per `external_id` oder `id` |
| `create-only` | Schlägt fehl wenn Entität bereits existiert |
| `skip-existing` | Überspringt vorhandene, legt nur neue an |

Atomarität: Konfigurierbar (`atomic: true/false`). Im atomaren Modus wird bei
erstem Fehler alles zurückgerollt. Im nicht-atomaren Modus: partielle Ergebnisse
mit pro-Item-Status in der Response.

Maximale Batch-Grösse: konfigurierbar, Default 500 Items.

### 5. Verbindungen anlegen

```
POST /api/v1/connections
```

Minimaler Body: `{ sourceExternalId, targetExternalId, connectionMetatypeId }`.
Alternativ: `sourceId`/`targetId` (interne IDs). Beide Felder sind upsert-fähig
analog zu Entitäten.

Validierung gegen `metatype_connection_constraints` — eine ungültige
Source-Target-MetaTyp-Kombination ergibt `422 Unprocessable Entity`.

### 6. Dry-Run

Jeder schreibende Endpoint akzeptiert den Query-Parameter `?dryRun=true`.
Validierung wird vollständig ausgeführt, keine Persistierung. Response enthält:
- Was würde angelegt / aktualisiert / übersprungen / fehlschlagen
- Validierungsfehler pro Item

### 7. System-Identität im Audit-Log

Das Audit-Log (ADR-024) wird um zwei Felder erweitert:

```sql
ALTER TABLE audit.events ADD COLUMN agent_id   TEXT;  -- System-Kennung (z.B. "oea-scanner/1.0")
ALTER TABLE audit.events ADD COLUMN agent_run   TEXT;  -- Korrelations-ID eines Laufs
```

Damit ist im Audit-Trail sichtbar, welches externe System welche Entitäten in welchem
Lauf angelegt hat — und nicht nur "Quelle: API".

Der `agent_id`-Wert kommt aus dem `X-Agent-Id`-Request-Header oder dem JWT-Claim
`agent_id`.

### 8. Metamodell-Discovery

Schreibende Systeme müssen vor dem ersten Write das Metamodell verstehen:

```
GET /api/v1/metamodel/metatypes?q={freitext}
GET /api/v1/metamodel/allowed-connections?sourceMetatypeId={id}&targetMetatypeId={id}
```

Der zweite Endpoint beantwortet: "Ist eine Verbindung zwischen MetaTyp A und B erlaubt,
und welcher Connection-MetaTyp ist dafür zu verwenden?"

### 9. Metamodell-Import/Export (Package-Format)

Damit externe Systeme das Metamodell einer OEA-Instanz kennen, ohne es einzeln
abzufragen, gibt es einen Package-Endpoint:

```
GET  /api/v1/admin/metamodel/export   → JSON-Paket: MetaTypen + PropertyDefs + Viewpoints + Shapes
POST /api/v1/admin/metamodel/import   ← dasselbe Format; Strategie: merge | replace
```

Dieses Format ist auch die Grundlage für austauschbare Metamodell-Bibliotheken
(z.B. "ArchiMate 3.2 Basis-Metamodell").

---

## Abgrenzung

| Was | Dieses Pattern | Nicht enthalten |
|---|---|---|
| Manuelle Pflege durch Architekten | Nein — UI-seitig | – |
| Import aus Legacy-Datei (CSV, XML) | Ja — über Batch-Endpoint | Parser ist Sache des Clients |
| Echtzeit-Synchronisation (Change-Streams) | Nein | separates Thema (v2) |
| Lese-Zugriff durch externe Systeme | Vorhanden (Resource API) | – |

---

## Sicherheit

- Externe Schreibzugriffe erfordern dedizierte Service-Accounts (kein User-JWT)
- Service-Accounts haben eingeschränkte Rollen: `integration-writer` (keine Admin-Operationen)
- Rate-Limiting auf Batch-Endpoints (konfigurierbar)
- `external_id` darf keine internen Ids anderer Systeme als Klartext enthalten (Namespace-Konvention)

---

## Verworfene Alternativen

### Nur manuelle Pflege
Nicht skalierbar für grosse IT-Landschaften mit häufigen Änderungen. Verworfen.

### Eigenes Import-Dateiformat (CSV, Excel)
Jedes System müsste einen spezifischen Exporter mitbringen. API ist universeller. Verworfen.

### Webhooks / Push vom Quellsystem direkt in DB
Kein Metamodell-Enforcement, kein Audit-Trail, keine Validierung. Verworfen.

---

## Konsequenzen

**Positiv:**
- Ist-Zustand kann automatisiert und idempotent befüllt werden
- Beliebige schreibende Systeme ohne OEA-spezifisches Wissen integrierbar
- Audit-Trail zeigt Herkunft jeder Entität

**Negativ / Kompromisse:**
- `external_id` muss durch das schreibende System konsistent vergeben werden
- Flat-Write-Schema erhöht Backend-Komplexität (Typ-Konversion + Fehlerreporting)
- Batch-Atomarität unter Multi-DB (ADR-023) erfordert sorgfältige Transaktionssteuerung

---

## Betroffene Konzept-Kapitel

- §6 (Entity-Modell, external_id), §9 (Metamodell-Discovery), §14 (Audit), §21 (Integration)

## Verwandte ADRs

- ADR-022: Strukturiertes Property-Modell
- ADR-023: Multi-DB-Strategie
- ADR-024: Audit-Separation (agent_id-Erweiterung)

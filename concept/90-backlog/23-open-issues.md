## 23. Offene Punkte – Status-Übersicht

Stand: 2026-06-26 — alle 47 ursprünglichen Punkte kategorisiert und abgearbeitet.

---

### 23.1 Geschlossen durch ADR oder Business Object

| # | Thema | Geschlossen durch | Entscheidung (Kurzform) |
|---|---|---|---|
| 2 | Identitäts-Schema / URN-Format | [ADR-001](../../adrs/ADR-001-urn-schema.md) | Integer-ID intern; `urn:oea:{instance-slug}:{id}` für externe Referenzen |
| 5 | BPMN-Speicherung | [business-objects/process.md](../../business-objects/process.md) | Internes Modell; `bpmn-contained-in`-Connection als Repo-Repräsentation; kein natives BPMN-XML in der DB |
| 11 | Product vs. Project | [ADR-003](../../adrs/ADR-003-product-vs-project.md) | `work-initiative` als built-in Basis; `product` und `project` als konfigurierbare Metamodell-Subtypen |
| 13 | Continuum-Repository-Verhältnis | [ADR-002](../../adrs/ADR-002-continuum-repository.md) | Ein Repository mit `scope`-Property (built-in/imported/organization) + Import-Mechanismus |
| 27 | API-Authentifizierungs-Scope | [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md) | OIDC; Microsoft Entra ID + Authentik als Pflicht-Integrationen; lokale Auth optional |
| 30 | Maximale Reifikations-Tiefe | [ADR-004](../../adrs/ADR-004-reifikation-details.md) | Tiefe 1 in v1.0; `allowsConnectionAsSource`-Flag; `maxConnectionDepth` konfigurierbar für v2.0 |
| 31 | Relation-Adressierung | [ADR-004](../../adrs/ADR-004-reifikation-details.md) | Alle Connections teilen den Integer-ID-Namespace mit Entities |
| 40 | Application-vs-Technology-Klassifikations-Default | [ADR-005](../../adrs/ADR-005-app-vs-tech-default.md) | Kein erzwungener Default; zwei optionale Starter-Pakete beim Bootstrapping |
| 46 | Visualisierungs-Strategie / Renderer-Notation | [ADR-007](../../adrs/ADR-007-canvas-bibliothek.md), [REQ-068](../../requirements/req/REQ-068-arc42-wysiwyg-editor.md), [REQ-078](../../requirements/req/REQ-078-drawio-einbetten.md) | Mermaid (client-seitig, kein Server), PlantUML (konfigurierter Server), draw.io (Embed via Code-Block), Vue Flow (interaktiver Canvas) |

---

### 23.2 Für v1.0 entschieden (Konzept-Entscheid)

Diese Punkte sind im Konzept entschieden, benötigen aber noch eine formale ADR bei der Implementierung.

---

**#1 / #24 — Persistenz-Technologie und Walking-Skeleton-Stack**

**Entscheidung**: PostgreSQL 15 + JSONB + Apache AGE (openCypher-Graph-Extension).

Begründung: Eine Datenbank-Instanz für beide Query-Paradigmen (SQL-Analytics + Graph-Traversierung, §22.1). JSONB ermöglicht flexible Properties ohne Schema-Migration bei Klasse-3-Properties (§15.1). Apache AGE ist Open Source (Apache 2.0), PostgreSQL-nativ und unterstützt openCypher. REQ-075 schreibt PostgreSQL 15 ohne proprietäre Extensions als Portabilitätsziel vor — AGE erfüllt diese Anforderung. → Formale ADR bei Backend-Sprint.

---

**#3 — Bitemporalität: Reicht Gültigkeits-Zeit (Plateau)?**

**Entscheidung**: Ja — nur Gültigkeitszeit in v1.0. Keine separate Erfassungszeit.

Begründung: Das Plateau-Modell (§11) bildet Gültigkeitszeiträume (wann ist ein Architekturzustand gültig?) vollständig ab. Erfassungszeit (wann wurde eine Änderung im System erfasst?) ist über das Audit-Log abdeckbar (§21.8), ohne das Datenmodell zu verdoppeln. Vollständige Bitemporalität ist für v2.0 als optionale Erweiterung möglich.

---

**#4 — Diff-Semantik: Property-by-Property oder Lifecycle-basiert?**

**Entscheidung**: Beides kombiniert — exakt wie in §11.5 definiert.

PlateauDiff enthält: `entitiesAdded`, `entitiesRetired`, `entitiesModified` (property-by-property), `lifecycleTransitions`. Berechnungsregeln sind in §11.5 festgelegt und gelten verbindlich. Keine weiteren Diff-Dimensionen in v1.0.

---

**#6 — Referenz-Integrität über Plateaus**

**Entscheidung**: Soft-Constraint (Warning) in v1.0.

Wenn Entität A in Plateau P als `retired` markiert ist, darf Entität B in Plateau P noch auf A verweisen — das Tool zeigt eine Warning ("Cross-Plateau-Konflikt"), blockiert aber nicht. Begründung: Migrations-Übergangszustände sind real; Hard-Constraint würde valide Zwischen-Modelle blockieren. v2.0: per Metamodell-Konfiguration auf Error konfigurierbar.

---

**#10 — Schema-Profile**

**Entscheidung**: Schema-Profile als formales Konzept entfallen.

ADR-005 Starter-Pakete übernehmen diese Funktion. Beim Bootstrapping wählt der Admin ein oder mehrere Starter-Pakete; das ergibt de facto ein initiales Schema-Profil. Zusätzliche Profile würden einen dritten Konfigurationsmechanismus neben Starter-Paketen und `scope`-Property einführen — unnötige Komplexität.

---

**#12 — SAFe-Capability-Kollision (TOGAF Capability vs. SAFe Capability)**

**Entscheidung**: Stereotype-Ansatz ist final.

- TOGAF Capability = `EntityType: capability` (built-in, §6)
- SAFe Capability = Subtyp via Metamodell: `EntityType: safe-capability` mit `extends: work-initiative` (oder eigener Subtyp von `capability`)

Im UI wird der vollständige EntityType-Name angezeigt (`capability` vs. `safe-capability`). Kein Namenskonflikt auf Daten-Ebene, da der EntityType-Identifier eindeutig ist.

---

**#23 / #25 — Query-Sprachen und Graph-Query-Standard**

**Entscheidung**: Zweischicht-Ansatz (§22.4) mit openCypher als Graph-Standard.

```
/api/v1/entities/search      ← typisierte REST-Abfragen (einfacher Einstieg)
/api/v1/graph/traverse       ← strukturierte Traversal-API (mittel)
/api/v1/graph/cypher         ← openCypher für mächtige Graph-Queries (fortgeschritten)
/api/v1/analytics/query      ← SQL für Analytics
/api/v1/analytics/views/{n}  ← benannte vordefinierte Views
```

openCypher (statt Gremlin oder ISO GQL): Apache AGE implementiert openCypher; Tooling-Reife; Neo4j-Kompatibilität bei späterer DB-Migration. Keine proprietäre DSL — Investitionsschutz für Query-Autoren.

---

**#26 — Event-Transport**

**Entscheidung**: Server-Sent Events (SSE) als Default in v1.0.

`GET /api/v1/events` als SSE-Stream; Events sind JSON mit `type`, `entityId`, `plateauId`, `timestamp`. WebSocket optional ab v2.0 für bidirektionale Use Cases (z.B. kollaborative Echtzeit-Bearbeitung). Kafka/Webhooks als Integration-Adapter (nicht Teil des Kern-Event-Bus).

---

**#28 — Modul-Isolierung: gleicher Prozess, Sidecar oder eigenständige Services?**

**Entscheidung**: Monolith-first in v1.0 — alle Module im gleichen Prozess.

Interne Modul-Kommunikation über einen internen Event-Bus (kein Netzwerk-Overhead). Modul-Grenzen werden durch klare Package-Struktur und Interface-Kontrakte sichergestellt, sodass spätere Extraktion zu Sidecar/Service ohne API-Breaking-Change möglich ist. Begründung: Operativer Overhead eines verteilten Systems ist für ein OSS-Tool in v1.0 unverhältnismäßig.

---

**#29 — CLI-Query-Tool: volle Query-Engine lokal oder Proxy?**

**Entscheidung**: v1.0 — CLI ist Proxy zum Server (REST-API-Client).

`oea query --server http://localhost:8080 "MATCH ..."` leitet Queries an den Server weiter. Vorteil: kein doppelter Query-Engine-Code in v1.0. v2.0: embedded DuckDB/Kuzu für offline und CI-Pipeline-Nutzung ohne laufenden Server (§22.10).

---

**#32 — Relation-Lifecycle pro Plateau: UI-Darstellung**

**Entscheidung**: Kantenfarbe auf dem Canvas nach Lifecycle-State; 3-Dot-Circle für Verbindungs-Panel.

Farb-Konvention (konsistent mit §11.5 Plateau-Diff-Visualisierung):
- `planned` / neu in Plateau: grün
- `active` / unverändert: grau/neutral
- `sunset` / `modified`: gelb/orange
- `retired` / entfernt aus Plateau: rot

Separates Relation-Liste-Panel: verfügbar im Detail-Panel der Canvas-Entität, nicht als eigene Top-Level-Ansicht (zu komplex für v1.0).

---

**#36 — Audit-Trail-Speicherung**

**Entscheidung**: Gleiche PostgreSQL-Instanz, separate append-only Tabelle `audit_events`.

Keine DELETE-Berechtigung auf dieser Tabelle, auch nicht für Admins (Constraint auf DB-Ebene). Felder: `id`, `timestamp`, `actor_id`, `action_type`, `entity_id`, `before_snapshot`, `after_snapshot`, `session_id`. v2.0: optionaler Export in SIEM (Splunk, Elastic, Wazuh) über Webhook-Adapter.

---

### 23.3 Zurückgestellt — Integration-Phase oder v2.0

Diese Punkte sind bewusst nicht für v1.0 entschieden. Sie werden beim jeweiligen Modul-Sprint oder in v2.0 adressiert.

| # | Thema | Zurückgestellt bis | Erste Orientierung |
|---|---|---|---|
| 7 | ITSM-Konnektor-Scope | ITSM-Modul-Sprint | ServiceNow als erstes Zielsystem (Enterprise-Verbreitung, SH-05); generischer REST-Adapter als Fallback |
| 8 | Sync-Konflikt-Resolution | ITSM-Modul-Sprint | Letzter-gewinnt als Default; manuelle Auflösung konfigurierbar; Master-System-Flag pro Property |
| 9 | PPM-Konnektor-Scope | PPM-Modul-Sprint | Jira Software + OpenProject als erste Zielsysteme; ServiceNow SPM als Enterprise-Option |
| 14 | TRM-Taxonomie-Pflege | v2.0 / Starter-Paket | TOGAF-Beispiel-TRM initial; eigenes TRM als organisation-eigenes Starter-Paket importierbar |
| 15 | Scope-Ausdrucksstärke für Schema-Evolution | v2.0 | v1.0: Properties + IN-Listen (Pattern 2, §15.2); Graph-Queries als Scope-Kriterium ab v2.0 |
| 16 | Data-Quality-Score-Gewichtung | v2.0 | Per-Organisation konfigurierbar; Schema definiert Gewichts-Property; Default-Gewichte im Starter-Paket |
| 17 | Person-Datenhaltung und DSGVO | DSGVO-Modul-Sprint | v1.0: Reference-only (Name + E-Mail); kein biometrisches/sensitives Datum direkt in OEA |
| 18 | Skill-Taxonomie | v2.0 | SFIA nur als Import-Option (nicht mitliefern wegen SFIA-Lizenz); Import-Format: CSV/JSON |
| 19 | BPM-Tool-Adapter-Scope | BPMN-Modul-Sprint | BPMN-XML-Import aus Camunda als erstes Ziel; Lese-only initial |
| 20 | PCF-Integration | v2.0 | APQC-PCF nicht mitliefern (Lizenzpflicht); nur Code-Referenzen; Import-Option für Lizenznehmer |
| 21 | Carbon-Assessment-Methodik | v2.0 | SCI (Software Carbon Intensity) als Methodik-Kandidat; manuell in v1.0; automatische Schätzung v2.0 |
| 22 | Contract-Dokumenten-Ablage | v2.0 | v1.0: nur externe Referenzen (URL/ID zu DMS); keine eigene Dokumentenablage in OEA |
| 33 | GRC-Adapter-Priorität | GRC-Modul-Sprint | Verinice zuerst (OSS, ISMS-nah) oder OSCAL-Export; hängt von Community-Feedback ab |
| 34 | Verarbeitungsverzeichnis-Format | DSGVO-Modul-Sprint | BSI-Orientierung (DE) als Primär-Schema; CNIL/ICO als Sekundär-Option |
| 35 | Control-Framework-Integration | v2.0 | v1.0: nur strukturelle Unterstützung (EntityType + Relation); ISO 27001 Annex A als Continuum-Artefakt ab v2.0 |
| 37 | Schutzbedarfs-Vererbung | v2.0 | v1.0: manuelle Pflege mit Validierungs-Hint; automatische Ableitung (DataEntity → Application) ab v2.0 |
| 38 | Property-Level-AuthZ-Konfiguration | Security-Sprint | Per-Deployment konfigurierbar (YAML im Admin-Bereich); Admin-UI in v1.1 |
| 39 | Segregation-of-Duties-Erzwingung | v2.0 | v1.0: Soft-Constraint (Warning); Hard-Constraint konfigurierbar ab v2.0; Override durch konfigurierten Admin-Rolle |
| 41 | Requirements-Versionierungs-Trigger | Requirements-Modul-Sprint | Trigger: Änderungen an `statement`, `acceptanceCriteria`, `priority`; Tipp-/Formatänderungen = kein Versionsbump |
| 42 | System-Requirements-Scope | Requirements-Modul-Sprint | Beides erlaubt (direkte Erfassung + externe Referenz); admin-konfigurierbar per Metamodell |
| 43 | ReqIF-Import-Export | Requirements-Modul-Sprint | Modul/Adapter, nicht Kern; Mapping-Konventionen bei Modul-Entwicklung definieren |
| 44 | Logical-Stub-Auto-Generierung | v2.0 | Opt-in; auto-generierte Stubs mit `(auto)`-Badge; kein automatischer Verfall; "Promote"-Aktion für Pflege |
| 45 | Inventar-Sicht-Scope | UX-Sprint | Default: `applicationcomponent` + `technologycomponent` sichtbar; alle weiteren via Viewpoint konfigurierbar (ADR-002) |
| 47 | Bestandstool-Migration | v1.1 | ArchiMate Open Exchange (Archi/Sparx EA) als erstes Import-Format; XMI als zweites |

---

← [Auswertbarkeit & Query-Architektur](../70-platform/22-auswertbarkeit.md) · [🏠 Übersicht](../README.md) · [Nächste Schritte](24-naechste-schritte.md) →

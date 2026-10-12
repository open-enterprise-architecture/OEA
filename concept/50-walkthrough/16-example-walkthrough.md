## 16. Beispiel-Walkthrough

**Szenario**: Das Unternehmen migriert von einem On-Premises-CRM auf ein Cloud-CRM bis 2027.

**Schritt 1 – Plateaus definieren:**
- `baseline-2026` (Ist)
- `transition-2027-q2` (parallel-Betrieb)
- `target-2028` (nur Cloud-CRM aktiv)

**Schritt 2 – Entitäten modellieren:**
- `ApplicationComponent "CRM-OnPrem"` – active in baseline, sunset in transition, retired in target
- `ApplicationComponent "CRM-Cloud"` – planned in baseline, under-construction in transition, active in target
- `Interface "Customer-Sync"` – verbindet CRM mit ERP, existiert in allen Plateaus, aber wechselt Source

**Schritt 3 – Gap und Work Package:**
- `Gap "crm-replacement"` zwischen baseline und target
- `WorkPackage "wp-crm-migration"` schließt diesen Gap

**Schritt 4 – Sichten generieren:**
- Bebauungsplan Domain "Sales" zeigt beide CRMs über Zeit mit Lifecycle-Farben
- Ziel-Diagramm für 2028 zeigt nur aktive Entitäten
- BPMN-Prozess "Lead Management" zeigt Tasks mit aktualisierten Service-Referenzen

**Schritt 5 – Pipeline:**
- Jeder Push auf `main` rendert: Bebauungsplan als PlantUML, Ziel-2028 als Mermaid, ArchiMate-XML-Export, ADR-Index
- Diagramme verlinken via klickbare Nodes zurück auf Entity-URIs im Repo

---

← [Schema-Evolution](../40-extensibility/15-schema-evolution.md) · [🏠 Übersicht](../README.md) · [ITSM-Integration](../60-integrations/17-itsm-integration.md) →

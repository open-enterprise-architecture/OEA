## 16. Example Walkthrough

**Scenario**: The company migrates from an on-premises CRM to a cloud CRM by 2027.

**Step 1 – Define plateaus:**
- `baseline-2026` (current state)
- `transition-2027-q2` (parallel operation)
- `target-2028` (only cloud CRM active)

**Step 2 – Model entities:**
- `ApplicationComponent "CRM-OnPrem"` – active in baseline, sunset in transition, retired in target
- `ApplicationComponent "CRM-Cloud"` – planned in baseline, under-construction in transition, active in target
- `Interface "Customer-Sync"` – connects CRM with ERP, exists in all plateaus, but changes source

**Step 3 – Gap and work package:**
- `Gap "crm-replacement"` between baseline and target
- `WorkPackage "wp-crm-migration"` closes this gap

**Step 4 – Generate views:**
- Landscape map (Bebauungsplan) for domain "Sales" shows both CRMs over time with lifecycle colors
- Target diagram for 2028 shows only active entities
- BPMN process "Lead Management" shows tasks with updated service references

**Step 5 – Pipeline:**
- Every push to `main` renders: landscape map as PlantUML, target 2028 as Mermaid, ArchiMate XML export, ADR index
- Diagrams link back to entity URIs in the repo via clickable nodes

---

← [Schema Evolution](../40-extensibility/15-schema-evolution.md) · [🏠 Overview](../README.md) · [ITSM Integration](../60-integrations/17-itsm-integration.md) →

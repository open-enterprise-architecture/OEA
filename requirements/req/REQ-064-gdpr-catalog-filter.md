---
id: REQ-064
title: GDPR catalog filter on data flows (personalDataCategories)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
    - catalog
  business_rules: []
  stakeholders:
    - SH-02
  concept:
    - concept/60-integrations/20-grc-gdpr-isms-integration.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-064: GDPR catalog filter on data flows (personalDataCategories)

## Statement

The system MUST make it possible, in a catalog (UC-06), to restrict all ArchitectureEntities of type `data-flow` with a filter on `personalDataCategories ≠ empty` and to export the result as CSV; this filter MUST also be combinable with the catalog filter operators from REQ-047.

## Rationale

GDPR Art. 30 requires a record of processing activities for all data flows that transport personal data. If `personalDataCategories` is maintained as a PropertyValue on `data-object` entities (REQ-060), and carries-data connections link the DataObjects with DataFlows (REQ-061), the system can provide a machine-readable basis for Art. 30 via a catalog filter — without a separate compliance tool.

## Context

Filtering runs through the catalog infrastructure (REQ-047). The special case here: filter on properties of *linked* entities via n-connection (carries-data → data-object → personalDataCategories). This is a join query across two levels.

Two variants:
1. **Direct property on data-flow**: `data-flow` itself has a PropertyDefinition `personalDataCategories` → simple filter directly on the data-flow PropertyValue
2. **Traversing filter**: filter on `personalDataCategories` of the `data-object` entities linked via `carries-data` → join via n-connection

In v1.0, variant 1 is supported (direct property on data-flow, easier to implement). Variant 2 (join via n-connection) is planned for v2.0, when the n-connection infrastructure is fully in place.

## Type-Specific Fields

### Filter Configuration (v1.0 – direct property)

| Parameter | Value |
|---|---|
| entityTypeId | `data-flow` |
| Filter | `personalDataCategories IS NOT NULL AND personalDataCategories != ''` |
| Combination | AND with further filters from REQ-047 (e.g., `dataClassification=confidential`) |
| Export | CSV with columns: id, name, protocol, frequency, personalDataCategories, dataOwner (if present), sourceEntityName, targetEntityName |

### Export Format (CSV)

```
id,name,entityTypeId,protocol,personalDataCategories,source,target
5,ERP→DWH Customer master data,data-flow,JDBC,"Name,Address,Email",SAP ERP,Data Warehouse
6,DWH→BI Customer master data,data-flow,REST,"Name,Email",Data Warehouse,BI Tool
```

## Acceptance Criteria

**AC1** (Filter personalDataCategories ≠ empty):
- Given: DataFlow id=5 with `personalDataCategories="Name,Address,Email"` and DataFlow id=7 without personalDataCategories
- When: Lukas opens a catalog with filter `entityTypeId=data-flow` + `personalDataCategories ≠ empty`
- Then: Only id=5 appears; id=7 does not

**AC2** (Combined filter):
- When: Lukas additionally adds filter `protocol=JDBC`
- Then: Only DataFlows with JDBC protocol AND non-empty personalDataCategories appear

**AC3** (CSV export):
- Given: Catalog shows 3 DataFlows after filtering
- When: Lukas clicks "Export as CSV"
- Then: CSV download with 3 rows + header; fields as described above; encoding UTF-8

**AC4** (Empty result):
- Given: No DataFlows have personalDataCategories filled
- When: Filter applied
- Then: Empty table; no error; export delivers only the header row

## Verification Method

- [x] Method: test (automated)
- [x] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes, as soon as implementation exists

## Dependencies

- **Prerequisites**: REQ-047 (catalog filter operators); REQ-060 (data-flow EntityType with personalDataCategories property)
- **Downstream effects**: Basis for a future UC GDPR Art. 30 report generation

## Implementation Notes

- Catalog filter operator `isNotEmpty` on PropertyValue; SQL: `WHERE property_name='personalDataCategories' AND value IS NOT NULL AND value != ''`
- CSV export: backend-side; no client-side generation

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

# US-068: GDPR Catalog — Filter and Export Data Flows by personalDataCategories

**ID**: US-068
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Data Architect, I want to filter out in the catalog all data flows that transport personal data and export them as CSV – so that I can provide the data protection officer with a machine-readable basis for the GDPR record of processing activities (Art. 30) without maintaining a separate compliance tool.

## Reference

**Use Case**: [UC-08](../use-cases/UC-08-model-data-lineage.md)
**Persona**: Lukas – Senior Data Architect (SH-02)
**Requirements**: [REQ-064](../req/REQ-064-gdpr-catalog-filter.md)

## Acceptance Criteria

**AC1** (filter on personalDataCategories):
- Given: DataFlow id=5 with `personalDataCategories="Name,Address,Email"` and DataFlow id=7 without personalDataCategories
- When: Lukas opens the catalog with filter `entityTypeId=data-flow` + `personalDataCategories is not empty`
- Then: Only DataFlow id=5 appears; id=7 does not

**AC2** (combined filter):
- When: Lukas additionally adds `protocol=JDBC`
- Then: Only JDBC data flows with personalDataCategories displayed

**AC3** (CSV export):
- Given: Catalog shows 3 DataFlows after filtering
- When: Lukas clicks "Export as CSV"
- Then: CSV download; contains header + 3 data rows; fields: id, name, protocol, personalDataCategories, source, target; UTF-8 encoding

**AC4** (empty result is not an error):
- Given: No DataFlows with personalDataCategories
- When: Filter applied
- Then: Empty table; export delivers only header; no error

## Technical Notes

- Filter operator `isNotEmpty` on PropertyValue (SQL: `value IS NOT NULL AND value != ''`)
- CSV export: generated backend-side, no client-side CSV

## Definition of Done

- [ ] AC1–AC4 met
- [ ] Tests: filter, combination, CSV export, empty state
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

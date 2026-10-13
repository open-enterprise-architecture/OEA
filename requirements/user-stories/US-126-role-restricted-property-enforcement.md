# US-126: Restricted Properties Are Displayed Empty Without Permission

**ID**: US-126
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Anna – Business Analyst, I want to simply see an empty (non-editable) field for entities where I have no read permission on certain fields — without an error message and without knowing the actual value, so that the application remains seamlessly usable.

## Reference

**Use Case**: [UC-21](../use-cases/UC-21-configure-property-visibility.md)
**Persona**: Anna – Business Analyst (SH-02)
**Requirements**: [REQ-126](../req/REQ-126-property-visibility-enforcement-role.md)

## Acceptance Criteria

**AC1** (API returns null):
- When: Anna retrieves an entity with a role-restricted property
- Then: API response contains `null` for this field; no real value transmitted

**AC2** (UI empty and disabled):
- When: Anna opens the entity detail form
- Then: the restricted field is empty and not editable; no tooltip, no placeholder with value

**AC3** (Write attempt rejected):
- When: Anna tries to write the field via a direct API call
- Then: HTTP 403; value unchanged

**AC4** (Catalog column empty):
- When: a catalog contains a column on a restricted property
- Then: cell is empty; sorting and filtering of the column disabled for Anna

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present (incl. API test with wrong token)
- [ ] Code review; linter green; entered in trace matrix

# US-108: Manage SBBs with Governance Status

**ID**: US-108
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to create and manage `SolutionBuildingBlock` entries with governance status, so that I can enforce technology standards and automatically flag prohibited products in the landscape.

## Reference

**Use Case**: [UC-17](../use-cases/UC-17-manage-continuum-building-blocks.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-108](../req/REQ-108-manage-sbb-governance.md)

## Acceptance Criteria

**AC1** (Create SBB):

- When: I create an SBB with `governanceStatus=approved` and `implements=[ABB-UUID]`
- Then: the system saves it with HTTP 201

**AC2** (Prohibited warning):

- When: I set an SBB to `governanceStatus=prohibited`
- Then: all entities with `instanceOfSBBId=this-SBB` show the note "Product in use is prohibited – migration required"

**AC3** (Warning without ABB reference):

- When: I create an SBB without `implements` ABBs
- Then: the system shows the governance warning "SBB without ABB reference is only a product catalog entry"; saving is not blocked

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix

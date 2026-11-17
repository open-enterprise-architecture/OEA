# US-153: Resolve the Open Issues of a Go-Live

**ID**: US-153
**Story Points**: 5
**Status**: done
**Sprint**: S14
**Implementation**: OpenSpec change `us-153-open-issues` – `listSolutionOpenIssues` with status and resolution, `resolveSolutionOpenIssues` (`solution:write` or `entity:write`), the blocking check `EntityBlocks` in the batch, restore, deltas and go-live (409 `entity-blocked`, item error `OPEN_ISSUES`), the automatic resolution computed on read, the marker in the catalog and the notice in the change history, the dialog SCR-064 (V36) (AC1–AC5)

## Story

As Michael – Solution Architect, I want the open issues a go-live left behind to block their entities until they are resolved, and to resolve them with valid values, so that metamodel violations taken live do not stay in the landscape unnoticed.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md) (project mode, after the go-live of [US-152](./US-152-solution-go-live.md))
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirements**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md), [REQ-162: Property write protection on all paths](../req/REQ-162-property-write-protection-all-paths.md)

## Acceptance Criteria

**AC1** (Open issues block their entity):

- Given: the go-live of a solution left the open issue "Contact missing" on "Salesforce"
- When: someone writes "Salesforce" in the catalog (batch write, restore) or captures a delta `modified` or `retiring` of it in a solution, or another solution with such a delta goes live
- Then: the write is rejected (409) naming the open issues and their solution, and nothing changes

**AC2** (Resolve with a valid value):

- Given: the open issue "Contact missing" on "Salesforce"
- When: Michael resolves it in the dialog on the page of the solution with the value `sales@example.com`
- Then: "Salesforce" has the value, the issue is resolved with person and time, the write is audited, and the entity is no longer blocked once all its issues are resolved

**AC3** (Only valid values):

- Given: the open issue "Users exceeds 1000" on "ERP-Core" of an optional property
- When: Michael resolves it with 5000, or removes the value
- Then: 5000 is rejected (422) and nothing changes; removing the value resolves the issue. The value of a mandatory property cannot be removed

**AC4** (Resolved automatically):

- Given: the open issue "Contact missing" on "Salesforce"
- When: the metamodel makes "Contact" optional
- Then: the issue counts as resolved and no longer blocks "Salesforce"

**AC5** (Visible in the catalog):

- Given: "Salesforce" has an open issue
- When: a person views "Salesforce" in the catalog or its change history
- Then: the row is marked, and the change history names the open issues with a link to their solution

## Technical Notes

- Permission to resolve: `solution:write`, or `entity:write` with the write roles and scope of a catalog write (owner decision 2026-11-17)
- An issue whose violation no longer holds counts as resolved on every read; no stored state is needed for that
- API: `listSolutionOpenIssues` (status), `resolveSolutionOpenIssues` (new)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration where applicable)

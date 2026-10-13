# US-034: Trace Metamodel Changes in the Audit Log

**ID**: US-034
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to be able to see who made which changes to the metamodel and when, so that in the case of unintended changes I can identify the originator and trace the change.

## Reference

**Use Case**: [UC-04: Configure the Metamodel Collaboratively](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-034: Audit Log for Metamodel Changes](../req/REQ-034-audit-log-metamodel.md)

## Acceptance Criteria

**AC1**:

- Given: Kurt has created `SecurityZone`, Lukas has deleted another type
- When: Kurt opens the audit log
- Then: he sees both entries with the respective person names, timestamps and element names

**AC2**:

- Given: 5 types were imported via import (REQ-033)
- Then: the audit log shows 5 entries as a coherent import batch (same batch ID)

**AC3**:

- Given: a write error in the audit log system
- When: Kurt makes a change
- Then: the change is saved; the audit log entry is caught up asynchronously

## Technical Notes

- Affected components: audit log infrastructure (same as REQ-005/REQ-025); new `eventType` namespace `metamodel.*`
- API endpoints: `GET /admin/audit-log?eventType=metamodel.*` (filtering by metamodel events)
- Database changes: no new tables; new `eventType` in existing audit table

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests: create/edit/delete triggers audit entry; import batch correct; audit log error does not block the main operation
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-032 (operations that must be logged), US-033 (import batch logging); existing audit log infrastructure
- Blocks: none

## Notes

2 SP because the audit log infrastructure already exists (REQ-005/REQ-025). Only the new `eventType` namespace and `importBatch` field need to be added. No new UI needed if the existing audit log view supports filtering by event type.

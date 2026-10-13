---
id: REQ-082
title: Data backup and point-in-time recovery
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-27
last_updated: 2026-06-27
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-016
supersedes: []
superseded_by: []
---

# REQ-082: Data backup and point-in-time recovery

## Statement

The system MUST ensure that PostgreSQL data (all ArchitectureEntities, metamodel configuration, audit events) is backed up daily and can be restored within a recovery time objective of **4 hours**. The recovery point objective (data loss window) MUST NOT exceed **24 hours**.

## Rationale

EA data are strategic corporate resources (SH-05: investment decisions; SH-06: sole EA platform). Data loss through DB corruption, accidental deletion or hardware failure without a backup strategy is not acceptable for OEA operators. The RPO of 24h means at most one working day of data loss — acceptable for EA data, which usually changes slowly.

## Context

Applies to production OEA instances. The concrete backup mechanics are the deployment responsibility of the operator; however, OEA MUST create the necessary prerequisites (pg_dump compatibility, documentation, optionally: built-in backup trigger). The REQ describes the requirement; the implementation technique (pg_dump, WAL archiving, managed DB snapshot) is chosen by the operator.

## Type-Specific Fields

### For type=non-functional

**Category**: reliability

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Scope |
|---|---|---|---|
| Recovery point objective (RPO) | ≤ 24 hours | > 12 hours | Production instance; daily backups |
| Recovery time objective (RTO) | ≤ 4 hours | > 2 hours | Complete restore from backup |
| Backup retention period | ≥ 30 days | < 14 days | Rotating backup archive |
| Backup validation | ≥ 1× per month | | Restore test on staging environment |

## Acceptance Criteria

**AC1** (Backup procedure documented):
- Given: fresh OEA installation via `docker compose up`
- When: Operator reads the documentation
- Then: Step-by-step instructions for `pg_dump`-based backup and restore are present; setup effort < 30 minutes

**AC2** (Restore test):
- Given: Backup of a production instance with 10,000 entities
- When: Restore onto an empty PostgreSQL instance via the documented procedure
- Then: All entities, metamodel configuration and audit events fully present; API responds correctly within 4 hours

**AC3** (RPO evidence):
- Given: daily backup at 02:00; database outage at 23:59
- When: Restore from the last backup
- Then: Data loss ≤ 22 hours (< 24h RPO)

**AC4** (Retention):
- Given: daily backups over 31 days
- When: Operator checks the backup archive
- Then: at least 30 backup generations present; oldest backup not deleted automatically

## Verification Method

- [x] Method: inspection (documentation) + test (restore drill)
- [x] Test setup: Restore test on staging environment with production data set; time measurement
- [x] Pass criterion: Restore within RTO; RPO met; 30 backup generations present
- [ ] Integrated in CI: no (production measurement); as release gate: backup documentation must exist

## Dependencies

- **Prerequisites**: ADR-016 (PostgreSQL 15; pg_dump-compatible); REQ-073 (availability)
- **Downstream effects**: Backup procedure must be documented in `docs/operations.md` (before walking skeleton release)

## Risks of Non-Fulfillment

- Data loss through DB outage or accidental deletion without the possibility of restoration
- Compliance violation for customers with internal data protection/audit policies

## Trade-offs

- `pg_dump` (logical backup): simple, but no PITR; sufficient for RPO 24h
- WAL archiving + `pg_basebackup`: enables PITR (minute-accurate recovery), but higher complexity — recommended as a v2.0 option if RPO < 1h is required

## Implementation Notes

- `docker-compose.yml` should contain an example volume mount and a cron comment for pg_dump
- `docs/operations.md` (to be created): backup/restore procedure, retention recommendation, restore test checklist

## Implementation

- ADR(s): ADR-016
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-27 | requirements-engineer | Initial draft |

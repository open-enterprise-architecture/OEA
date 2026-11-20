## ADDED Requirements

### Requirement: Deletion conflicts stop a plateau go-live

The plateau go-live preview SHALL list per solution its deltas `modified` and `retiring` whose entity is deleted in the repository, and the plateau go-live SHALL be rejected with 409 `entity-deleted` naming the deltas while one exists, and nothing SHALL change.

Trace: US-157, owner decision 2026-11-20, ADR-019

#### Scenario: Deletion conflict in the preview

- **GIVEN** "ERP Extension", targeting "Target 2027", has a delta `modified` on "ERP-Core", which is then deleted
- **WHEN** Kurt reads the go-live preview of "Target 2027"
- **THEN** "ERP Extension" lists the delta as a deletion conflict

#### Scenario: Plateau go-live blocked

- **WHEN** Kurt takes "Target 2027" live with the deletion conflict
- **THEN** the response is 409 `entity-deleted` naming the delta, and no solution goes live

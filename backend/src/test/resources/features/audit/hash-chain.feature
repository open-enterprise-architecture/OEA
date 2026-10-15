# Spec: openspec audit/hash-chain – Verification command
# Source material: requirements/tests/REQ-174-audit-tamper-resistance.feature (AC3, AC5)

@REQ-174 @ADR-033
Feature: Tamper evidence of the audit trail

  Every audit record is a link of a hash chain computed by the database. The
  verification command recomputes the chain with read-only audit credentials;
  checkpoints exported to the application log reveal a chain that was cut back
  or recomputed.

  Background:
    Given a separate OEA database whose audit trail contains 5 events

  @AC3
  Scenario: Record modified directly in the database
    When a superuser changes one audit event directly in the database
    And the verification command runs
    Then it reports the first broken record with its chain sequence number
    And it exits with a failure status

  @AC5
  Scenario: Newest records deleted after a checkpoint
    Given a checkpoint of the chain head was exported to the application log
    When a superuser deletes the newest records and rewrites the chain head
    And the verification command runs with the exported checkpoint
    Then it reports that the database chain head is behind the checkpoint
    And it exits with a failure status

  @AC5
  Scenario: Chain recomputed after a checkpoint
    Given a checkpoint of the chain head was exported to the application log
    When a superuser changes one audit event before the checkpoint and recomputes the chain
    And the verification command runs with the exported checkpoint
    Then it reports that the chain diverges from the checkpoint
    And it exits with a failure status

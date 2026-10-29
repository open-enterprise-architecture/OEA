# audit/hash-chain Specification

## Purpose

Defines the tamper evidence of the audit trail: a hash chain over all audit records computed in the database, a verification command and checkpoints of the chain head kept outside the database.

## Requirements

### Requirement: Hash chain over all audit records

Every record of the audit trail SHALL be a link of one hash chain with a gapless sequence number, the hash of the previous link and its own hash, computed by the database independently of values supplied by the application, following the published canonical serialization.

Trace: REQ-174, ADR-033 §5

#### Scenario: Chain values assigned by the database

- **WHEN** the application appends an audit record and supplies its own values for sequence number or hashes
- **THEN** the stored record carries the next sequence number, the hash of the previous link and a hash computed by the database, and the supplied values are ignored

### Requirement: Verification command

A verification command SHALL recompute the chain with read-only audit credentials and SHALL report the first broken link with its sequence number; given exported checkpoints, it SHALL report a database chain head that is behind or diverges from them. It SHALL read checkpoints from plain text log lines and from JSON log lines of every selectable format, also mixed in one file, and SHALL ignore all other lines.

Trace: REQ-174 AC3, REQ-174 AC5, ADR-033 §5, issue #63

#### Scenario: Record modified directly in the database

- **WHEN** a superuser modifies or deletes one row of the audit events table directly in the database and the verification command runs
- **THEN** it reports the first broken record with its chain sequence number and exits with a failure status

#### Scenario: Chain recomputed or backup restored after a checkpoint

- **WHEN** a superuser deletes the newest records and recomputes the chain, or restores a backup older than an exported checkpoint, and the verification command runs with that checkpoint as input
- **THEN** it reports that the database chain head is behind or diverges from the checkpoint

#### Scenario: Checkpoints from a JSON log

- **WHEN** the verification command reads a log file that contains checkpoint entries in plain text and in the JSON formats `ecs`, `logstash` and `gelf`
- **THEN** it uses every checkpoint and reports the same result as for the same checkpoints in plain text

### Requirement: Checkpoints outside the database

The application SHALL periodically, and at orderly shutdown, append a checkpoint record and emit the chain sequence number and hash of the chain head as a structured application log entry. The entry SHALL carry the fields `event` (`audit.checkpoint`), `chainSeq`, `recordHash` and `at`, and its message SHALL keep the form `event=audit.checkpoint chainSeq=… recordHash=… at=…`.

Trace: REQ-174, REQ-174 AC5, ADR-033 §6, issue #63

#### Scenario: Periodic checkpoint

- **WHEN** the checkpoint interval elapses
- **THEN** an `audit.checkpoint` record is appended and a log entry with event `audit.checkpoint`, its chain sequence number, record hash and timestamp is written

#### Scenario: Checkpoint in a JSON log

- **WHEN** the JSON log format is selected and a checkpoint is written
- **THEN** the log line is a JSON object with the fields `event`, `chainSeq`, `recordHash` and `at` and the unchanged message

# Audit Hash Chain – Canonical Serialization

**Version:** `hash_version = 1`
**Status:** binding for the database trigger, the verification command `oea audit verify` and external verifiers
**Background:** [ADR-033](../adrs/ADR-033-audit-trail-hardening.md) §5 and §6, [REQ-174](../requirements/req/REQ-174-audit-tamper-resistance.md)

Every row of `audit.events`, `audit.event_changes` and `audit.retention_settings` is a link of one hash chain ordered by `chain_seq`. The database computes the chain values in a `BEFORE INSERT` trigger owned by `oea_owner`; values supplied by the application are overwritten. Anyone holding an export or read access can recompute the chain with plain SHA-256 – no OEA secret is involved. This document defines the exact input of the hash. It changes only together with a new `hash_version`.

## Chain Fields

| Column | Type | Meaning |
|---|---|---|
| `chain_seq` | `BIGINT` | Position in the chain, gapless, starting at 1 |
| `hash_version` | `SMALLINT` | Version of this serialization (1) |
| `prev_hash` | `BYTEA` (32 bytes) | `record_hash` of the link `chain_seq - 1`; 32 zero bytes for `chain_seq = 1` (genesis) |
| `record_hash` | `BYTEA` (32 bytes) | SHA-256 of the input defined below |

The head of the chain (`last_seq`, `last_hash`) is kept in the single row of `audit.chain_head`. The trigger locks this row, so links are appended strictly one after the other.

## Hash Input

```text
record_hash = SHA-256( F(hash_version) || F(chain_seq) || F(prev_hash) || F(table_tag) || F(field_1) || … || F(field_n) )

F(value) = 4-byte unsigned big-endian length of the value bytes || value bytes
F(NULL)  = FF FF FF FF          (no value bytes)
```

`||` is byte concatenation. `hash_version` and `chain_seq` are encoded as decimal text like every integer; `prev_hash` as its 32 raw bytes.

## Value Bytes per Column Type

| Type | Value bytes |
|---|---|
| `TEXT` | UTF-8 bytes of the stored text |
| `SMALLINT`, `INTEGER`, `BIGINT` | Decimal text without sign for positive values, leading `-` for negative values, no leading zeros |
| `BOOLEAN` | `true` or `false` |
| `UUID` | Lower-case canonical form `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `TIMESTAMPTZ` | UTC in the form `YYYY-MM-DDTHH:MM:SS.ffffffZ` with exactly six fractional digits |
| `INET` | PostgreSQL text output of the value (for example `192.0.2.10` or `2001:db8::1`; a host address carries no prefix length) |
| `JSONB` | PostgreSQL's normalized text output of the value (`jsonb::text`) |
| `BYTEA` | Raw bytes (ciphertext is hashed as stored) |

## Table Tags and Field Order

The technical key `id` is part of the hash: it links `audit.event_changes` to `audit.events` and must not be changeable without breaking the chain. The chain columns themselves are not repeated in the field list.

### `audit.events` – table tag `events`

1. `id`
2. `event_type`
3. `event_category`
4. `severity`
5. `outcome`
6. `occurred_at`
7. `source`
8. `source_ip`
9. `request_id`
10. `actor_type`
11. `actor_person_id`
12. `actor_admin_id`
13. `target_type`
14. `target_id`
15. `entity_id`
16. `entity_name`
17. `details`
18. `agent_id`
19. `agent_label`
20. `agent_run`

### `audit.event_changes` – table tag `event_changes`

1. `id`
2. `event_id`
3. `property_def_id`
4. `property_name`
5. `system_field`
6. `value_protection`
7. `old_value`
8. `new_value`
9. `old_value_enc`
10. `new_value_enc`
11. `key_id`

### `audit.retention_settings` – table tag `retention_settings`

1. `id`
2. `retention_days`
3. `recorded_at`
4. `effective_from`
5. `recorded_by_person_id`
6. `recorded_by_admin_id`

A column added to one of these tables later is **not** hashed under `hash_version = 1`; including it requires `hash_version = 2` and an update of this document, the trigger and the verification command.

## Worked Example

The genesis link: the seed row of `audit.retention_settings` with `id = 1`, `retention_days = 2555`, `recorded_at = effective_from = 2026-10-15T08:00:00.000000Z` and no recorder.

| Part | Value | Encoded (hex) |
|---|---|---|
| `hash_version` | `1` | `00000001 31` |
| `chain_seq` | `1` | `00000001 31` |
| `prev_hash` | 32 zero bytes | `00000020 00…00` |
| `table_tag` | `retention_settings` | `00000012 726574656e74696f6e5f73657474696e6773` |
| `id` | `1` | `00000001 31` |
| `retention_days` | `2555` | `00000004 32353535` |
| `recorded_at` | `2026-10-15T08:00:00.000000Z` | `0000001b 323032362d31302d31355430383a30303a30302e3030303030305a` |
| `effective_from` | `2026-10-15T08:00:00.000000Z` | `0000001b 323032362d31302d31355430383a30303a30302e3030303030305a` |
| `recorded_by_person_id` | NULL | `ffffffff` |
| `recorded_by_admin_id` | NULL | `ffffffff` |

```text
record_hash = 643769ba104bab2f3320bdc1195390d0432a16618a21a475c34d506641b64e00
```

The backend test suite checks that the Java implementation of the verification command produces this value and that it reproduces the trigger-computed hash of stored records covering every column type.

## Verification

`oea audit verify` (see [operations](operations.md)) reads the three tables ordered by `chain_seq`, recomputes every `record_hash`, and checks that `chain_seq` is gapless and that every `prev_hash` equals the `record_hash` of the previous link. It reports the **first broken link** with its `chain_seq`, table, and expected and stored hash.

With exported checkpoints as input it also reports a database chain head that is behind a checkpoint, or a checkpoint whose hash differs from the stored link with the same `chain_seq`.

## Checkpoints

The application appends an `audit.checkpoint` event periodically and at orderly shutdown and writes one application log line per checkpoint:

```text
event=audit.checkpoint chainSeq=<chain_seq> recordHash=<64 hex digits> at=<UTC timestamp>
```

The values are those of the checkpoint record itself. Log lines are kept outside the database; the verification command accepts a file containing such lines (other lines are ignored).

---
identifier: system-settings
name_de: Instanz-Einstellungen
name_en: System Settings
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Instance Settings
  - SystemSettings
related_capabilities: []
references:
  - concept: concept/70-platform/22-analyzability.md
  - adr: adrs/ADR-032-authorization-model.md
  - adr: adrs/ADR-033-audit-trail-hardening.md
  - adr: adrs/ADR-034-secured-raw-analytics.md
---

# Business Object: System Settings

## Definition

The system settings are the single, instance-wide set of operator-controlled switches and limits of an OEA instance that can be changed at runtime by a principal holding `settings:manage`.

## Description

Each OEA instance has exactly one settings record (table `system_settings`, single row). Settings change behavior without a redeployment; deployment-time configuration (database URLs, credentials, keys, migration roles) is not part of this object.

Settings that need protection against the administrators who manage them are deliberately **not** here: the audit retention period is an append-only series in the audit schema with a database-enforced floor (see [audit event](./audit-event.md), `AuditRetentionSetting`; ADR-033 §4). `auditRetentionDays` was removed from the generic settings.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| privilegedAssignmentApproval | boolean | required | false | | Four-eyes approval for privileged role assignments, permissions and IdP group mappings (owner decisions Q15, Q18) |
| allowReactivationOfOffboarded | boolean | required | false | | Whether an `offboarded` person can be reactivated (owner decision Q19) |
| sessionRetentionDays | integer | required | 30 | > 0 | Days an ended session record is kept (owner decision Q20, REQ-189 AC4) |
| analytics.enabled | boolean | required | false | | Raw SQL analytics on/off; enabled by the operator after provisioning the `oea_analytics` credentials (ADR-034 §7) |
| analytics.statementTimeoutSeconds | integer | required | 30 | 1–120 | Maximum execution time of one analytics query |
| analytics.maxRows | integer | required | 10000 | 1–100000 | Row limit; result marked truncated above |
| analytics.maxResultBytes | integer | required | 10485760 | > 0 | Result size limit (10 MB of serialized JSON) |
| analytics.maxConcurrentPerPrincipal | integer | required | 2 | > 0 | Concurrent analytics queries per principal |
| analytics.queriesPerMinute | integer | required | 30 | > 0 | Analytics queries per principal and minute |
| analytics.poolSize | integer | required | 4 | > 0; must match the `CONNECTION LIMIT` of `oea_analytics` | Size of the separate analytics connection pool (global concurrency) |
| updatedAt | datetime | required | | | Time of the last change |
| updatedBy | principal | optional | | person or system admin account | Actor of the last change |

Per-request analytics values (`maxRows`, `timeoutSeconds`) may only lower these limits.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| updatedBy | [person](./person.md) or [system-admin-account](./system-admin-account.md) | n:1 | yes | Last editor |

## Lifecycle

No lifecycle; the single record is created by the first migration with the defaults and only updated afterwards.

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Settings can be changed only by principals holding `settings:manage`; every change is audited (`settings.changed`) with old and new value | onUpdate | ADR-032, REQ-175 |
| BR-02 | Exactly one settings record exists per instance | onCreate | – |
| BR-03 | Analytics limits are enforced by the server; a request can only lower them; `analytics.statementTimeoutSeconds` cannot exceed the role default of `oea_analytics` | onAnalyticsQuery | ADR-034 §5, REQ-186 |
| BR-04 | The audit retention period is not a system setting; it is changed only through the dedicated retention operation (floor 365 days, reductions effective after 30 days) | onUpdate | ADR-033 §4, Q10 |

## Examples

**Example 1**: Analytics enabled with lower limits

```yaml
analytics.enabled: true
analytics.statementTimeoutSeconds: 20
analytics.maxRows: 5000
analytics.poolSize: 4
sessionRetentionDays: 30
privilegedAssignmentApproval: true
```

## Distinction from Similar Objects

- **NOT** [metamodel-configuration](./metamodel-configuration.md): the metamodel shapes EA content; system settings control platform behavior.
- **NOT** deployment configuration: secrets, keys, database credentials and role names are set at deployment time, not through `settings:manage`.
- **NOT** the audit retention setting: see [audit-event](./audit-event.md).

## Usage in Use Cases

- No dedicated use case yet; settings are maintained by the system admin ([UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md) creates the first administrator).

## Concept Reference

- [§22 Analyzability and Query Architecture](../concept/70-platform/22-analyzability.md)
- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Created for ADR-034 DM-5. The settings were so far referenced by other business objects (`privilegedAssignmentApproval`, `allowReactivationOfOffboarded`, `sessionRetentionDays`) and the API schema `SystemSettings`, but not modeled. The storage form (single typed row with database CHECKs instead of a key-value table) is a modeling proposal, chosen so that value ranges are enforced in the database. Further settings defined only in the API (`SystemSettings` in `api/openapi.yaml`) still have to be added here.

Open question: whether the audit checkpoint interval (ADR-033 §6, 5–1440 minutes, default 60) is an instance setting or deployment configuration.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft for ADR-034 DM-5 (analytics settings); existing settings of ADR-032 / Q19 / Q20 consolidated; audit retention excluded (ADR-033 §4) |

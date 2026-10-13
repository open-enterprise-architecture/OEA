# ADR-028: Backend Layered Architecture — API / Application / Core

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)

---

## Context

ADR-027 defines the package structure of the backend, but leaves open how the
layers communicate internally. Two questions are open:

1. Do REST controllers call the core services directly, or is there an
   intermediate layer?
2. How does the external API stay stable when the internal domain model changes?

**The problem without an intermediate layer**: If `EntityController` directly calls `EntityService`
from `core/` and `EntityService` changes its interface due to domain changes,
the controller breaks. With API v2, the v1 controller must also be
adjusted. With ADR-026 (batch write via REST and possibly in the future via
message queue), the same logic must be called in two places.

---

## Decision

The backend uses three clearly separated layers with a strict
dependency rule: **dependencies always point inward.**

```
┌─────────────────────────────────────────┐
│  io.oea.api   (REST adapter)            │  ← knows app, not core
│  Controllers, request/response DTOs     │
└───────────────┬─────────────────────────┘
                │ calls (via interface)
┌───────────────▼─────────────────────────┐
│  io.oea.app   (application services)   │  ← knows core, not api
│  Stable interfaces + commands/queries   │
└───────────────┬─────────────────────────┘
                │ calls
┌───────────────▼─────────────────────────┐
│  io.oea.core  (domain)                  │  ← knows nothing above it
│  Domain model, repositories, services   │
└─────────────────────────────────────────┘
```

### Layer 1 — `io.oea.api` (REST Adapter)

Responsible for:
- HTTP mapping (paths, methods, status codes)
- Request validation (`@Valid`)
- Translation request DTO → command/query
- Translation result → response DTO
- No business logic; no database access

```java
// Example: controller method
@PutMapping("/entities/by-external-id/{externalId}")
public ResponseEntity<EntityDetailResponse> upsert(
    @PathVariable String externalId,
    @Valid @RequestBody EntityWriteRequest request
) {
    var command = EntityMapper.toUpsertCommand(externalId, request);
    var result  = entityAppService.upsert(command);         // app layer
    return ResponseEntity.ok(EntityMapper.toResponse(result));
}
```

### Layer 2 — `io.oea.app` (Application Services)

Responsible for:
- **Stable interfaces** — these are the contracts that the API layer calls
- Use case orchestration (reading + writing in the right order)
- Transaction boundaries (`@Transactional` lives here, not in `core/`)
- Calling core services and repositories
- Writing to the audit log after a successful operation

```java
// Interface (stable; the API depends on it)
public interface EntityAppService {
    EntityResult upsert(UpsertEntityCommand command);
    EntityResult getById(long id);
    BatchWriteResult batchWrite(BatchWriteCommand command);
}

// Command object (purely data-carrying, no behavior)
public record UpsertEntityCommand(
    String externalId,
    String name,
    UUID   metatypeId,
    List<PropertyWriteValue> properties
) {}
```

**Commands** (for write operations) and **queries** (for read operations) are
simple records — no command bus, no mediator, no infrastructure.

### Layer 3 — `io.oea.core` (Domain)

Responsible for:
- Domain entities (`Entity`, `Metatype`, `PropertyDefinition`, ...)
- Repository interfaces (Spring Data JPA implementations here)
- Domain services for complex rules (property validation, constraint checks)
- Knows neither `api/` nor `app/` — pure domain logic

```java
// Domain service (domain logic only, no HTTP knowledge)
public class PropertyValidationService {
    public ValidationResult validate(Entity entity, Metatype metatype) { ... }
}
```

---

## Package Structure (complete)

```
io.oea/
├── api/
│   ├── v1/
│   │   ├── controller/         EntityController, CatalogController, ...
│   │   ├── dto/request/        EntityWriteRequest, BatchWriteRequest, ...
│   │   └── dto/response/       EntityDetailResponse, BatchWriteResponse, ...
│   └── shared/
│       └── mapper/             EntityMapper, MetatypeMapper, ...
│
├── app/
│   ├── entity/
│   │   ├── EntityAppService.java          (interface)
│   │   ├── EntityAppServiceImpl.java
│   │   ├── command/                       UpsertEntityCommand, BatchWriteCommand, ...
│   │   └── query/                         EntityDetailQuery, CatalogQuery, ...
│   ├── metamodel/
│   │   ├── MetamodelAppService.java
│   │   └── command/
│   └── admin/
│       ├── ServiceAccountAppService.java
│       └── command/
│
├── core/
│   ├── domain/
│   │   ├── entity/             Entity, EntityConnection, EntityPropertyValue, ...
│   │   ├── metamodel/          Metatype, PropertyDefinition, MetatypePropertyMapping, ...
│   │   └── auth/               Person, Role, MachineCredential, ...
│   ├── repository/             EntityRepository, MetatypeRepository, ...
│   └── service/                PropertyValidationService, ConstraintCheckService, ...
│
├── audit/                      AuditEventWriter, AuditEvent, AuditEventChange
├── integration/                BatchWriteService, ExternalIdResolver
└── config/                     SecurityConfig, FlywayConfig, AuditDataSourceConfig
```

---

## Stability Guarantee

The app service interfaces are the stable contract:

| Layer | Change frequency | Impact on others |
|---|---|---|
| `api/` (DTOs, controllers) | high (new fields, new versions) | only `api/` |
| `app/` (interfaces, commands) | low (breaking only on use case change) | `api/` must adapt |
| `core/` (domain) | medium (refactoring, new rules) | only `app/` must adapt |

**API v2**: `api/v2/controller/EntityController` calls the same `EntityAppService`
as `v1/`. The translation from new v2 DTOs to commands lives in `api/v2/`.

**ADR-026 batch write**: `BatchWriteAppService` is called both by
`v1/controller/EntityBatchController` and by the future
`integration/MessageQueueConsumer` — same logic, two entry points.

---

## What This Layer Is Explicitly NOT

- **No command bus / mediator** (e.g. MediatR pattern): No additional
  infrastructure framework. App services are called directly via Spring injection.
- **No CQRS with separate read/write models**: Read operations may
  call repositories directly in app services — no separate query model.
- **No event sourcing infrastructure**: Delta-based history (ADR-022) is
  not event sourcing; events are written synchronously, not as primary
  data storage.

---

## Rejected Alternatives

### Controller calls core directly (without app layer)

- **Pro**: Fewer classes per feature
- **Con**: Controller tests test business logic; v1 and v2 share no code;
  batch write duplicates logic; core changes directly break controllers
- **Rejected**: Simpler in the short term, expensive in the long term

### Full hexagonal architecture with ports

- **Pro**: Maximum decoupling; core does not know Spring
- **Con**: Duplicate interfaces everywhere; massive overhead for v1 with one
  developer; Spring Data JPA is already a "port"
- **Rejected**: Overkill for v1; app service interfaces give 80% of the benefit
  with 20% of the effort

---

## Consequences

**Positive:**
- Core refactoring never touches the REST controllers
- API v2 possible without core changes
- App services are testable with Spring tests without the HTTP stack
- Transaction boundaries explicit and central (`@Transactional` in `app/`)
- Audit log writing consistent after every app service call

**Negative / Compromises:**
- Approx. 2–3 more classes per feature (interface, impl, command)
- Discipline needed: core must never import `api/` (no compiler enforcement
  without Maven multi-module — hence code review obligation)

---

## Affected Concept Chapters

- §21 (Tech Stack Backend)

## Related ADRs

- ADR-027: Mono-repo and Maven module structure (updated)
- ADR-012: Backend Java/Spring Boot
- ADR-022: Structured property model (core domain changes)
- ADR-026: External integration (app services as common entry point)

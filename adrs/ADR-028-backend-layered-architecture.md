# ADR-028: Backend-Schichtenarchitektur — API / Application / Core

**Status**: accepted
**Datum**: 2026-06-29
**Entscheider**: [Lukas – Repository-Inhaber](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Kontext

ADR-027 legt die Paket-Struktur des Backends fest, lässt aber offen, wie die
Schichten intern kommunizieren. Zwei Fragen sind offen:

1. Rufen REST-Controller die Core-Services direkt auf, oder gibt es eine
   Zwischenschicht?
2. Wie bleibt die externe API stabil, wenn sich das interne Domain-Modell ändert?

**Das Problem ohne Zwischenschicht**: Wenn `EntityController` direkt `EntityService`
aus `core/` aufruft und `EntityService` sein Interface wegen Domain-Änderungen
anpasst, bricht der Controller. Bei API v2 muss der v1-Controller ebenfalls
angepasst werden. Bei ADR-026 (Batch-Write via REST und ggf. künftig via
Message Queue) muss dieselbe Logik an zwei Stellen aufgerufen werden.

---

## Entscheidung

Das Backend verwendet drei klar getrennte Schichten mit einer strikten
Abhängigkeitsregel: **Abhängigkeiten zeigen immer nach innen.**

```
┌─────────────────────────────────────────┐
│  io.oea.api   (REST-Adapter)            │  ← kennt app, nicht core
│  Controller, Request/Response DTOs      │
└───────────────┬─────────────────────────┘
                │ ruft auf (via Interface)
┌───────────────▼─────────────────────────┐
│  io.oea.app   (Application Services)   │  ← kennt core, nicht api
│  Stabile Interfaces + Commands/Queries  │
└───────────────┬─────────────────────────┘
                │ ruft auf
┌───────────────▼─────────────────────────┐
│  io.oea.core  (Domain)                  │  ← kennt niemanden über sich
│  Domain Model, Repositories, Services   │
└─────────────────────────────────────────┘
```

### Schicht 1 — `io.oea.api` (REST-Adapter)

Verantwortlich für:
- HTTP-Mapping (Pfade, Methoden, Status-Codes)
- Request-Validierung (`@Valid`)
- Übersetzung Request-DTO → Command/Query
- Übersetzung Result → Response-DTO
- Keine Business-Logik; kein Datenbankzugriff

```java
// Beispiel: Controller-Methode
@PutMapping("/entities/by-external-id/{externalId}")
public ResponseEntity<EntityDetailResponse> upsert(
    @PathVariable String externalId,
    @Valid @RequestBody EntityWriteRequest request
) {
    var command = EntityMapper.toUpsertCommand(externalId, request);
    var result  = entityAppService.upsert(command);         // App-Layer
    return ResponseEntity.ok(EntityMapper.toResponse(result));
}
```

### Schicht 2 — `io.oea.app` (Application Services)

Verantwortlich für:
- **Stabile Interfaces** — das sind die Verträge, die die API-Schicht aufruft
- Use-Case-Orchestrierung (Lesen + Schreiben in der richtigen Reihenfolge)
- Transaktionsgrenzen (`@Transactional` liegt hier, nicht in `core/`)
- Aufrufen von Core-Services und Repositories
- Schreiben in das Audit-Log nach erfolgreicher Operation

```java
// Interface (stabil; die API hängt davon ab)
public interface EntityAppService {
    EntityResult upsert(UpsertEntityCommand command);
    EntityResult getById(long id);
    BatchWriteResult batchWrite(BatchWriteCommand command);
}

// Command-Objekt (rein datentragend, kein Verhalten)
public record UpsertEntityCommand(
    String externalId,
    String name,
    UUID   metatypeId,
    List<PropertyWriteValue> properties
) {}
```

**Commands** (für Schreiboperationen) und **Queries** (für Leseoperationen) sind
einfache Records — kein Command-Bus, kein Mediator, keine Infrastruktur.

### Schicht 3 — `io.oea.core` (Domain)

Verantwortlich für:
- Domain-Entities (`Entity`, `Metatype`, `PropertyDefinition`, ...)
- Repository-Interfaces (Spring Data JPA Implementierungen hier)
- Domain-Services für komplexe Regeln (Property-Validierung, Constraint-Checks)
- Kennt weder `api/` noch `app/` — reine Domänenlogik

```java
// Domain-Service (nur Domänen-Logik, keine HTTP-Kenntnis)
public class PropertyValidationService {
    public ValidationResult validate(Entity entity, Metatype metatype) { ... }
}
```

---

## Paket-Struktur (vollständig)

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
│   │   ├── EntityAppService.java          (Interface)
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

## Stabilitäts-Garantie

Die App-Service-Interfaces sind der stabile Vertrag:

| Schicht | Änderungsfrequenz | Auswirkung auf andere |
|---|---|---|
| `api/` (DTOs, Controller) | hoch (neue Felder, neue Versionen) | nur `api/` |
| `app/` (Interfaces, Commands) | niedrig (breaking only bei Use-Case-Änderung) | `api/` muss anpassen |
| `core/` (Domain) | mittel (Refactoring, neue Regeln) | nur `app/` muss anpassen |

**API v2**: `api/v2/controller/EntityController` ruft denselben `EntityAppService`
auf wie `v1/`. Die Übersetzung von neuen v2-DTOs zu Commands liegt in `api/v2/`.

**ADR-026 Batch-Write**: `BatchWriteAppService` wird sowohl von
`v1/controller/EntityBatchController` als auch vom künftigen
`integration/MessageQueueConsumer` aufgerufen — gleiche Logik, zwei Einstiegspunkte.

---

## Was diese Schicht explizit NICHT ist

- **Kein Command-Bus / Mediator** (z.B. MediatR-Muster): Kein zusätzliches
  Infrastruktur-Framework. App-Services werden direkt per Spring-Injection aufgerufen.
- **Kein CQRS mit getrennten Read/Write-Models**: Leseoperationen dürfen
  Repositories direkt in App-Services aufrufen — kein separates Query-Modell.
- **Keine Event-Sourcing-Infrastruktur**: Delta-basierte History (ADR-022) ist
  kein Event Sourcing; Events werden synchron geschrieben, nicht als primäre
  Datenhaltung.

---

## Verworfene Alternativen

### Controller ruft Core direkt (ohne App-Schicht)

- **Pro**: Weniger Klassen pro Feature
- **Contra**: Controller-Tests testen Business-Logik; v1 und v2 teilen keinen Code;
  Batch-Write dupliziert Logik; Core-Änderungen brechen direkt Controller
- **Verworfen**: Kurzfristig einfacher, langfristig teuer

### Vollständige Hexagonale Architektur mit Ports

- **Pro**: Maximale Entkopplung; Core kennt Spring nicht
- **Contra**: Doppelte Interfaces überall; für v1 mit einem Entwickler massiver
  Overhead; Spring Data JPA bereits ein "Port"
- **Verworfen**: Overkill für v1; App-Service-Interfaces geben 80% des Nutzens
  mit 20% des Aufwands

---

## Konsequenzen

**Positiv:**
- Core-Refactoring berührt nie die REST-Controller
- API v2 ohne Core-Änderungen möglich
- App-Services sind mit Spring-Tests ohne HTTP-Stack testbar
- Transaktionsgrenzen explizit und zentral (`@Transactional` in `app/`)
- Audit-Log-Schreiben konsistent nach jedem App-Service-Call

**Negativ / Kompromisse:**
- Pro Feature ca. 2–3 Klassen mehr (Interface, Impl, Command)
- Disziplin nötig: Core darf nie `api/` importieren (kein Compiler-Enforcement
  ohne Maven-Multi-Module — daher Code-Review-Pflicht)

---

## Betroffene Konzept-Kapitel

- §21 (Tech-Stack Backend)

## Verwandte ADRs

- ADR-027: Mono-Repo und Maven-Modul-Struktur (aktualisiert)
- ADR-012: Backend Java/Spring Boot
- ADR-022: Strukturiertes Property-Modell (Core-Domain-Änderungen)
- ADR-026: Externe Integration (App-Services als gemeinsamer Einstiegspunkt)

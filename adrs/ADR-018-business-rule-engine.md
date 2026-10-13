# ADR-018: Business Rule Engine – CEL with GUI Abstraction Layer

**Status**: accepted
**Date**: 2026-06-27
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Business Engineer
**Informed**: ADR-012 (backend stack), ADR-017 (layer strategy)
**Supersedes**: –
**Updated**: 2026-06-28 — CEL library corrected: `@cel-community/cel-js` (npm/Node.js) → `dev.cel:cel` (Google CEL-Java, Maven); decision driver "TypeScript/Node.js" was wrong, ADR-012 chose Java 21 + Spring Boot 3

## Context and Problem

OEA allows operators to define validation rules for architecture entities (`ConstraintRule`, `MandatoryConnectionConstraint` in the `MetamodelConfiguration`). These rules should:

1. Be creatable, editable, and deletable in the web GUI
2. Be importable and exportable as a JSON file
3. Be lockable to import-only via admin setting (no GUI edit)
4. Be operable by non-technical configurers (enterprise architects) without syntax knowledge

What was open: Which OSS technology evaluates the rule expressions, and how is the configuration interface designed?

## Decision Drivers

- **Target group is not developers**: Admins and enterprise architects should be able to build rules in the GUI without knowing CEL/code
- **Expressiveness**: Simple null checks up to multiple conditions with AND/OR; cardinality checks on arrays
- **JSON serializability**: Rules must be importable/exportable as JSON
- **Java 21 + Spring Boot 3** ([ADR-012](./ADR-012-backend-stack.md)): the rule engine must be JVM-compatible; Node.js/npm libraries are ruled out
- **Sandboxing**: Rule evaluation must be safe (no `eval`); rules must have no side effects
- **Future-proofing**: Expression language should be extensible without breaking the rule format

## Considered Options

### Option 1: CEL + GUI abstraction layer ✓

**Common Expression Language (CEL)** as evaluation engine; the configurer never sees the CEL syntax — the GUI generates CEL expressions from a visual condition builder. Power users can optionally enter CEL directly.

Java implementation: **`dev.cel:cel`** — Google's official CEL reference implementation for the JVM (Apache 2.0). The same organization that maintains the CEL specification. In production in Kubernetes Java clients and Istio Java integrations.

```xml
<!-- backend/pom.xml -->
<dependency>
  <groupId>dev.cel</groupId>
  <artifactId>cel</artifactId>
  <version>[0.7,0.8)</version>
</dependency>
```

- **Pro**: CEL is type-safe, sandboxed, without side effects; established in Google Cloud, Kubernetes admission policies, and Firebase; `dev.cel:cel` is the official Java reference implementation of the CEL specification; expression syntax is 1:1 identical to the originally planned JS implementation — GUI builder, JSON format, and CEL preview remain valid unchanged; expressive enough for all foreseeable v1.0 use cases; GUI abstraction decouples the user experience from the expression language; Apache 2.0
- **Con**: `dev.cel:cel` is still in the 0.x version range (API may change); younger in the Java ecosystem than Drools or SpEL; GUI builder development is its own effort

### Option 2: json-rules-engine

Rules are natively JSON-structured (conditions/events); no separate expression string.

- **Pro**: Rules are directly JSON — no compile step; actively maintained; simplest import/export
- **Con**: No free expression string possible; power users cannot work outside the predefined operators; CEL-level expressiveness not achievable; hard API lock-in to the json-rules-engine format

### Option 3: Own expression parser

OEA implements its own mini expression language.

- **Pro**: Full control over syntax and feature set
- **Con**: High development effort; security risks to be borne ourselves; no ecosystem; ruled out

### Option 4: No OSS engine — structured conditions only

GUI builder only, no free expression mode.

- **Pro**: Simplest implementation
- **Con**: Too inflexible for enterprise requirements; cannot cover all TOGAF constraint types

## Decision

**Option 1: CEL (`dev.cel:cel`, Google CEL-Java) with GUI abstraction layer.**

Core principle: **CEL is the evaluator — invisible to configurers.** The GUI generates CEL internally; the configurer works with a visual condition builder.

---

### Two Rule Modes

Each `ConstraintRule` has a `ruleMode`:

| Mode | Who uses it | How it is created |
|---|---|---|
| `structured` | Configurers without syntax knowledge | GUI builder generates CEL internally; user sees only fields and dropdowns |
| `expression` | Developers, experienced admins | Direct entry of a CEL expression; GUI shows text field with syntax hint |

In both modes the GUI shows a CEL preview (read-only), so that power users can see what is executed.

---

### Condition Builder (structured mode)

The GUI builder produces structured conditions that the backend compiles to CEL:

```
[ Property: owner ▼ ] [ Operator: is not empty ▼ ]
[ AND ]
[ Property: architectureDomainIds ▼ ] [ Operator: contains at least ▼ ] [ Value: 1 ]

→ CEL preview: entity.owner != null && entity.architectureDomainIds.size() >= 1
```

Supported operators v1.0:

| Category | Operators |
|---|---|
| Null checks | `notNull`, `isNull` |
| String | `equals`, `notEquals`, `contains`, `startsWith`, `matches` (regex) |
| Numeric | `equals`, `notEquals`, `greaterThan`, `lessThan`, `between` |
| Array | `minCount`, `maxCount`, `contains`, `notContains` |
| Logical | `all` (AND), `any` (OR) |

---

### JSON Import/Export Format

Both modes are JSON-importable. The backend validates the format and compiles `structured` rules to CEL before persistence:

```json
{
  "schemaVersion": "1.0",
  "constraintRules": [
    {
      "name": "appcomp-owner-required",
      "appliesTo": "application-component",
      "severity": "warning",
      "message": "Every ApplicationComponent must have an owner.",
      "ruleMode": "structured",
      "conditions": {
        "all": [
          { "property": "owner", "operator": "notNull" }
        ]
      }
    },
    {
      "name": "interface-naming-convention",
      "appliesTo": "interface",
      "severity": "hint",
      "message": "Interface name should start with 'I-'.",
      "ruleMode": "expression",
      "expression": "entity.name.startsWith('I-')"
    }
  ],
  "mandatoryConnectionConstraints": [
    {
      "name": "appcomp-must-run-on-tech",
      "sourceEntityType": "application-component",
      "connectionType": "runs-on",
      "targetEntityType": "technology-component",
      "validationMode": "warning",
      "message": "An ApplicationComponent should run on at least one TechnologyComponent."
    }
  ]
}
```

Import endpoint: `POST /api/v1/metamodel/rules/import`
Import preview: `POST /api/v1/metamodel/rules/import/preview` (**dry run**, v1.0)
Export endpoint: `GET /api/v1/metamodel/rules/export`

**Import semantics**: Replace-all (existing rules are replaced completely). The dry-run endpoint returns a diff preview (`added`, `removed`, `unchanged`) without actually saving the rules — protection against accidental deletion. A merge mode (add/update/delete individual rules) is deferred to v2.0.

---

### rulesEditMode

`MetamodelConfiguration` receives a new field `rulesEditMode` (independent of `editMode`):

| `rulesEditMode` | Effect |
|---|---|
| `gui-and-import` | Rules changeable via GUI and JSON import (default) |
| `import-only` | GUI CRUD for rules disabled; only import possible |

Possible combinations with `editMode`:

| `editMode` | `rulesEditMode` | Effect |
|---|---|---|
| `gui-and-import` | `gui-and-import` | Everything editable via GUI |
| `import-only` | `gui-and-import` | Metamodel locked, rules maintainable via GUI |
| `gui-and-import` | `import-only` | Rules only via JSON import; rest via GUI |
| `import-only` | `import-only` | Full GitOps operation |

---

### CEL Security Context

CEL is executed in a restricted context (Java implementation via `dev.cel:cel`):

- No file system access, no network call — CEL is side-effect-free by specification
- No side effects (pure evaluation); no reflection access to arbitrary Java classes
- Timeout: max. 100 ms per rule evaluation (Spring `@Async` + `CompletableFuture.get(100, MILLISECONDS)`)
- Evaluation context: `{ entity: ArchitectureEntity, metamodel: MetamodelContext }`

```java
// Example: CEL evaluation in the Spring service
Cel cel = CelFactory.standardCelBuilder()
    .addVar("entity", CelTypes.createMap(CelTypes.STRING, CelTypes.DYN))
    .build();
CelProgram program = cel.createProgram(cel.compile(expression).getAst());
Object result = program.eval(Map.of("entity", entityProperties));
```

---

## Consequences

### Positive Consequences

- Configurers without programming knowledge can build complete rule sets in the GUI
- Power users can use CEL directly when the builder reaches its limits
- JSON format is human-readable and versionable (e.g. in Git)
- CEL is established in the Kubernetes ecosystem — developers know the syntax
- `rulesEditMode` enables GitOps operation without locking the rest of the metamodel configuration

### Negative Consequences / Trade-offs

- GUI condition builder is a standalone frontend effort
- `dev.cel:cel` is still in the 0.x version range; check API stability before the Walking Skeleton (alternative fallback: Spring Expression Language `SimpleEvaluationContext` — without CEL standards conformance, but zero additional dependency)
- Import semantics (replace-all) can delete all rules by mistake → dry-run endpoint (`/import/preview`) mitigates the risk; additionally UI warning before confirmation
- `structured`-to-CEL compilation must be tested; inconsistency between builder display and actually executed CEL possible

### Follow-up Decisions / Open Questions

- **Operator extension**: which operators in v2.0 (e.g. cross-entity checks: "every interface must have at least one assigned ApplicationComponent")? Affects CEL context extension.
- **Import dry run**: `/import/preview` returns diff without persistence (v1.0, decided)
- **Import merge mode**: Merge (add/update/delete individual rules) comes in v2.0; replace-all remains the v1.0 default
- **`MandatoryConnectionConstraint` builder**: structured builder for connection constraints analogous to `ConstraintRule`; for v1.0 only JSON import or simple form

## References

**Related ADRs**: [ADR-012](./ADR-012-backend-stack.md) (Java 21 + Spring Boot 3), [ADR-017](./ADR-017-architecture-layer-strategy.md) (Fully Open → rules as compensation)

**Business Objects**: [metamodel-configuration.md](../business-objects/metamodel-configuration.md) (`constraintRules`, `mandatoryConnectionConstraints`, `rulesEditMode`)

**Concept**: §14 Extensibility

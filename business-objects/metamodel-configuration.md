---
identifier: metamodel-configuration
name_de: Metamodell-Konfiguration
name_en: Metamodel Configuration
version: 1.3.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Metamodell
  - Schema-Konfiguration
  - Instanz-Metamodell
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept: concept/40-extensibility/14-extensibility.md
  - concept: concept/40-extensibility/15-schema-evolution.md
  - adr: adrs/ADR-017-architecture-layer-strategy.md
  - adr: adrs/ADR-018-business-rule-engine.md
  - adr: adrs/ADR-034-secured-raw-analytics.md
---

# Business Object: MetamodelConfiguration

## Definition

The `MetamodelConfiguration` is the instance-wide configuration that determines which entity types, stereotypes, and constraint rules are available in an OEA repository. It is the aggregate that bundles all schema elements managed by the architecture team.

## Description

Every OEA instance has exactly one `MetamodelConfiguration`. It consists of two layers:

**Built-in elements (built-in, read-only)**:
The TOGAF-based core entity types from [§6 Core Entity Types](../concept/20-entities/06-core-entity-types.md) are always present:

- Business Architecture: `Actor`, `Role`, `OrganizationUnit`, `BusinessService`, `BusinessFunction`, `BusinessProcess`, `Capability`, `ValueStream`, `InformationObject`
- Data Architecture: `DataEntity`, `LogicalDataComponent`, `PhysicalDataComponent`
- Application Architecture: `ApplicationComponent`, `ApplicationService`, `ApplicationFunction`, `Interface`
- Technology Architecture: `TechnologyComponent`, `Platform`, `Node`, `CommunicationPath`

These types cannot be deleted or changed in their basic structure. They can be extended by stereotypes and optional properties.

**Configurable elements**:
The architecture team can extend the configuration through seven categories (per [§14 Extensibility](../concept/40-extensibility/14-extensibility.md)):

1. **Custom EntityTypes**: new types that are not part of the TOGAF core (e.g. `SecurityZone`, `DataPipeline`); an EntityType can be marked as a **connection type** that models a connection between two entities and enforces start and target (REQ-036); every EntityType can receive any number of **PropertyDefinitions** with data type, validation mode, and category
2. **Stereotypes**: non-breaking extensions of existing types with additional properties (e.g. `SaaSApplication` on `ApplicationComponent`)
3. **ConstraintRules**: declarative expression validation rules for entities (e.g. "every Interface must have an owner")
4. **Viewpoints**: named architecture views that determine which EntityTypes and connection types may appear in a diagram of this type and in which notation it is displayed (ArchiMate 3, UML, or BPMN 2.0); see [viewpoint.md](./viewpoint.md)
5. **ArchitectureLayers**: freely definable architecture layers (e.g. business, application, technology, or domain-specific layers); exactly one layer can be assigned to each EntityType; they control sorting, color coding, and filterability in the Web Portal
6. **MandatoryConnectionConstraints**: declarative rules that enforce a mandatory connection between two EntityTypes (e.g. "every ApplicationComponent must have at least one `runs-on` connection to a TechnologyComponent"); they complement ConstraintRules with structural connection validation

The **edit mode** (`editMode`) controls whether changes to the metamodel are possible via GUI and import or exclusively via import. In lock mode `import-only`, GUI editing is disabled for all users; the YAML file becomes the single source of truth (REQ-035).

**Two-level scoping** (REQ-037): A `MetamodelConfiguration` has a `scope`:

- `scope=instance`: the instance default metamodel (singleton per instance); `editMode` can be controlled here; can be locked
- `scope=solution`: extension configuration for a specific [Solution](./solution.md); inherits all types of the instance configuration (`parentId` points to it); can add its own EntityTypes, stereotypes, and ConstraintRules; has its own `editMode` independent of the parent; solution types are visible only within this solution

The **effective metamodel** of a solution results as the union: instance types ∪ solution-specific types. Solution types cannot override or remove instance types.

Changes to the configuration are versioned and recorded in the audit log.

## Attributes

### MetamodelConfiguration (root object)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4, globally unique | Internal primary key |
| scope | enum | required | `instance` | `[instance, solution]` | Distinguishes instance default (singleton) from solution extension |
| instanceId | reference | required | | target: instance; unique if scope=instance | Link to the OEA instance |
| solutionId | reference | optional | null | target: solution; only set if scope=solution | Link to the solution (scope=solution) |
| parentId | reference | optional | null | target: MetamodelConfiguration (scope=instance); only set if scope=solution | Parent configuration whose types are inherited |
| schemaVersion | string | required | "1.0" | Semver | Current schema version of the configuration |
| lastModifiedAt | datetime | required | | ISO 8601, UTC | Time of the last change |
| lastModifiedBy | reference | required | | target: person | Person who made the last change |
| editMode | enum | required | `gui-and-import` | `[gui-and-import, import-only]` | Controls whether GUI editing of the metamodel (EntityTypes, stereotypes, layers) is allowed; `import-only` = lock mode (REQ-035); applies independently per scope level |
| rulesEditMode | enum | required | `gui-and-import` | `[gui-and-import, import-only]` | Controls GUI editing of `constraintRules` and `mandatoryConnectionConstraints` independently of `editMode`; `import-only` = only JSON import allowed (ADR-018) |
| entityTypeDefinitions | EntityTypeDefinition[] | required | [] | | List of user-defined entity types |
| stereotypes | Stereotype[] | required | [] | | List of defined stereotypes |
| constraintRules | ConstraintRule[] | required | [] | | List of expression constraint rules |
| viewpoints | ViewpointDefinition[] | required | [] | Contains system-defined (built-in) + user-defined viewpoints | List of viewpoints; see [viewpoint.md](./viewpoint.md) for the full specification |
| architectureLayers | ArchitectureLayerDefinition[] | required | [] | | List of architecture layers; empty = no layer categorization |
| mandatoryConnectionConstraints | MandatoryConnectionConstraint[] | required | [] | | Declarative mandatory-connection rules |
| documentCollectionDefinitions | DocumentCollectionDefinition[] | required | [] | | Operator-defined documentation types with chapters (Arc42, third-party, license register, etc.); see [document-collection.md](./document-collection.md) |

### EntityTypeDefinition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | kebab-case, globally unique | Stable identifier (e.g. `security-zone`) |
| name | string | required | | PascalCase, unique across all types | Display name (e.g. `SecurityZone`) |
| extends | string | optional | null | must be a valid EntityType name | Base type; null = no parent type |
| description | string | optional | | max. 1000 characters | Business description of the type |
| properties | PropertyDefinition[] | required | [] | | Type-specific attributes |
| relations | RelationDefinition[] | required | [] | | Allowed relations to other types |
| isBuiltIn | boolean | required | false | read-only; true only for §6 types | Marks built-in types |
| isConnection | boolean | required | false | | Marks the type as a connection type; enforces `source` and `target` on every instance (REQ-036) |
| allowedSourceTypes | string[] | optional | null | null = any EntityType or connection type permitted | Restriction: which types are allowed as start entity |
| allowedTargetTypes | string[] | optional | null | null = any EntityType or connection type permitted | Restriction: which types are allowed as target entity |
| allowsConnectionAsSource | boolean | optional | false | only meaningful if `isConnection=true` | Allows instances of this type to act as the `sourceEntityId` of a further connection (n-connection, ADR-010); default false |
| architectureLayerId | string | optional | null | must reference a valid ArchitectureLayerDefinition.id | Assignment to the architecture layer; null = no layer assigned |
| creationSteps | CreationStep[] | optional | [] | | Defines the wizard on creation; empty = simple creation form without wizard |

### PropertyDefinition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | camelCase; unique per EntityType | Technical attribute name (e.g. `investmentCostForecast`) |
| displayLabel | string | optional | | max. 100 characters | Display name in the GUI; overrides `name` if set |
| sqlColumnName | string | required | derived from `name` (snake_case) | regex `^[a-z][a-z0-9_]{0,62}$`; unique across all property definitions; not one of the reserved entity column names (`id`, `urn`, `metatype_id`, `name`, `description`, `is_logical`, `created_at`, `updated_at`); immutable after creation | Stable column name of the property in the generated reporting views `reporting.<metatype>` (ADR-034 DM-6); keeps analysts' SQL working when `name` or `displayLabel` change |
| dataType | PropertyDataType | required | | | Data type definition (details: PropertyDataType) |
| validationMode | enum | required | `optional` | `[mandatory, warning, optional]` | `mandatory` = error on save if empty; `warning` = notice, but saving allowed; `optional` = no check |
| category | string | required | | max. 100 characters; freely selectable | Category assignment (e.g. `"Costs"`, `"Organization"`, `"Compliance"`, `"Security"`) |
| defaultValue | string | optional | null | must match the dataType definition | Default value as string representation |
| description | string | optional | | max. 500 characters | Business description of the attribute |
| visibilityMode | enum | optional | `public` | `[public, role-restricted, connection-scoped]`; changeable only with `visibility:configure` | Controls who may read and write the value (REQ-124); `public` = all; `role-restricted` = only `readRoles`/`writeRoles`; `connection-scoped` = ABAC via graph traversal |
| readRoles | reference (list) | conditional | [] | target: [authorization-role](./authorization-role.md) IDs (not deleted); required with at least 1 entry if `visibilityMode=role-restricted`; changeable only with `visibility:configure` | Authorization roles that may read the value (REQ-124, ADR-032 DM-9) |
| writeRoles | reference (list) | optional | [] | target: authorization-role IDs; every entry must also be in `readRoles`; changeable only with `visibility:configure` | Authorization roles that may write the value; empty = nobody under `role-restricted` (REQ-124) |
| scopingConnectionType | string | conditional | null | Required if `visibilityMode=connection-scoped`; must reference a valid `isConnection=true` ConnectionType ID; changeable only with `visibility:configure` | Connection type that defines the scope (e.g. `DomainAssignment`); creating, changing or deleting connections of this type requires `visibility-scope:assign` (REQ-161) |

### PropertyDataType

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| kind | enum | required | | `[int, varchar, enum]` | Base type: `int` = integer; `varchar` = text with length; `enum` = selection list |
| maxLength | integer | conditional | | only for `kind=varchar`; value range 1–4000 | Maximum number of characters |
| enumValues | string[] | conditional | | only for `kind=enum`; at least 2 entries; unique | Allowed selection values |

### RelationDefinition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | camelCase | Relation name (e.g. `contains`) |
| target | string | required | | valid EntityType name | Target type of the relation |
| cardinality | string | required | | Format: `min..max` (e.g. `0..n`, `1..1`) | Cardinality |
| description | string | optional | | | Business meaning of the relation |

### Stereotype

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | PascalCase, unique | Stereotype name (e.g. `SaaSApplication`) |
| basedOn | string | required | | valid EntityType name | Base type that is extended |
| properties | PropertyDefinition[] | required | [] | | Additional attributes |
| description | string | optional | | | Business meaning |

### ConstraintRule

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | kebab-case, unique | Rule identifier (e.g. `every-interface-has-owner`) |
| appliesTo | string | required | | valid EntityType name | Scope of applicability: entity type |
| scope | ScopeDefinition | optional | null | null = applies to all instances of the type | Restriction to a subset (§15.2) |
| ruleMode | enum | required | `structured` | `[structured, expression]` | `structured` = GUI builder conditions (compiled to CEL); `expression` = direct CEL expression (ADR-018) |
| conditions | ConditionGroup | conditional | null | REQUIRED if `ruleMode=structured` | Nested AND/OR condition structure; compiled to CEL by the backend |
| expression | string | conditional | null | REQUIRED if `ruleMode=expression`; valid CEL expression | Direct CEL expression (e.g. `entity.owner != null`) |
| severity | enum | required | `warning` | `[hint, warning, error]` | Severity on rule violation |
| message | string | required | | max. 500 characters | Message for the user on violation |

### ConditionGroup

| Attribute | Type | Optional | Description |
|---|---|---|---|
| all | Condition[] | conditional | AND combination; at least one of `all` or `any` must be set |
| any | Condition[] | conditional | OR combination |

### Condition

| Attribute | Type | Optional | Description |
|---|---|---|---|
| property | string | required | Property name of the entity (e.g. `owner`, `criticality`) |
| operator | enum | required | `[notNull, isNull, equals, notEquals, contains, notContains, startsWith, matches, greaterThan, lessThan, between, minCount, maxCount]` |
| value | string \| number | conditional | Comparison value; not needed for `notNull`/`isNull` |
| group | ConditionGroup | optional | Nested subgroup (enables complex AND/OR combinations) |

### ArchitectureLayerDefinition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | kebab-case, unique | Technical identifier (e.g. `business-layer`, `application-layer`) |
| name | string | required | | unique in the MetamodelConfiguration | Display name (e.g. "Business", "Application", "Technology") |
| sortOrder | integer | required | | ≥ 0; controls display order | Order in lists and diagrams (ascending) |
| color | string | optional | | hex color code (#RRGGBB) | Color coding for visual distinction |
| description | string | optional | | max. 500 characters | Description of the scope of this layer |

**Usage**: Each `EntityTypeDefinition` can be assigned to exactly one layer via `architectureLayerId`. Entity instances inherit the layer of their type. Layers control sorting and filterability in catalogs and dashboards.

### MandatoryConnectionConstraint

Declarative rule that prescribes that every instance of a specific EntityType must have at least one connection of a defined connection type to a target EntityType.

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | | kebab-case, unique | Rule identifier (e.g. `appcomp-must-run-on-tech`) |
| sourceEntityType | string | required | | valid EntityType ID (built-in or custom) | Source entity type to which the rule applies |
| connectionType | string | required | | EntityType ID with `isConnection=true` | Connection type required as mandatory connection |
| targetEntityType | string | optional | null | valid EntityType ID or null | Target entity type; null = any target type accepted |
| validationMode | enum | required | `warning` | `[error, warning]` | `error` = saving blocked; `warning` = notice, saving allowed |
| message | string | required | | max. 500 characters | Error message or notice for the user |

### CreationStep

Describes a single page (step) in the creation wizard that is run through when creating an entity of this type. If `EntityTypeDefinition.creationSteps` is not empty, the system automatically opens a multi-page wizard instead of the simple creation form.

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| stepId | string | required | | kebab-case; unique within `creationSteps` | Technical identifier of the step |
| title | string | required | | max. 100 characters | Page title in the wizard (e.g. "Basic Data", "Domain Assignment") |
| description | string | optional | null | max. 500 characters | Help text below the title |
| stepType | enum | required | | `[properties, domainAssignment, connectionAssignment]` | Kind of wizard step |
| propertyCategory | string | conditional | null | Required if `stepType=properties` | Shows all `PropertyDefinition` fields of this category on the page |
| connectionType | string | conditional | null | Required if `stepType=connectionAssignment`; must have `isConnection=true` | Connection type that the user is to link here |
| targetEntityType | string | optional | null | only relevant if `stepType=connectionAssignment` | Restricts target selection to this type; null = any type allowed |
| minConnections | integer | optional | 1 | only relevant if `stepType=connectionAssignment`; ≥ 1 | Minimum number of connections; 0 = optional |

**stepType semantics**:

- `properties`: shows all `PropertyDefinitions` of the specified `propertyCategory` as a fillable form
- `domainAssignment`: shows a multi-select of all `ArchitectureDomainDefinition` entries; the user selects membership
- `connectionAssignment`: shows a search field + selection for target entities; creates connection(s) upon completion

**Wizard flow**: N steps → N-1 times "Next" + once "Finish". The entity is persisted atomically only after clicking "Finish" (entity + all specified connections in one transaction). Until then it does not exist in the repository.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| belongsTo | instance | 1:1 | no | Each instance has exactly one configuration |
| modifiedBy | [person](./person.md) | n:1 | no | The last change is assigned to a person |

**Notes**:

- The `MetamodelConfiguration` is not an EA model element; it is infrastructure that shapes the model.
- Changes to the configuration affect all entities created in the future; existing entities are migrated through schema evolution (§15).
- Built-in EntityTypes (`isBuiltIn=true`) are managed in code, not in the DB-stored configuration. They are visible in the API and UI but not editable.

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | An `EntityTypeDefinition.name` must be unique across all types (built-in + custom) | onCreate, onUpdate | – |
| BR-02 | A built-in EntityType (`isBuiltIn=true`) must not be deleted or changed in its basic structure | onDelete, onUpdate | – |
| BR-03 | A custom EntityType can only be deleted if no instance of this type exists in the repository | onDelete | – |
| BR-04 | A `ConstraintRule` with `severity=error` blocks saving of entities that violate the rule | onValidate | – |
| BR-05 | Changes to the `MetamodelConfiguration` require the permission `metamodel:write`, except the visibility fields of `PropertyDefinition` (`visibilityMode`, `readRoles`, `writeRoles`, `scopingConnectionType`), which require `visibility:configure` (ADR-032 §3, SoD-2) | onCreate, onUpdate, onDelete | REQ-157, REQ-161 |
| BR-06 | If `editMode=import-only` for a configuration, create, update, and delete operations on EntityTypeDefinitions, stereotypes, and ConstraintRules of this configuration MUST NOT be executed via the GUI API; only the import path (REQ-033) remains active; applies independently per scope level: a solution extension can have `gui-and-import` while the instance configuration is `import-only` | onUpdate | – |
| BR-09 | A `MetamodelConfiguration` with `scope=solution` MUST NOT contain types that are already defined in the parent object (`parentId`) (no overriding, only supplementing) | onCreate, onUpdate | – |
| BR-10 | A `MetamodelConfiguration` with `scope=solution` MUST have a valid `parentId` pointing to a `MetamodelConfiguration` with `scope=instance` of the same OEA instance | onCreate | – |
| BR-07 | An instance of a connection type (`isConnection=true`) MUST have exactly one `source` and one `target` reference; both references may point to entities of any EntityType class (including other connection instances), provided the respective `allowedSourceTypes`/`allowedTargetTypes` lists are respected | onCreate, onUpdate | – |
| BR-08 | `allowedSourceTypes`, `allowedTargetTypes`, and `allowsConnectionAsSource` may only be set if `isConnection=true`; for `isConnection=false` they are meaningless and are ignored | onCreate, onUpdate | – |
| BR-11 | `PropertyDefinition.dataType.kind=varchar` requires `maxLength` ≥ 1 | onCreate, onUpdate | – |
| BR-12 | `PropertyDefinition.dataType.kind=enum` requires `enumValues` with at least 2 unique entries | onCreate, onUpdate | – |
| BR-13 | `PropertyDefinition.name` must be unique within an EntityType (case-insensitive) | onCreate, onUpdate | – |
| BR-14 | `PropertyDefinition.category` must not be empty; whitespace-only counts as empty | onCreate, onUpdate | – |
| BR-15 | `EntityTypeDefinition.architectureLayerId`, if set, must reference an `ArchitectureLayerDefinition.id` of the same `MetamodelConfiguration` (or its parent configuration) | onCreate, onUpdate | – |
| BR-16 | `MandatoryConnectionConstraint.connectionType` must reference an `EntityTypeDefinition` with `isConnection=true` | onCreate, onUpdate | – |
| BR-17 | `MandatoryConnectionConstraint.sourceEntityType` and `targetEntityType` (if set) must be valid EntityType IDs | onCreate, onUpdate | – |
| BR-18 | `ArchitectureLayerDefinition.id` and `ArchitectureDomainDefinition.id` must be unique within the MetamodelConfiguration (shared namespace does not allow conflicts) | onCreate, onUpdate | – |
| BR-19 | `CreationStep.stepId` must be unique within the `creationSteps` list of an EntityType | onCreate, onUpdate | – |
| BR-20 | `CreationStep.propertyCategory`, if set, must correspond to an existing `PropertyDefinition.category` value of the associated EntityType | onCreate, onUpdate | – |
| BR-21 | `CreationStep.connectionType`, if set, must be a valid EntityType ID with `isConnection=true` in the same MetamodelConfiguration | onCreate, onUpdate | – |
| BR-22 | When the creation wizard is started (`creationSteps` not empty): the entity is persisted only after clicking "Finish"; a cancellation (ESC or X) creates no entity and leaves no state in the repository | onEntityCreate | – |
| BR-23 | `readRoles` and `writeRoles` reference authorization role IDs, never business roles; an authorization role referenced there cannot be deleted; every entry of `writeRoles` must be contained in `readRoles` | onCreate, onUpdate; onDelete of the role | REQ-124, ADR-032 DM-9 |
| BR-24 | `PropertyDefinition.sqlColumnName` is derived from `name` on creation (lowercase snake_case; on collision a numeric suffix is appended), is unique across all property definitions and never changes afterwards, also not when the property is renamed | onCreate, onUpdate | ADR-034 DM-6 |
| BR-25 | Every change of EntityTypes or PropertyDefinitions regenerates the affected reporting views `reporting.<metatype>`; every generated column applies the same masking expression as `reporting.property_values` | onCreate, onUpdate, onDelete | ADR-034 §3.1, REQ-160 |

## Examples

**Custom EntityType (YAML representation, §14.1)**:

```yaml
entityType: SecurityZone
id: security-zone
extends: null
description: "Logical network zone with a defined trust level"
architectureLayerId: technology-layer
properties:
  - name: trustLevel
    displayLabel: "Trust Level"
    dataType: { kind: enum, enumValues: [public, dmz, internal, restricted] }
    validationMode: mandatory
    category: "Security"
  - name: complianceScope
    displayLabel: "Compliance Scope"
    dataType: { kind: varchar, maxLength: 200 }
    validationMode: optional
    category: "Compliance"
  - name: investmentCostForecast
    displayLabel: "Investment Cost Forecast (CHF)"
    dataType: { kind: int }
    validationMode: warning
    category: "Costs"
relations:
  - name: contains
    target: TechnologyComponent
    cardinality: 0..n
```

**Stereotype (YAML representation, §14.2)**:

```yaml
stereotype: SaaSApplication
basedOn: ApplicationComponent
description: "Software-as-a-Service application without own hosting operations"
properties:
  - name: vendor
    type: string
    required: true
  - name: contractEndDate
    type: date
    required: false
  - name: dataResidency
    type: string
    required: false
```

**Connection EntityType (YAML representation, REQ-036)**:

```yaml
entityType: DataFlow
id: data-flow
isConnection: true
description: "Data flow between two components; start and target are mandatory"
allowedSourceTypes: [ApplicationComponent, ApplicationService, DataFlow]
allowedTargetTypes: [ApplicationComponent, ApplicationService, DataFlow]
properties:
  - name: protocol
    type: enum
    required: false
    enumValues: [REST, gRPC, AMQP, JDBC, SFTP]
  - name: dataClassification
    type: enum
    required: false
    enumValues: [public, internal, confidential, restricted]
```

*T relationship*: `DataFlow.allowedSourceTypes` contains `DataFlow` → a DataFlow can point to another DataFlow, which yields a T connection (e.g. a branch of a data line).

**Constraint rule (YAML representation, §14.3)**:

```yaml
name: every-interface-has-owner
appliesTo: Interface
rule: "entity.owner != null"
severity: error
message: "Every Interface must have an owner ApplicationComponent assigned"
```

**ArchitectureLayer definitions (YAML representation)**:

```yaml
architectureLayers:
  - id: business-layer
    name: "Business"
    sortOrder: 1
    color: "#F5A623"
    description: "Business processes, roles, and organizational units"
  - id: application-layer
    name: "Application"
    sortOrder: 2
    color: "#4A90D9"
    description: "Application components and services"
  - id: technology-layer
    name: "Technology"
    sortOrder: 3
    color: "#7ED321"
    description: "Infrastructure, platforms, and technical services"
```

**ArchitectureDomain definitions (YAML representation)**:

```yaml
architectureDomains:
  - id: finance
    name: "Finance"
    color: "#9B59B6"
    description: "Financial accounting, controlling, treasury"
  - id: hr
    name: "Human Resources"
    color: "#E74C3C"
  - id: logistics
    name: "Logistics"
    color: "#1ABC9C"
```

**MandatoryConnectionConstraint (YAML representation)**:

```yaml
mandatoryConnectionConstraints:
  - name: appcomp-must-run-on-tech
    sourceEntityType: application-component
    connectionType: runs-on
    targetEntityType: technology-component
    validationMode: warning
    message: "Every ApplicationComponent should have at least one 'runs-on' connection to a TechnologyComponent"
  - name: solution-must-realize-plateau
    sourceEntityType: solution
    connectionType: realizes
    targetEntityType: plateau
    validationMode: error
    message: "Every Solution must be assigned to exactly one Plateau"
```

## Delimitation from Similar Objects

- **NOT** [person](./person.md): The configuration belongs to the instance, not to a person; persons are only editors.
- **NOT** an EA model element: `ApplicationComponent`, `BusinessProcess`, etc. are instances of the types defined by the `MetamodelConfiguration`, not part of the configuration itself.
- **NOT** an ADR: Architecture decisions (which EntityType do we use for Kafka?) are ADRs; the `MetamodelConfiguration` is the technical implementation of this decision.

## Usage in Use Cases

- [UC-04: Configure Metamodel](../requirements/use-cases/UC-04-configure-metamodel.md) (creates and manages MetamodelConfiguration incl. viewpoints)

## Notes

Version 1.3.0 implements ADR-034 DM-6: raw SQL analytics expose one wide view per metatype with one column per property. Deriving column names from `name` at query time would break analysts' queries on every rename, hence a separate, immutable `sqlColumnName` (column `property_definitions.sql_column_name` in `docs/data-model.puml`). Uniqueness is global because property definitions are a shared table that can be mapped to several metatypes. Naming rules for the per-metatype view names are still open (ADR-034 follow-up).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | Business Engineer | Initial draft |
| 0.2.0 | 2026-06-25 | Business Engineer | Added `editMode` (lock mode REQ-035) and connection attributes (`isConnection`, `allowedSourceTypes`, `allowedTargetTypes`) to EntityTypeDefinition (REQ-036); added BR-06/07/08 |
| 0.3.0 | 2026-06-25 | Business Engineer | Introduced two-level scoping (REQ-037): `scope`, `architectureId`, `parentId`; added BR-09/10; `editMode` applies independently per scope level |
| 0.4.0 | 2026-06-25 | Business Engineer | `scope=architecture` → `scope=solution`; `architectureId` → `solutionId`; scope container clarified as solution (plateau principle, option 3) |
| 0.5.0 | 2026-06-26 | Business Engineer | Added viewpoints as fourth configurable category; added `viewpoints: ViewpointDefinition[]` to the root object; reference to viewpoint.md |
| 0.6.0 | 2026-06-26 | Business Engineer | Reworked PropertyDefinition: `type/required` → `dataType (kind: int/varchar/enum)` + `validationMode (mandatory/warning/optional)` + `category`; new sub-object `PropertyDataType`; `architectureLayerId` on EntityTypeDefinition; new sub-objects `ArchitectureLayerDefinition`, `ArchitectureDomainDefinition`, `MandatoryConnectionConstraint`; three new fields in the root object; added BR-11–18; updated YAML examples |
| 0.7.0 | 2026-06-26 | Business Engineer | Added `allowsConnectionAsSource: boolean` (default false) to EntityTypeDefinition (ADR-010, n-connection); extended BR-08 |
| 0.8.0 | 2026-06-26 | Business Engineer | Added `creationSteps: CreationStep[]` to EntityTypeDefinition; new sub-object `CreationStep` with stepTypes properties/domainAssignment/connectionAssignment; added BR-19–22 (REQ-066) |
| 0.9.0 | 2026-06-26 | Business Engineer | Added `arc42Collections: Arc42ChapterCollection[]` to the root object (reference to arc42.md); declared built-in EntityTypes `arc42-meta-object` and `arc42-describes` (REQ-067) |
| 1.0.0 | 2026-06-27 | Business Engineer | `arc42Collections` → `documentCollectionDefinitions`; Arc42-specific naming generalized; Arc42 becomes a template, not a system concept (reference to document-collection.md) |
| 1.1.0 | 2026-06-28 | Business Engineer | Extended `PropertyDefinition` with `visibilityMode`, `allowedRoles`, `scopingConnectionType` (UC-21, REQ-124); three visibility modes: `public` (default), `role-restricted`, `connection-scoped` (ABAC via graph traversal) |
| 1.2.0 | 2026-10-13 | Business Engineer | REQ-124 v0.2.0 / ADR-032 DM-9: `allowedRoles` split into `readRoles`/`writeRoles` referencing authorization role IDs (persisted in `property_visibility_roles`); visibility fields require `visibility:configure`; BR-05 reworded to permissions; BR-23 added. Reason: security review SEC-03 (one list for read and write, self-escalation via metamodel editing) |
| 1.3.0 | 2026-10-13 | Business Engineer | ADR-034 DM-6: `PropertyDefinition.sqlColumnName` (stable, immutable SQL column name for generated reporting views); BR-24, BR-25 added |

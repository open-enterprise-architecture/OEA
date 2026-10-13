---
identifier: catalog
name_de: Katalog
name_en: Catalog
version: 0.1.0
status: draft
maturity: initial
owner_role: Lead Enterprise Architect
aliases:
  - Architekturkatalog
  - Entity-Katalog
  - Tabellensicht
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept: concept/20-entities/12-domain-views.md
---

# Business Object: Catalog

## Definition

A `Catalog` is a named, configurable table view of entities in the architecture repository. It defines which entity type forms the primary row, which attributes appear as columns, how further entities are joined via connections, and which saved filters are preset.

## Description

While a [Viewpoint](./viewpoint.md) defines a graphical diagram view, a catalog provides a **tabular view** – comparable to a parameterized database query that can be saved, shared, and adjusted interactively.

The catalog is the enterprise architect's primary tool for producing structured overviews: a list of all applications with their interfaces, a compliance overview of all data entities with classification, or a cross-layer view that links applications to the technology components they run on.

---

### Primary Entity and Columns

Every catalog has a **primary entity** (`primaryEntityType`): it supplies the rows of the table. **Columns** are configured from its attributes: which attributes are visible, in which order they appear, and under which label.

---

### Joins via Connections

Via [connection types](./metamodel-configuration.md) (EntityTypes with `isConnection=true`), a catalog can contain **join definitions** that include further entities – analogous to SQL JOINs.

**Example**: Catalog "Applications & Interfaces"

```
Primary entity: ApplicationComponent
Join: via DataFlow (connection) → Interface (target entity)
```

Two modes are available for each join, which the **catalog visitor** can switch at runtime; the **default mode** is stored on the JoinDefinition:

| Mode | Behavior | Analogy |
|---|---|---|
| `rows` | A separate row is created for each linked target entity; the primary entity repeats → SQL INNER/LEFT JOIN | `SELECT a.*, b.* FROM a JOIN c ON … JOIN b ON …` |
| `aggregate` | The primary entity appears exactly once; all linked target entities are aggregated into a single cell (e.g. as a comma-separated list or sub-table) | `SELECT a.*, array_agg(b.*) … GROUP BY a.id` |

The **catalog-level default** (`defaultJoinMode`) applies to all JoinDefinitions that do not override it with their own default. At runtime the visitor can switch between `rows` and `aggregate` per join or globally; the choice is optionally persisted as a **SavedView**.

---

### Saved Filters

Filters can be set interactively and persisted as named **SavedFilters**. A SavedFilter is a reusable, named filter condition (e.g. "Only applications with status active", "Only systems of the business layer") that is offered to visitors as a quick selection.

---

### Saved Views

A **SavedView** bundles: visible columns, column order, active filters (references to SavedFilters), and join mode. It allows saving several preconfigured views of the same catalog (e.g. "Compact view", "Audit export view").

---

### Catalog Scope

| Scope | Visibility |
|---|---|
| `instance` | Visible to all users of the OEA instance (shared catalog) |
| `personal` | Visible only to the creator |

---

### Delimitation: Catalog vs. Viewpoint

| Aspect | Catalog | Viewpoint |
|---|---|---|
| Presentation form | Table / list | Graphical diagram |
| Configurable | Columns, joins, filters, sort | Allowed types, notation |
| Primary action | Export, filter, query data | Visualize, draw architecture |
| Notation | none | ArchiMate 3, UML, BPMN 2.0 |
| Join concept | yes (via connections) | no |

## Attributes

### Catalog (root object)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4, globally unique | Internal primary key |
| name | string | required | | max. 255 characters, unique within the instance | Readable name (e.g. "Application Overview") |
| description | string | optional | | max. 1000 characters | Purpose and target audience of the catalog |
| primaryEntityType | string | required | | valid EntityType name | Entity type that supplies the rows |
| columns | ColumnConfig[] | required | [] | at least 1 entry | Configuration of the columns from attributes of the primary entity |
| joinDefinitions | JoinDefinition[] | required | [] | | Joins via connections to further entity types |
| defaultJoinMode | enum | required | `aggregate` | `[rows, aggregate]` | Global default for all joins of the catalog; can be overridden per JoinDefinition and by visitors |
| savedFilters | SavedFilter[] | required | [] | | Named, reusable filter conditions |
| savedViews | SavedView[] | required | [] | | Named combinations of columns, filters, and join mode |
| scope | enum | required | `instance` | `[instance, personal]` | Visibility of the catalog |
| createdBy | reference | required | | target: person | Creating person |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| lastModifiedAt | datetime | required | | ISO 8601, UTC | Time of last modification |
| lastModifiedBy | reference | required | | target: person | Person who made the last modification |

### ColumnConfig

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| attributeName | string | required | | Name of the attribute from the EntityType (e.g. `status`, `name`, `owner`) |
| displayLabel | string | optional | = `attributeName` | Overrides the column header in the display |
| visible | boolean | required | true | Whether the column is visible (false = present but hidden) |
| sortOrder | integer | required | | Order in the table (0 = first column) |
| sortable | boolean | required | true | Whether the table can be sorted by this column |
| defaultSortDirection | enum | optional | null | `[asc, desc]`; null = no default sorting |

### JoinDefinition

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4 |
| name | string | required | | Readable name for this join (e.g. "Interfaces") |
| connectionType | string | required | | EntityType name with `isConnection=true` |
| joinDirection | enum | required | `outbound` | `[outbound, inbound, both]`; outbound = primary entity as source, inbound = as target |
| targetEntityType | string | required | | EntityType of the target entity after the join |
| targetColumns | ColumnConfig[] | required | [] | Attributes of the target entity that appear as columns |
| defaultJoinMode | enum | optional | = Catalog.defaultJoinMode | `[rows, aggregate]`; overrides the catalog default for this join |
| aggregateLabel | string | optional | = `name` | Label of the aggregate cell in `aggregate` mode |

### SavedFilter

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4 |
| name | string | required | | Readable name (e.g. "Only active systems") |
| description | string | optional | | |
| logicalOperator | enum | required | `and` | `[and, or]`; how the filter expressions are combined |
| expressions | FilterExpression[] | required | | At least one expression |

### FilterExpression

| Attribute | Type | Optional | Description |
|---|---|---|---|
| attributePath | string | required | Attribute name of the primary or joined entity (e.g. `status`, `dataFlow.protocol`) |
| operator | enum | required | `[eq, neq, contains, startsWith, in, notIn, isNull, isNotNull, gt, lt, gte, lte]` |
| value | any | optional | Comparison value; empty for `isNull`/`isNotNull`; an array for `in`/`notIn` |

### SavedView

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4 |
| name | string | required | | Readable name (e.g. "Audit export view") |
| description | string | optional | | |
| columnOrder | string[] | optional | | Ordered list of `attributeName` values of the visible columns; overrides the catalog ColumnConfig order |
| activeFilterIds | string[] | optional | [] | IDs of SavedFilters that are active in this view |
| joinModeOverrides | JoinModeOverride[] | optional | [] | Explicit mode per join (`rows`/`aggregate`); overrides JoinDefinition defaults |
| isDefault | boolean | required | false | Whether this view is preselected when the catalog is opened |

### JoinModeOverride (value object in SavedView)

| Attribute | Type | Description |
|---|---|---|
| joinDefinitionId | string | ID of the affected JoinDefinition |
| joinMode | enum `[rows, aggregate]` | Selected mode for this SavedView |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| createdBy | [person](./person.md) | n:1 | no | Creating person |
| referencesEntityType | EntityTypeDefinition (via name) | n:1 | no | Primary entity |
| referencesConnectionTypes | EntityTypeDefinition (isConnection=true) | n:n | yes | Connections via which joins are made |
| containedIn | [tree-node](./tree-node.md) | n:0..1 | yes | Position in the navigation tree |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `primaryEntityType` MUST be a valid EntityType name in the MetamodelConfiguration | onCreate, onUpdate | – |
| BR-02 | `JoinDefinition.connectionType` MUST be an EntityType with `isConnection=true` | onCreate, onUpdate | – |
| BR-03 | `JoinDefinition.targetEntityType` MUST correspond to one of the `allowedSourceTypes` or `allowedTargetTypes` of the ConnectionType (or be arbitrary if null) | onCreate, onUpdate | – |
| BR-04 | A `SavedFilter.expression.attributePath` MUST point to a valid attribute of the primary entity or of a joined target entity | onCreate, onUpdate | – |
| BR-05 | `SavedView.columnOrder` may only contain attribute names that are defined in the catalog's `columns` configuration | onCreate, onUpdate | – |
| BR-06 | At most one `SavedView` per catalog may have `isDefault=true` | onCreate, onUpdate | – |

## Lifecycle

Catalogs have no formal status lifecycle. They are configuration objects that can be created, edited, and deleted. Deleted catalogs are removed from the [TreeNode](./tree-node.md).

## Examples

**Catalog "Application Overview with Interfaces"**:

```
primaryEntityType: ApplicationComponent
defaultJoinMode: aggregate
columns:
  - attributeName: name,       sortOrder: 0
  - attributeName: status,     sortOrder: 1
  - attributeName: owner,      sortOrder: 2
  - attributeName: layer,      sortOrder: 3
joinDefinitions:
  - name: Interfaces
    connectionType: DataFlow
    joinDirection: outbound
    targetEntityType: Interface
    targetColumns:
      - attributeName: name,     sortOrder: 0
      - attributeName: protocol, sortOrder: 1
    defaultJoinMode: aggregate  ← shows all interfaces of an app in one cell
savedFilters:
  - name: Only active applications
    expressions:
      - attributePath: status, operator: eq, value: active
savedViews:
  - name: Compact view
    columnOrder: [name, status]
    activeFilterIds: [filter-active-applications]
    isDefault: true
  - name: Full view with interfaces (rows mode)
    joinModeOverrides:
      - joinDefinitionId: join-interfaces, joinMode: rows
```

**Result in `aggregate` mode (default)**:

| Name | Status | Interfaces |
|---|---|---|
| CRM-System | active | REST-API, SFTP-Export |
| ERP-Core | active | JDBC-Connector, REST-API |

**Result in `rows` mode (visitor switches)**:

| Name | Status | Interface | Protocol |
|---|---|---|---|
| CRM-System | active | REST-API | REST |
| CRM-System | active | SFTP-Export | SFTP |
| ERP-Core | active | JDBC-Connector | JDBC |
| ERP-Core | active | REST-API | REST |

## Delimitation

- **NOT** [Viewpoint](./viewpoint.md): A viewpoint controls the graphical diagram presentation; a catalog is a tabular data query.
- **NOT** a report: Reports (PDF, export) can be generated from catalogs but are not a BO of their own.
- **NOT** a [Solution](./solution.md): A solution describes change deltas; a catalog shows the current (or historical) state.

## Usage in Use Cases

- Future: UC for creating and configuring catalogs (primary actor: SH-03 Kurt)
- [UC-05: Describe Architecture Vision](../requirements/use-cases/UC-05-describe-architecture-vision.md) – catalogs can be used for baseline analysis (complementary to REQ-039)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; join modes (rows/aggregate), SavedFilter, SavedView, ColumnConfig, JoinDefinition |

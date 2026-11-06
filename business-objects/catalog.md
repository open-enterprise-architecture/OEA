---
identifier: catalog
name_de: Katalog
name_en: Catalog
version: 0.7.0
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
  - concept: concept/30-dynamics/12-domain-views.md
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

The primary entity type may be a connection type (US-080, REQ-065): the rows are then its connections, and columns may also show attributes of each connection's source and target (`endpoint`, BR-11). Without configured columns, such a catalog shows the source name, the name and the target name.

---

### Joins via Connections

Via [connection types](./metamodel-configuration.md) (EntityTypes with `isConnection=true`), a catalog can contain **join definitions** that include further entities – analogous to SQL JOINs.

**Example**: Catalog "Applications & Interfaces"

```text
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

An ad-hoc filter names a column of the query result by its key, an operator of the column's type (BR-12) and a value; it is part of the view state in the URL and changes nothing stored (change us-051-catalog-filters). A filter on a join column keeps only entries with a matching join result and narrows the join to the matching results. SavedFilters belong to the catalog configuration and are changed under the rule of the columns: `catalog:manage` for an instance catalog, the creator for a personal catalog; the `scope` of a SavedFilter was dropped.

---

### Saved Views

A **SavedView** bundles: visible columns, column order, active filters (references to SavedFilters), and join mode. It allows saving several preconfigured views of the same catalog (e.g. "Compact view", "Audit export view").

As implemented (change us-053-saved-views, owner decisions 2026-11-06), a SavedView is a snapshot of the view state: the column keys in display order, the filter expressions with their combination, the join mode overrides and the sort. It does not reference SavedFilters. The query applies the columns of a view (`view` parameter); the client applies its filters, modes and sort as parameters, and applies the default view only when the URL holds no view state. SavedViews are changed under the rule of the columns (BR-09).

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
| name | string | required | | 1–255 characters, stored trimmed; unique within the instance, compared case-insensitively after trimming; personal and instance catalogs share the namespace | Readable name (e.g. "Application Overview"); a mutable label – references to a catalog always use `id` |
| description | string | optional | `''` | max. 1000 characters | Purpose and target audience of the catalog |
| primaryEntityType | reference | required | | target: EntityTypeDefinition (metatype) by ID | Entity type that supplies the rows; referenced by its ID so that a rename of the type does not break the catalog, the name is shown |
| columns | ColumnConfig[] | required | [] | may be empty | Configuration of the columns from attributes of the primary entity; while empty, the display shows `name` and `status` |
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
| endpoint | enum | optional | null | `[source, target]`; null = attribute of the primary entity; otherwise of the connection's source or target (BR-11) |
| field | enum | conditional | | `[name, description, externalId, createdAt, updatedAt, entityVersion]`; a system field of every entity; exactly one of `field` and `propertyDefId` |
| propertyDefId | reference | conditional | | target: PropertyDefinition mapped to the primary EntityType (also inherited), not retired, visibility `public`; with `endpoint`, mapped to an allowed endpoint metatype of that side (BR-11); by ID, so a renamed property keeps its column |
| displayLabel | string | optional | = attribute name | 1–100 characters; overrides the column header in the display |
| visible | boolean | required | true | Whether the column is visible (false = present but hidden) |
| sortable | boolean | required | true | Whether the table can be sorted by this column |
| defaultSortDirection | enum | optional | null | `[asc, desc]`; null = no default sorting; at most one column (BR-08) |

The position in the list is the display order (it replaces `sortOrder`, change us-047-configure-columns).

### JoinDefinition

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4 |
| name | string | required | | 1–100 characters; the header above the columns of the join |
| connectionMetatypeId | UUID | required | | Metatype with `isConnection=true` of the instance metamodel (BR-02) |
| joinDirection | enum | required | | `[outbound, inbound, both]`; outbound = primary entity as source, inbound = as target, both = either, targets deduplicated |
| targetMetatypeId | UUID | required | | Metatype of the target entities, reachable through the connection metatype (BR-03); targets have exactly this metatype |
| targetColumns | ColumnConfig[] | required | | 1–20 attributes of the target metatype, at least one visible, without `defaultSortDirection` (BR-10) |
| defaultJoinMode | enum | optional | null | `[rows, aggregate]`; null = Catalog.defaultJoinMode |

A catalog has at most 10 joins, in creation order (change us-048-catalog-joins). `aggregateLabel` was dropped: the join name is the header. Only active connections and active target entities count. A join whose metatypes are gone or no longer reachable is kept but left out of the query (`available = false`).

### SavedFilter

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4 |
| name | string | required | | 1–100 characters, unique in the catalog after trimming, regardless of case |
| description | string | optional | null | At most 1000 characters |
| logicalOperator | enum | required | `and` | `[and, or]`; how the filter expressions are combined |
| expressions | FilterExpression[] | required | | 1–20 expressions |
| available | boolean | derived | | `false` if an expression names a column the query no longer returns; not stored |

A catalog has at most 50 SavedFilters, in creation order (change us-051-catalog-filters). A SavedFilter is not edited in place; it is removed and saved anew.

### FilterExpression

| Attribute | Type | Optional | Description |
|---|---|---|---|
| attributePath | string | required | Column key of the query result: `name` or another system field, the `sqlColumnName` of a property, `source.{key}`/`target.{key}` in a connection catalog, `{joinId}.{key}` for a join column (BR-04) |
| operator | enum | required | `[eq, neq, contains, startsWith, in, notIn, isNull, isNotNull, gt, lt, gte, lte]`, by the type of the column (BR-12) |
| value | string or string[] | optional | Comparison value: a number, a date `yyyy-MM-dd`, `true`/`false` or an enum value by the type; 1–100 values without commas for `in`/`notIn`; none for `isNull`/`isNotNull` |

### SavedView

| Attribute | Type | Optional | Default | Description |
|---|---|---|---|---|
| id | string | required | | UUID v4 |
| name | string | required | | 1–100 characters, unique in the catalog after trimming, regardless of case (e.g. "Audit export view") |
| description | string | optional | null | At most 1000 characters |
| columnOrder | string[] | required | | 1–100 column keys of the catalog's primary columns in display order (BR-05); columns not listed are hidden in the view |
| filters | FilterExpression[] | optional | [] | 0–20 expressions on columns of the view or of joins (BR-04, BR-12); replaces `activeFilterIds` |
| filterMode | enum | optional | `and` | `[and, or]` |
| joinModeOverrides | JoinModeOverride[] | optional | [] | Explicit mode per join (`rows`/`aggregate`); overrides JoinDefinition defaults; at most one per join |
| sort | {column, direction} | optional | null | A sortable column of the view, or of a join in `rows` mode in the view |
| isDefault | boolean | required | false | Whether this view is applied when the catalog is opened without view state in the URL (BR-06) |
| available | boolean | derived | | `false` if the view names a column, a join or a sort column the catalog no longer has; not stored |

A catalog has at most 20 SavedViews, in creation order (change us-053-saved-views).

### JoinModeOverride (value object in SavedView)

| Attribute | Type | Description |
|---|---|---|
| joinDefinitionId | string | ID of the affected JoinDefinition |
| joinMode | enum `[rows, aggregate]` | Selected mode for this SavedView |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| createdBy | [person](./person.md) | n:1 | no | Creating person |
| referencesEntityType | EntityTypeDefinition (via ID) | n:1 | no | Primary entity |
| referencesConnectionTypes | EntityTypeDefinition (isConnection=true) | n:n | yes | Connections via which joins are made |
| containedIn | [tree-node](./tree-node.md) | n:0..1 | yes | Position in the navigation tree |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `primaryEntityType` MUST reference an existing EntityType of the MetamodelConfiguration | onCreate, onUpdate | – |
| BR-02 | `JoinDefinition.connectionMetatypeId` MUST be a metatype with `isConnection=true` | onCreate, onUpdate | REQ-045 AC2 |
| BR-03 | `JoinDefinition.targetMetatypeId` MUST be reachable from the primary metatype through the connection metatype in `joinDirection`, by its allowed endpoints (with subtypes; no constraint on a side allows every metatype) | onCreate, onUpdate | REQ-045 AC3 |
| BR-04 | A `SavedFilter.expression.attributePath` MUST name a column the catalog query returns: a visible primary or endpoint column or a visible column of an available join. Removing a join removes the expressions on its columns and a SavedFilter left without expressions | onCreate, onUpdate, onJoinRemoved | REQ-047 AC3, US-052 |
| BR-05 | `SavedView.columnOrder` may only contain keys of the catalog's primary columns (the visible configured columns, else the default columns), each at most once; filters and sort of a view name its columns or join columns | onCreate, onUpdate | REQ-048 AC3 |
| BR-06 | At most one `SavedView` per catalog may have `isDefault=true`; marking a view as default clears the default of every other view | onCreate, onUpdate | REQ-048 AC2 |
| BR-07 | A non-empty `columns` list MUST contain at least one column with `visible=true`; an attribute appears at most once | onUpdate | REQ-044 AC3 |
| BR-08 | At most one column MAY carry a `defaultSortDirection`; the query uses it when no sort is requested | onUpdate | REQ-044 |
| BR-09 | The columns and joins of a catalog with `scope=instance` are changed only with `catalog:manage`, those of a personal catalog only by its creator | onUpdate | ADR-032 §8 |
| BR-10 | The target columns of a join follow BR-07 for the target metatype and carry no `defaultSortDirection`; removing a join removes the `joinModeOverrides`, filters and sort of SavedViews that refer to it | onCreate, onUpdate, onDelete | REQ-045 AC4, AC5, US-053 |
| BR-11 | A column with `endpoint` is allowed only in `columns` of a catalog whose primary metatype is a connection metatype, never in join target columns; its property MUST be mapped to an allowed endpoint metatype of that side, one of its subtypes or their base types, or to any metatype if the side has no constraint; an attribute appears at most once per endpoint | onUpdate | REQ-065, US-080 AC2 |
| BR-12 | The operator of a filter MUST fit the type of its column: text (`name`, `description`, `externalId`, properties `string`, `text`, `url`) takes `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`; enum takes `eq`, `neq`, `in`, `notIn`, `isNull`, `isNotNull`; boolean takes `eq`, `neq`, `isNull`, `isNotNull`; number (`entityVersion`, `integer`, `reference` by entity ID) and date (`createdAt`, `updatedAt` by UTC day, `date`) take `eq`, `neq`, `gt`, `lt`, `gte`, `lte`, `isNull`, `isNotNull`. Text compares ignoring case; `neq` and `notIn` also hold without a value | onQuery, onCreate | REQ-047, US-051 AC5 |

## Notes

- **Identity and label** (change us-046-create-catalog, 2026-10-19): a catalog is identified by `id`, its `name` is a unique but mutable label; a rename (UC-06 A3) goes through the ID. The same applies to the primary entity type: a metatype keeps its ID across renames (a package re-import matches by ID), so the catalog stores the ID.
- **Shared namespace**: because personal and instance catalogs share the name namespace (REQ-043), a name collision reveals that some catalog with that name exists, even a personal catalog of another person; no further information is disclosed.
- **Permissions** ([ADR-032](../adrs/ADR-032-authorization-model.md)): creating a catalog with `scope=instance` requires `catalog:manage`; with `scope=personal`, `entity:read` suffices.
- **Empty columns**: REQ-043 creates catalogs without columns; the column configuration follows in a separate step (REQ-044).
- **Default columns** (change us-049-show-catalog-data, design D2): while `columns` is empty, the catalog query (REQ-046) shows the entity `name` and, if the primary entity type maps a property named `Status` (case-insensitive) with a mapping valid today and visibility `public`, that property under its `sqlColumnName` (`status`). The rule is a name convention of the starter packages and is replaced by configured columns (US-047).
- **Configured columns** (change us-047-configure-columns): the query shows the visible configured columns in their order. A configured property that is no longer mapped, retired or `public` is left out of the query and marked `available=false`; the configuration keeps it until the next change. If no configured column can be shown, the default columns apply.
- **View state** ([ADR-044](../adrs/ADR-044-view-state-in-url.md)): sort and page of an open catalog are kept in the URL, not in the catalog; storing a view permanently is the job of `savedViews`.

## Lifecycle

Catalogs have no formal status lifecycle. They are configuration objects that can be created, edited, and deleted. Deleted catalogs are removed from the [TreeNode](./tree-node.md).

## Examples

**Catalog "Application Overview with Interfaces"**:

```yaml
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
| 0.2.0 | 2026-10-19 | Change us-046-create-catalog | Primary entity type by ID; name uniqueness case-insensitive and trimmed; empty columns allowed; notes on identity, namespace and permissions |
| 0.7.0 | 2026-11-06 | Change us-053-saved-views | SavedView as implemented: snapshot with column keys, filters, filter mode, join modes and sort instead of `activeFilterIds`; limits and `available`; BR-05, BR-06 and BR-10 sharpened |
| 0.6.0 | 2026-11-05 | Change us-051-catalog-filters | SavedFilter and FilterExpression as implemented: column keys, limits, `available`, `scope` dropped; BR-04 sharpened, BR-12 |
| 0.5.0 | 2026-11-05 | Change us-080-connection-catalogs | Connection metatype as primary entity type; ColumnConfig `endpoint`; BR-11 |
| 0.4.0 | 2026-11-05 | Change us-048-catalog-joins | JoinDefinition as implemented: metatype IDs, `aggregateLabel` dropped, limits; BR-02, BR-03 sharpened, BR-10 |
| 0.3.0 | 2026-11-05 | Change us-047-configure-columns | ColumnConfig as implemented: `field` or `propertyDefId` instead of `attributeName`, list order instead of `sortOrder`; BR-07 to BR-09; stale columns |

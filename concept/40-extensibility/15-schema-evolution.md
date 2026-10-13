## 15. Schema Evolution

An EA repository lives for years, and the schema behind it evolves. After 2 years of operation with 500 application components, the requirement arises: "We have to maintain the data classification per application, because of ISO 27001." Without an evolutionary approach, this means updating 500 entities and months of review board work.

This chapter describes how schema changes can be introduced **in a controlled, subjective way and without blanket impact**.

### 15.1 Three Classes of Properties

Not every property is equally critical. The metamodel distinguishes:

**Class 1 – Identity & Essentials**
Always mandatory, otherwise the entity is useless: name, ID, type. No exceptions, no evolution need.

**Class 2 – Architectural Essentials**
Mandatory because without them no architecture statements are possible: lifecycle, primary relations (e.g., application to capability). Changes here are breaking changes.

**Class 3 – Everything Else**
Optional or conditionally mandatory. This is where the actual schema evolution takes place. Almost all new properties land in this class.

### 15.2 Evolution Patterns

#### Pattern 1 – Optionally Added Property

The simplest pattern: the property is optional, existing entities have no value, new ones may have one. No breaking change.

```yaml
EntityType: PhysicalApplicationComponent
  properties:
    - name: dataClassification
      type: enum[public, internal, confidential, restricted]
      cardinality: 0..1
      introducedIn: "schema-v2.3"
      required: false
```

#### Pattern 2 – Scope-Based Obligation (Subjective Selection)

The central mechanism for pragmatic introduction: a new obligation applies **not globally** but to a deliberately chosen subset. This subset can be defined criteria-based or explicitly.

```yaml
constraint: data-classification-required
  appliesTo: PhysicalApplicationComponent
  scope:
    type: subjective-selection
    criteria:
      - entity.criticalityClass == "tier-1"          # automatic
      - entity.belongsTo.domain == "finance"         # automatic
      - entity.id in explicitlySelectedEntities      # manually selected
    combineWith: OR
  rule: "entity.dataClassification != null"
  severity: error
  introducedIn: "schema-v2.3"
```

The decisive point: `type: subjective-selection` with a combination of **automatic criteria** and **explicit manual selection**. Architecture teams can:

- Automatically include all tier-1 applications or all finance applications
- Manually add or exclude individual further entities
- Extend the scope step by step as soon as the initial scope is maintained

Result: A new property requirement initially affects only 30 of 500 applications – feasible in a few weeks, instead of 500 at once.

#### Pattern 3 – Conditional Obligation (Rule-Based)

Classic context-dependent, without manual selection:

```yaml
constraint: data-classification-for-production
  appliesTo: PhysicalApplicationComponent
  when: "entity.lifecycleState == 'active' AND entity.environment == 'production'"
  rule: "entity.dataClassification != null"
  severity: error
  exemptions: [legacy-before-2023]
```

Legacy entities explicitly exempted, new production applications affected immediately. This is stronger than pattern 2 (applies automatically to all matching entities) but less flexible (no manual selection).

#### Pattern 4 – Deadline-Based Transition to Obligation

For cases in which a global obligation is to be reached in the long term:

```yaml
properties:
  - name: dataClassification
    type: enum[...]
    required: true
    requiredFrom: "2027-07-01"
    gracePeriod: "2026-01-01 to 2027-06-30"
    escalation:
      warning: "2026-06-01"
      error: "2027-07-01"
```

During the grace period, the tool shows warnings and escalates to errors from the deadline. Combinable with pattern 2: grace period for tier-1, then global obligation.

### 15.3 Data Quality Instead of Mandatory Binarity

A complementary concept: instead of classifying every rule as mandatory or not mandatory, a **data quality score** is calculated per entity:

```yaml
EntityType gains meta-property:
  - dataQualityScore:
      completeness: 0-100    # share of maintained relevant properties
      accuracy: 0-100        # validity of checked values
      timeliness: 0-100      # age of the last update
      overall: 0-100         # weighted average
```

New properties lower the completeness score but are not blocking. Views such as "applications with the worst data quality" allow **targeted follow-up maintenance** without blanket enforcement. This is often more effective than hard mandatory rules.

### 15.4 Schema Versioning as a First-Class Concept

Every schema carries a semantic version and explicit migration rules between versions:

```yaml
Schema: togaf-content-metamodel
  version: 2.3.0
  predecessor: 2.2.0
  migrationRules:
    from-2.2.0:
      - addProperty:
          entity: PhysicalApplicationComponent
          property: dataClassification
          required: false
          default: null
      - renameProperty:
          entity: Interface
          from: direction
          to: flowDirection
          preserveValues: true
      - deprecateProperty:
          entity: PhysicalApplicationComponent
          property: oldTagField
          removeIn: "2.5.0"
```

On schema update, the tool performs the migrations automatically where possible and shows manual actions for all cases that cannot be solved automatically.

### 15.5 Entity-Level Schema Tracking

Every entity carries the information about which schema version it was created under and last validated against:

```yaml
Entity instance (meta-properties):
  - schemaVersionCreated: "2.1.0"
  - schemaVersionLastUpdated: "2.3.0"
  - lastValidatedAt: "2.3.0"
  - validationStatus: valid | outdated | invalid
```

This enables statements such as "this entity was created under the old schema and would need to be revalidated against the current one". A typical case in audits. Views can specifically filter "outdated entities" and trigger review workflows.

### 15.6 Tag-Based Piloting

Before an attribute becomes a property, it can be piloted as a **tag**:

```yaml
Entity gains:
  - tags: Tag[]  # generic, freely assignable

Tag:
  - key: string  # e.g. "data-classification"
  - value: string  # e.g. "confidential"
  - source: string  # who set the tag
  - addedAt: datetime
```

Workflow: tags are introduced experimentally, the data obtained is evaluated, and if the concept proves its worth, a formal property with a suitable pattern from [§15.2 (Schema Evolution)](15-schema-evolution.md) is created from it. This considerably reduces the number of breaking changes.

### 15.7 Governance and Impact Analysis

Schema changes are not only technical but organizational. The tool supports:

**Impact analysis before a schema change:**
```
"Add property dataClassification as mandatory?
 Scope 'all tier-1 applications' affects 47 entities.
 Affected owners: Finance IT (23), Sales IT (14), Corporate (10).
 Estimated maintenance effort: 12 person-days."
```

**Ownership assignment for follow-up maintenance:**
The owner is determined per entity, follow-up maintenance tasks are grouped by owner.

**Progress tracking:**
Dashboard shows how many entities already have the new property and who still has to deliver.

### 15.8 Severity Levels Instead of Binary Gating

For enforcement: not every rule violation blocks. Severity levels allow a graduated response:

- **Hint**: Recommendation, non-blocking, not visible in standard reports
- **Info**: Information, non-blocking, visible in reports
- **Warning**: Should be fixed, non-blocking, prominent in dashboards
- **Error**: Blocks further changes to the entity, must be fixed

Recommended lifecycle of a new rule: starts as a warning, is upgraded to an error after proving itself and feedback.

### 15.9 Backwards Compatibility and Deprecation

For unavoidable breaking changes:

```yaml
Schema gains:
  - compatibilityMode: strict | relaxed | migration-period
  - breakingChangesAllowed: boolean
  - deprecationPeriod: duration (default: 6 months)

Deprecated properties:
  - remain in the schema during the deprecation period
  - are displayed struck through in the UI
  - write attempts produce warnings (later errors)
  - disappear completely only after the deprecation period
```

### 15.10 Practical Principles

- **Start with few mandatory properties.** Better a lean schema with later tightening than an overloaded schema that nobody maintains.
- **New rules first as a warning, later as an error.** Gives time for feedback and follow-up maintenance.
- **Subjective scopes are the rule, not the exception.** Blanket obligations are rarely really necessary.
- **Tags for piloting, properties for proven concepts.**
- **Data quality scores as positive framing.** "Improve score" instead of "fulfill obligation".
- **Schema changes with impact analysis.** Put numbers on the table before every change: how many entities, which owners, what effort.

### 15.11 Constraints for Schema Governance Itself

Meta level: rules for the rules.

```yaml
constraint: new-required-property-needs-scope
  appliesTo: Property (meta level)
  rule: "if property.required == true AND property.introducedIn != initial,
         then property.scope must be defined (not global)"
  severity: warning
  message: "New global mandatory property without scope – really intended?"

constraint: deprecation-period-minimum
  appliesTo: Property (meta level)
  rule: "if property.deprecated == true, deprecationPeriod >= 3 months"
  severity: error
```

---

← [Extensibility](14-extensibility.md) · [🏠 Overview](../README.md) · [Example Walkthrough](../50-walkthrough/16-example-walkthrough.md) →

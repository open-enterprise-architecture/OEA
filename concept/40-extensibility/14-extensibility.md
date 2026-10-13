## 14. Extensibility

### 14.1 Custom EntityTypes

New types are added as a YAML schema:

```yaml
entityType: SecurityZone
extends: null
properties:
  - name: trustLevel
    type: enum[public, dmz, internal, restricted]
  - name: complianceScope
    type: string[]
relations:
  - name: contains
    target: TechnologyComponent
    cardinality: 0..n
```

### 14.2 Stereotypes (non-breaking extension)

Existing EntityTypes can be extended without inheritance:

```yaml
stereotype: SaaSApplication
basedOn: ApplicationComponent
properties:
  - name: vendor
    type: string
  - name: contractEndDate
    type: date
  - name: dataResidency
    type: string
```

### 14.3 Constraint Rules

Rules as declarative checks:

```yaml
constraint: every-interface-has-owner
appliesTo: Interface
rule: "entity.owner != null"
severity: error
```

---

← [Business–Technology Linking](../30-dynamics/13-business-technology-linking.md) · [🏠 Overview](../README.md) · [Schema Evolution](15-schema-evolution.md) →

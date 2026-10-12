## 14. Erweiterbarkeit

### 14.1 Custom EntityTypes

Neue Typen werden als YAML-Schema hinzugefügt:

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

### 14.2 Stereotypen (non-breaking Erweiterung)

Bestehende EntityTypes können ohne Vererbung erweitert werden:

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

### 14.3 Constraint-Regeln

Regeln als deklarative Prüfungen:

```yaml
constraint: every-interface-has-owner
appliesTo: Interface
rule: "entity.owner != null"
severity: error
```

---

← [Fach-Technik-Verlinkung](../30-dynamics/13-fach-technik-verlinkung.md) · [🏠 Übersicht](../README.md) · [Schema-Evolution](15-schema-evolution.md) →

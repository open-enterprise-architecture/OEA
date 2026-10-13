## 13. Linking the Business and Technology Levels

The requirement "technical objects on interfaces reference modeled information flows" is realized through the following chain:

```
BusinessProcess (BPMN)
  └─ Task
      └─ realizedBy → ApplicationService
                        └─ exposedBy → Interface (technical)
                                        └─ implements → InformationFlow (business)
                                                         └─ transports → InformationObject
```

**Key relations**:

- `ApplicationService.realizes(BusinessService)` – technology realizes business
- `Interface.implements(InformationFlow)` – technical interface implements business flow
- `InformationFlow.between(BusinessFunction, BusinessFunction)` – business semantics
- `Interface.flows(ApplicationComponent, ApplicationComponent)` – technical implementation

### InformationFlow: Entity or Relation?

`InformationFlow` is a conceptual borderline case. Two modeling options are possible – both supported by the meta-metamodel (see reification in [§2 (Meta-Metamodel)](../00-overview/02-meta-metamodel.md)):

**Option A – InformationFlow as an EntityType**:
```
InformationFlow "Customer change" (Entity)
  - from: BusinessFunction "Sales"
  - to: BusinessFunction "Accounting"
  - transports: InformationObject "Customer"
```
Advantages: Own URN, properties, lifecycle. Easily addressable. Good for flows with rich metadata.

**Option B – InformationFlow as a relation between BusinessFunctions**:
```
Relation "flows-to" (RelationType)
  - source: BusinessFunction "Sales"
  - target: BusinessFunction "Accounting"
  - transports: InformationObject "Customer"   # property on the relation
```
Advantages: Naturally fits the business – a flow *is* a relationship between functions. Less boilerplate.

**Implementation recommendation**: **Option B as the default**, because it represents the business semantics more precisely. The bridge to the technical implementation remains functionally equivalent because relations are reified first-class objects:

```
Relation(BF_Sales -[flows-to]-> BF_Accounting)
    ↑ implementedBy
Interface X (Entity)
    ↓ flows
ApplicationComponent A → ApplicationComponent B
```

That means: An interface (technical, entity) is `implementedBy` a reified relation (business). Binding the target of a relation to an entity (or vice versa) is exactly the use case for which reification exists.

**When Option A still makes sense**: If an InformationFlow has complex properties (multiple information objects, its own approval history, multi-party involvement), entity modeling becomes more manageable. The schema can allow both options side by side.

### Validation Constraints

- Every `Interface` in the current state must implement at least one InformationFlow, otherwise a warning ("undocumented data exchange")
- Every InformationFlow must transport an InformationObject, otherwise a warning ("empty flow")
- Reified flow relations must exist in their source and target (referential integrity)

---

← [Domain Views](12-domain-views.md) · [🏠 Overview](../README.md) · [Extensibility](../40-extensibility/14-extensibility.md) →

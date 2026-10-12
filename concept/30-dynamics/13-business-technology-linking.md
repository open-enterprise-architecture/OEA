## 13. Verlinkung Fach- und Technik-Ebene

Die Anforderung "technische Objekte auf Schnittstellen verweisen auf modellierte Informationsflüsse" wird über folgende Kette realisiert:

```
BusinessProcess (BPMN)
  └─ Task
      └─ realizedBy → ApplicationService
                        └─ exposedBy → Interface (technisch)
                                        └─ implements → InformationFlow (fachlich)
                                                         └─ transports → InformationObject
```

**Zentrale Relationen**:

- `ApplicationService.realizes(BusinessService)` – Technik realisiert Fachlichkeit
- `Interface.implements(InformationFlow)` – technische Schnittstelle implementiert fachlichen Fluss
- `InformationFlow.between(BusinessFunction, BusinessFunction)` – fachliche Semantik
- `Interface.flows(ApplicationComponent, ApplicationComponent)` – technische Umsetzung

### InformationFlow: Entity oder Relation?

`InformationFlow` ist ein konzeptioneller Grenzfall. Zwei Modellierungs-Optionen sind möglich – beide durch das Meta-Metamodell unterstützt (siehe Reifikation in [§2 (Meta-Metamodell)](../00-overview/02-meta-metamodell.md)):

**Option A – InformationFlow als EntityType**:
```
InformationFlow "Kundenänderung" (Entity)
  - from: BusinessFunction "Vertrieb"
  - to: BusinessFunction "Buchhaltung"
  - transports: InformationObject "Kunde"
```
Vorteile: Eigene URN, Properties, Lifecycle. Einfach adressierbar. Gut für Flows mit reichhaltigen Metadaten.

**Option B – InformationFlow als Relation zwischen BusinessFunctions**:
```
Relation "flows-to" (RelationType)
  - source: BusinessFunction "Vertrieb"
  - target: BusinessFunction "Buchhaltung"
  - transports: InformationObject "Kunde"   # Property an der Relation
```
Vorteile: Fachlich natürlich – ein Fluss *ist* eine Beziehung zwischen Funktionen. Weniger Boilerplate.

**Implementierungsempfehlung**: **Option B als Default**, weil es die fachliche Semantik präziser abbildet. Die Brücke zur technischen Implementierung bleibt funktional äquivalent, weil Relationen reifiziert First-Class sind:

```
Relation(BF_Vertrieb -[flows-to]-> BF_Buchhaltung)
    ↑ implementedBy
Interface X (Entity)
    ↓ flows
ApplicationComponent A → ApplicationComponent B
```

Das heißt: Ein Interface (technisch, Entity) `implementedBy` eine reifizierte Relation (fachlich). Die Zielbindung einer Relation an eine Entität (oder umgekehrt) ist genau der Anwendungsfall, für den die Reifikation existiert.

**Wann Option A dennoch sinnvoll ist**: Wenn ein InformationFlow komplexe Eigenschaften hat (mehrere Informationsobjekte, eigene Genehmigungs-Historie, Multi-Party-Beteiligung), wird die Entity-Modellierung handhabbarer. Das Schema kann beide Optionen nebeneinander erlauben.

### Validierungs-Constraints

- Jedes `Interface` im Ist-Zustand muss mindestens einen InformationFlow implementieren, sonst Warnung ("undokumentierter Datenaustausch")
- Jeder InformationFlow muss ein InformationObject transportieren, sonst Warnung ("leerer Fluss")
- Reifizierte Flow-Relationen müssen in ihrer Quelle und ihrem Ziel existieren (Referenzielle Integrität)

---

← [Domain-Konzept & Sichten](12-domain-sichten.md) · [🏠 Übersicht](../README.md) · [Erweiterbarkeit](../40-extensibility/14-erweiterbarkeit.md) →

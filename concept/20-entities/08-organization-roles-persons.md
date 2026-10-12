## 8. Organisation, Rollen und Personen

Das EA-Repository kennt drei klar getrennte Konzepte: **OrganizationUnit** (strukturelle Einheit), **Role** (fachlich-funktionale Verantwortung) und **Person** (konkrete:r Mitarbeiter:in). Die Trennung ist essentiell, weil Personalwechsel nicht Architektur-Änderungen auslösen soll.

### 8.1 OrganizationUnit

```yaml
EntityType: OrganizationUnit
  properties:
    - name, description
    - type: company | division | department | team | virtual-team | chapter | guild
    - size: number (Mitarbeitende)
    - costCenter: string
  relations:
    - parentUnit: OrganizationUnit    # Hierarchie
    - matrixReporting: OrganizationUnit[]  # für Matrix-Organisationen
    - locatedAt: Location[]
    - headedBy: Role
    - memberOfRoles: Role[]           # Organisations-Rollen
```

Die OrganizationUnit-Hierarchie ist zentral für strukturelle Analysen (Org-Chart, Kostenallokation) und kann sowohl klassische Hierarchien als auch Matrix- und Netzwerk-Organisationen abbilden.

### 8.2 Role

Rollen sind **von Personen entkoppelt** – ein Paradigma, das das Repository stabil über Personalwechsel macht.

```yaml
EntityType: Role
  properties:
    - name: string
    - description: text
    - type: functional | process | project | governance | architectural
    - responsibilities: text[]
    - authorities: text[]             # was darf die Rolle entscheiden
    - isStandardRole: boolean         # aus Referenzmodell vs. org-spezifisch
  relations:
    - belongsToUnit: OrganizationUnit
    - reportsToRole: Role
    - requiresSkills: Skill[]
    - filledBy: Person[]              # aktuell besetzt durch
    - participatesInProcesses: Process[]
    - ownsEntities: Entity[]          # generische Ownership
```

**Rollen-Typen im Detail**:

- **Functional Role**: "Buchhalter", "Vertriebsmitarbeiter" – Linien-Rollen
- **Process Role**: "Process Owner", "Incident Manager" – prozess-spezifisch
- **Project Role**: "Product Owner", "Scrum Master" – projekt-spezifisch
- **Governance Role**: "Data Owner", "Risk Owner" – verantwortungs-fokussiert
- **Architectural Role**: "Enterprise Architect", "Solution Architect" – EA-Funktion

### 8.3 Person

```yaml
EntityType: Person
  properties:
    - name: string
    - employeeId: string (optional)
    - email: string
    - active: boolean
    - startDate, endDate: date
  relations:
    - fillsRoles: Role[]              # mehrere gleichzeitig möglich
    - hasSkills: SkillLevel[]         # siehe 8.4
    - memberOf: OrganizationUnit[]
    - primaryOrgUnit: OrganizationUnit

Relation: RoleAssignment
  - role: Role
  - person: Person
  - validFrom, validTo: date
  - assignmentType: primary | secondary | deputy
  - percentage: number               # FTE-Anteil
```

**Wichtig**: Person ist optional. In vielen Organisationen ist Person-Information aus Datenschutz-Gründen (DSGVO) nicht im EA-Repository, sondern wird aus HR-Systemen referenziert. Das Metamodell unterstützt beides:

```yaml
Person erhält:
  - externalReference: ExternalRef (z.B. zu Active Directory, Workday)
  - detailLevel: full | reference-only | anonymized
```

### 8.4 Skills und Kompetenzen

```yaml
EntityType: Skill
  properties:
    - name: string
    - category: technical | functional | domain | leadership | methodology
    - taxonomy: string                # z.B. "SFIA-8" als Bezugsrahmen
    - description: text
  relations:
    - parentSkill: Skill              # Hierarchie
    - relatedSkills: Skill[]

Relation: SkillLevel
  - person: Person
  - skill: Skill
  - level: novice | advanced-beginner | competent | proficient | expert
  - certifiedBy: Certification (optional)
  - verifiedDate: date
```

Die Referenz auf **SFIA (Skills Framework for the Information Age)** als Standardtaxonomie macht die Skills international vergleichbar und auditierbar.

### 8.5 RACI als Modellierungs-Primitiv

RACI-Zuordnungen werden als explizite Relation modelliert:

```yaml
Relation: RACIAssignment
  - role: Role
  - activity: Activity | Process | Deliverable | Decision
  - responsibility: responsible | accountable | consulted | informed
  - note: text (optional)
```

**Constraint**: Pro Activity darf maximal eine Role `accountable` sein (klassische RACI-Regel). Dieser Constraint wird im Metamodell erzwungen.

### 8.6 Architecture Review Board (ARB) und Governance-Gremien

Gremien als strukturierte Gruppen von Rollen:

```yaml
EntityType: GovernanceBody
  properties:
    - name: string
    - type: ARB | steering-committee | CAB | data-council | risk-committee
    - charter: text
    - meetingFrequency: string
    - decisionAuthority: text
  relations:
    - chairedBy: Role
    - members: Role[]
    - decidesOn: EntityType[]         # was darf dieses Gremium entscheiden
    - reportsTo: GovernanceBody | OrganizationUnit
```

Das macht explizit, welches Gremium welche Entscheidungen treffen darf – und referenziert ADRs zurück auf das entscheidende Gremium.

### 8.7 Mastership mit HR-Systemen

Wie bei ITSM: OrganizationUnit und Person sind typischerweise in HR- und Identity-Systemen gemastert.

| Entität | Typischer Master | EA-Master für |
|---|---|---|
| OrganizationUnit | HR-System | EA-spezifische Ownership-Properties |
| Person | HR-System / AD | nichts (nur Referenz) |
| Role | EA-Repository | alle architektur-relevanten Properties |
| Skill (Taxonomie) | EA-Repository oder externes Framework (SFIA) | Mapping |
| SkillLevel (Person × Skill) | HR/LMS-System oder EA | je nach Org |

### 8.8 Constraints

```yaml
constraint: orgunit-has-head
  appliesTo: OrganizationUnit
  when: "entity.type in [division, department]"
  rule: "entity.headedBy != null"
  severity: warning

constraint: role-has-responsibilities
  appliesTo: Role
  rule: "entity.responsibilities.length >= 1"
  severity: warning
  message: "Rolle ohne definierte Verantwortlichkeiten ist inhaltsleer"

constraint: single-accountable-per-activity
  appliesTo: RACIAssignment
  rule: "for any activity, count(raci where responsibility='accountable') == 1"
  severity: error
  message: "Genau eine accountable Rolle pro Activity erforderlich"

constraint: person-minimum-role
  appliesTo: Person
  when: "entity.active == true"
  rule: "entity.fillsRoles.length >= 1"
  severity: warning
```

---

← [Motivation, Stakeholder & Ziele](07-motivation-stakeholder-ziele.md) · [🏠 Übersicht](../README.md) · [Prozess-Architektur](09-prozess-architektur.md) →

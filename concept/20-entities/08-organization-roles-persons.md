## 8. Organization, Roles, and Persons

The EA repository knows three clearly separated concepts: **OrganizationUnit** (structural unit), **Role** (business-functional responsibility), and **Person** (concrete employee). The separation is essential because personnel changes should not trigger architecture changes.

### 8.1 OrganizationUnit

```yaml
EntityType: OrganizationUnit
  properties:
    - name, description
    - type: company | division | department | team | virtual-team | chapter | guild
    - size: number (employees)
    - costCenter: string
  relations:
    - parentUnit: OrganizationUnit    # hierarchy
    - matrixReporting: OrganizationUnit[]  # for matrix organizations
    - locatedAt: Location[]
    - headedBy: Role
    - memberOfRoles: Role[]           # organizational roles
```

The OrganizationUnit hierarchy is central for structural analyses (org chart, cost allocation) and can represent classic hierarchies as well as matrix and network organizations.

### 8.2 Role

Roles are **decoupled from persons** – a paradigm that makes the repository stable across personnel changes.

```yaml
EntityType: Role
  properties:
    - name: string
    - description: text
    - type: functional | process | project | governance | architectural
    - responsibilities: text[]
    - authorities: text[]             # what the role may decide
    - isStandardRole: boolean         # from reference model vs. org-specific
  relations:
    - belongsToUnit: OrganizationUnit
    - reportsToRole: Role
    - requiresSkills: Skill[]
    - filledBy: Person[]              # currently filled by
    - participatesInProcesses: Process[]
    - ownsEntities: Entity[]          # generic ownership
```

**Role types in detail**:

- **Functional Role**: "Accountant", "Sales representative" – line roles
- **Process Role**: "Process Owner", "Incident Manager" – process-specific
- **Project Role**: "Product Owner", "Scrum Master" – project-specific
- **Governance Role**: "Data Owner", "Risk Owner" – responsibility-focused
- **Architectural Role**: "Enterprise Architect", "Solution Architect" – EA function

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
    - fillsRoles: Role[]              # several simultaneously possible
    - hasSkills: SkillLevel[]         # see 8.4
    - memberOf: OrganizationUnit[]
    - primaryOrgUnit: OrganizationUnit

Relation: RoleAssignment
  - role: Role
  - person: Person
  - validFrom, validTo: date
  - assignmentType: primary | secondary | deputy
  - percentage: number               # FTE share
```

**Important**: Person is optional. In many organizations, person information is not in the EA repository for data protection reasons (GDPR) but is referenced from HR systems. The metamodel supports both:

```yaml
Person gains:
  - externalReference: ExternalRef (e.g., to Active Directory, Workday)
  - detailLevel: full | reference-only | anonymized
```

### 8.4 Skills and Competencies

```yaml
EntityType: Skill
  properties:
    - name: string
    - category: technical | functional | domain | leadership | methodology
    - taxonomy: string                # e.g. "SFIA-8" as a frame of reference
    - description: text
  relations:
    - parentSkill: Skill              # hierarchy
    - relatedSkills: Skill[]

Relation: SkillLevel
  - person: Person
  - skill: Skill
  - level: novice | advanced-beginner | competent | proficient | expert
  - certifiedBy: Certification (optional)
  - verifiedDate: date
```

The reference to **SFIA (Skills Framework for the Information Age)** as a standard taxonomy makes skills internationally comparable and auditable.

### 8.5 RACI as a Modeling Primitive

RACI assignments are modeled as an explicit relation:

```yaml
Relation: RACIAssignment
  - role: Role
  - activity: Activity | Process | Deliverable | Decision
  - responsibility: responsible | accountable | consulted | informed
  - note: text (optional)
```

**Constraint**: At most one role may be `accountable` per activity (classic RACI rule). This constraint is enforced in the metamodel.

### 8.6 Architecture Review Board (ARB) and Governance Bodies

Bodies as structured groups of roles:

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
    - decidesOn: EntityType[]         # what this body may decide
    - reportsTo: GovernanceBody | OrganizationUnit
```

This makes explicit which body may make which decisions – and references ADRs back to the deciding body.

### 8.7 Mastership with HR Systems

As with ITSM: OrganizationUnit and Person are typically mastered in HR and identity systems.

| Entity | Typical master | EA master for |
|---|---|---|
| OrganizationUnit | HR system | EA-specific ownership properties |
| Person | HR system / AD | nothing (reference only) |
| Role | EA repository | all architecture-relevant properties |
| Skill (taxonomy) | EA repository or external framework (SFIA) | Mapping |
| SkillLevel (Person × Skill) | HR/LMS system or EA | depending on org |

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
  message: "A role without defined responsibilities is empty of content"

constraint: single-accountable-per-activity
  appliesTo: RACIAssignment
  rule: "for any activity, count(raci where responsibility='accountable') == 1"
  severity: error
  message: "Exactly one accountable role per activity required"

constraint: person-minimum-role
  appliesTo: Person
  when: "entity.active == true"
  rule: "entity.fillsRoles.length >= 1"
  severity: warning
```

---

← [Motivation, Stakeholders & Goals](07-motivation-stakeholders-goals.md) · [🏠 Overview](../README.md) · [Process Architecture](09-process-architecture.md) →

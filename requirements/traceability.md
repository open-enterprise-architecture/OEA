# Traceability Matrix

Complete traceability from use cases through requirements to user stories. Checked for consistency via `/trace-check` or maintained manually.

**As of**: 2026-06-28 | **Total REQs**: 134 | **Total USs**: 131 | **Total UCs**: 21

---

## 1. UC × REQ × US – Complete Trace Table

### UC-01: Login

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-001](req/REQ-001-oidc-login-session.md) | OIDC login and session handling | functional | must | [US-001](user-stories/US-001-oidc-login.md) |
| [REQ-002](req/REQ-002-api-key-authentication.md) | API key authentication | functional | must | [US-002](user-stories/US-002-api-key-auth.md) |
| [REQ-003](req/REQ-003-access-without-role-assignment.md) | Access without role assignment | functional | must | [US-003](user-stories/US-003-access-without-role.md) |
| [REQ-004](req/REQ-004-reject-unknown-offboarded-person.md) | Rejection of unknown/offboarded persons | functional | must | [US-004](user-stories/US-004-reject-unknown-person.md) |
| [REQ-005](req/REQ-005-audit-log-login.md) | Audit log login events | functional | must | [US-005](user-stories/US-005-audit-log-login.md) |
| [REQ-006](req/REQ-006-no-account-existence-disclosure.md) | No disclosure of account existence | security | must | [US-006](user-stories/US-006-generic-error-message.md) |
| [REQ-007](req/REQ-007-lifecycle-transition-invited-active.md) | Lifecycle transition invited→active | functional | must | [US-007](user-stories/US-007-lifecycle-invited-active.md) |
| [REQ-008](req/REQ-008-login-latency.md) | Login latency ≤ 500 ms (p95) | non-functional | must | [US-008](user-stories/US-008-login-latency.md) |
| [REQ-009](req/REQ-009-passkey-login.md) | Passkey login | functional | should | [US-009](user-stories/US-009-passkey-login.md) |
| [REQ-010](req/REQ-010-username-password-totp.md) | Username/password + TOTP | functional | must | [US-010](user-stories/US-010-totp-login.md) |
| [REQ-011](req/REQ-011-username-password-minimal.md) | Username/password minimal | functional | must | [US-011](user-stories/US-011-password-minimal.md) |
| [REQ-012](req/REQ-012-token-lifetime-local.md) | Token lifetime locally configurable | functional | must | [US-012](user-stories/US-012-token-lifetime-configurable.md) |

**Concept**: §21 API Architecture, §8 Organization/Roles/Persons | **ADRs**: [ADR-006](../adrs/ADR-006-auth-stack-choice.md)

---

### UC-02: System Admin Bootstrapping

**Primary Actor**: SH-06 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-013](req/REQ-013-local-bootstrapping.md) | Local bootstrapping | functional | must | [US-013](user-stories/US-013-local-bootstrapping.md) |
| [REQ-014](req/REQ-014-remote-bootstrapping.md) | Remote bootstrapping | functional | must | [US-014](user-stories/US-014-remote-bootstrapping.md) |
| [REQ-015](req/REQ-015-no-parallel-bootstrapping.md) | No parallel bootstrapping | functional | must | [US-015](user-stories/US-015-no-parallel-bootstrapping.md) |
| [REQ-016](req/REQ-016-audit-log-bootstrapping.md) | Audit log bootstrapping | functional | must | [US-016](user-stories/US-016-audit-log-bootstrapping.md) |
| [REQ-017](req/REQ-017-secure-setup-token-handover.md) | Secure setup token handover | security | must | [US-017](user-stories/US-017-secure-setup-token-handover.md) |
| [REQ-018](req/REQ-018-warning-empty-admin-claim.md) | Warning on empty admin claim | functional | must | [US-018](user-stories/US-018-warning-empty-admin-claim.md) |
| [REQ-019](req/REQ-019-disable-local-admin.md) | Deactivatability of the local admin | functional | must | [US-019](user-stories/US-019-disable-local-admin.md) |

**Concept**: §21 API Architecture | **ADRs**: [ADR-006](../adrs/ADR-006-auth-stack-choice.md)

---

### UC-03: Set Up Authentication Method

**Primary Actor**: SH-04 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-020](req/REQ-020-enforce-second-factor.md) | Enforcement of a second factor | functional | must | [US-031](user-stories/US-031-configure-2fa-enforcement.md) |
| [REQ-021](req/REQ-021-enrollment-token-validation.md) | Enrollment token validation | functional | must | [US-020](user-stories/US-020-enrollment-entry-invitation-link.md) |
| [REQ-022](req/REQ-022-totp-enrollment.md) | TOTP enrollment | functional | must | [US-021](user-stories/US-021-totp-enrollment.md) |
| [REQ-023](req/REQ-023-passkey-enrollment.md) | Passkey enrollment | functional | should | [US-022](user-stories/US-022-passkey-enrollment.md) |
| [REQ-024](req/REQ-024-initial-password-admin.md) | Initial password by admin | functional | must | [US-023](user-stories/US-023-initial-password-admin.md) |
| [REQ-025](req/REQ-025-audit-log-enrollment.md) | Audit log enrollment | functional | must | [US-024](user-stories/US-024-audit-log-enrollment.md) |
| [REQ-026](req/REQ-026-additional-method-authenticated-person.md) | Additional method for logged-in person | functional | must | [US-025](user-stories/US-025-additional-method-logged-in-person.md) |
| [REQ-027](req/REQ-027-password-generator.md) | Password generator | functional | should | [US-026](user-stories/US-026-password-generator.md) |
| [REQ-028](req/REQ-028-password-policies.md) | Password policies configurable | functional | must | [US-027](user-stories/US-027-configure-password-policies.md) |
| [REQ-029](req/REQ-029-role-based-2fa-exemption.md) | Role-based 2FA exemption | functional | should | [US-028](user-stories/US-028-role-based-2fa-exemption.md) |
| [REQ-030](req/REQ-030-multiple-totp-credentials.md) | Multiple TOTP credentials | functional | should | [US-029](user-stories/US-029-multiple-totp-credentials.md) |
| [REQ-031](req/REQ-031-password-reset-by-admin.md) | Password reset by admin | functional | must | [US-030](user-stories/US-030-password-reset-by-admin.md) |

**Concept**: §21 API Architecture, §8 Organization/Roles/Persons | **ADRs**: [ADR-006](../adrs/ADR-006-auth-stack-choice.md)

---

### UC-04: Configure Metamodel Collaboratively

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-032](req/REQ-032-entitytype-gui-configuration.md) | Configure EntityType via GUI | functional | must | [US-032](user-stories/US-032-create-entitytype.md) |
| [REQ-033](req/REQ-033-metamodel-import.md) | Metamodel import | functional | must | [US-033](user-stories/US-033-import-metamodel.md) |
| [REQ-034](req/REQ-034-audit-log-metamodel.md) | Audit log metamodel changes | functional | must | [US-034](user-stories/US-034-audit-log-metamodel.md) |
| [REQ-035](req/REQ-035-metamodel-lock-mode.md) | Metamodel lock mode (editMode) | functional | must | [US-035](user-stories/US-035-metamodel-lock-mode.md) |
| [REQ-036](req/REQ-036-connection-entitytype.md) | Connection-EntityType (isConnection) | functional | must | [US-036](user-stories/US-036-create-connection-entitytype.md) |
| [REQ-037](req/REQ-037-architecture-metamodel-extension.md) | Architecture-specific metamodel extension (scope=solution) | functional | must | [US-037](user-stories/US-037-try-out-architecture-metamodel.md) |
| [REQ-058](req/REQ-058-export-metamodel.md) | Export metamodel configuration (YAML/JSON) | functional | should | [US-078](user-stories/US-078-export-metamodel-configuration.md) |
| [REQ-059](req/REQ-059-viewpoint-import-export.md) | Import and export viewpoint | functional | should | [US-079](user-stories/US-079-export-and-import-viewpoint.md) |
| [REQ-079](req/REQ-079-multilingual-user-interface.md) | Multilingual user interface (i18n) | functional | should | [US-081](user-stories/US-081-switch-language.md) |

**Concept**: §6 Core Entity Types, §13 Integration/Import-Export, §14 Extensibility, §15 Schema Evolution | **ADRs**: –

---

### UC-05: Describe Architecture Vision of a Change Initiative

**Primary Actor**: SH-04 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-038](req/REQ-038-create-solution.md) | Create solution | functional | must | [US-038](user-stories/US-038-create-solution.md) |
| [REQ-039](req/REQ-039-landscape-baseline.md) | Display landscape as baseline | functional | must | [US-039](user-stories/US-039-show-landscape-baseline.md) |
| [REQ-040](req/REQ-040-capture-entity-deltas.md) | Capture EntityDeltas of a solution | functional | must | [US-040](user-stories/US-040-delta-new-entity.md), [US-041](user-stories/US-041-delta-modified.md), [US-042](user-stories/US-042-delta-retiring.md), [US-045](user-stories/US-045-delta-new-entity-diagram.md) |
| [REQ-041](req/REQ-041-diff-view.md) | Diff view (current vs. target state) | functional | should | [US-043](user-stories/US-043-diff-view.md) |
| [REQ-042](req/REQ-042-conflict-warning-parallel-solutions.md) | Conflict warning for parallel solutions | functional | should | [US-044](user-stories/US-044-conflict-warning.md) |
| [REQ-121](req/REQ-121-canvas-grid.md) | Canvas grid with guidelines | functional | must | [US-121](user-stories/US-121-canvas-grid.md) |
| [REQ-122](req/REQ-122-connection-routing-modes.md) | Connection routing modes (curved, straight, orthogonal) | functional | must | [US-122](user-stories/US-122-connection-routing-modes.md) |
| [REQ-123](req/REQ-123-connection-anchor-points.md) | Anchor points on connections | functional | must | [US-123](user-stories/US-123-connection-anchor-points.md) |

**Concept**: §6 Core Entity Types, §11 Temporal Model, §12 Domain Views, §16 Example Walkthrough | **ADRs**: [ADR-007](../adrs/ADR-007-canvas-library.md) (accepted), [ADR-008](../adrs/ADR-008-gui-architecture-dual-track.md) (accepted)

---

### UC-06: Create, Configure, and Use Architecture Catalog

**Primary Actor**: SH-03 (all stakeholders) | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-043](req/REQ-043-create-catalog.md) | Create catalog | functional | must | [US-046](user-stories/US-046-create-catalog.md) |
| [REQ-044](req/REQ-044-configure-columns.md) | Configure columns of a catalog | functional | must | [US-047](user-stories/US-047-configure-columns.md) |
| [REQ-045](req/REQ-045-configure-join-definition.md) | Configure join definition | functional | must | [US-048](user-stories/US-048-add-join.md) |
| [REQ-046](req/REQ-046-catalog-query.md) | Execute catalog query (live data) | functional | must | [US-049](user-stories/US-049-show-catalog-data.md) |
| [REQ-047](req/REQ-047-set-and-save-filters.md) | Set filters and save as SavedFilter | functional | must | [US-051](user-stories/US-051-ad-hoc-filter.md), [US-052](user-stories/US-052-saved-filter.md) |
| [REQ-048](req/REQ-048-saved-view.md) | Create SavedView and apply on open | functional | should | [US-053](user-stories/US-053-saved-view.md) |
| [REQ-049](req/REQ-049-join-mode-runtime.md) | Switch join mode at runtime | functional | must | [US-050](user-stories/US-050-switch-join-mode.md) |
| [REQ-050](req/REQ-050-catalog-navigation-tree.md) | Place catalog in the navigation tree | functional | should | [US-054](user-stories/US-054-catalog-navigation-tree.md) |
| [REQ-065](req/REQ-065-n-connection-catalog-join.md) | Catalog join for connection type primaries and n-connection traversal | functional | should | – |
| [REQ-066](req/REQ-066-entity-creation-workflow.md) | Configurable entity creation workflows (wizard) | functional | must | [US-069](user-stories/US-069-creation-wizard.md) |

**Concept**: §6 Core Entity Types, §12 Domain Views | **ADRs**: [ADR-010](../adrs/ADR-010-n-connection-data-lineage.md) (REQ-065)

---

### UC-08: Model and Analyze Data Flow Map (Data Lineage)

**Primary Actor**: SH-02 | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-060](req/REQ-060-create-data-object.md) | Create and manage data object (data-object) | functional | should | [US-063](user-stories/US-063-create-data-object.md) |
| [REQ-061](req/REQ-061-n-connection-carries-data.md) | n-Connection carries-data (DataFlow ↔ DataObject) | functional | should | [US-064](user-stories/US-064-carries-data-connection.md) |
| [REQ-062](req/REQ-062-lineage-graph-api.md) | Lineage-Graph-API (upstream/downstream/impact) | functional | should | [US-065](user-stories/US-065-lineage-query.md) |
| [REQ-063](req/REQ-063-n-connection-canvas-rendering.md) | n-connection canvas rendering (3-point indicator + panel) | functional | should | [US-066](user-stories/US-066-n-connection-indikator.md), [US-067](user-stories/US-067-connection-panel.md) |
| [REQ-064](req/REQ-064-gdpr-catalog-filter.md) | GDPR catalog filter (personalDataCategories) | functional | should | [US-068](user-stories/US-068-gdpr-catalog-filter.md) |
| [REQ-065](req/REQ-065-n-connection-catalog-join.md) | Catalog join for connection primaries + n-connection traversal | functional | should | – |

**Concept**: §6 Core Entity Types, §13 Business–Technology Linking, §14 Extensibility, §20 GRC/GDPR | **ADRs**: [ADR-010](../adrs/ADR-010-n-connection-data-lineage.md) (accepted)

---

## 2. UC × Concept × ADR – Overview

| UC | Concept Chapters | ADRs | NFRs |
|---|---|---|---|
| [UC-01](use-cases/UC-01-login.md) | §8 Organisation/Rollen, §21 API Architecture | [ADR-006](../adrs/ADR-006-auth-stack-choice.md) | REQ-008 (login latency), REQ-073 (availability), REQ-074 (scalability), REQ-129 (encryption), REQ-130 (WCAG) |
| [UC-02](use-cases/UC-02-system-admin-bootstrapping.md) | §21 API Architecture | [ADR-006](../adrs/ADR-006-auth-stack-choice.md) | – |
| [UC-03](use-cases/UC-03-set-up-authentication-method.md) | §8 Organisation/Rollen, §21 API Architecture | [ADR-006](../adrs/ADR-006-auth-stack-choice.md) | – |
| [UC-04](use-cases/UC-04-configure-metamodel.md) | §6 Core Entity Types, §14 Extensibility, §15 Schema Evolution | – | – |
| [UC-05](use-cases/UC-05-describe-architecture-vision.md) | §6 Core Entity Types, §11 Temporal Model, §12 Domain Views, §16 Walkthrough | [ADR-007](../adrs/ADR-007-canvas-library.md) (accepted), [ADR-008](../adrs/ADR-008-gui-architecture-dual-track.md) (accepted) | – |
| [UC-06](use-cases/UC-06-create-and-use-catalog.md) | §6 Core Entity Types, §12 Domain Views | – | REQ-130 (WCAG) |
| [UC-07](use-cases/UC-07-create-and-use-dashboard.md) | §21 Visualization Strategy (Web Portal) | – | REQ-073 (availability), REQ-074 (scalability), REQ-130 (WCAG) |
| [UC-08](use-cases/UC-08-model-data-lineage.md) | §6 Core Entity Types, §13 Business–Technology Linking, §14 Extensibility, §15 Schema Evolution, §20 GRC/GDPR | [ADR-010](../adrs/ADR-010-n-connection-data-lineage.md) (accepted) | – |
| [UC-09](use-cases/UC-09-document-solution-architecture-arc42.md) | §14 Extensibility, §18 Reporting | – | – |
| [UC-10](use-cases/UC-10-model-business-processes.md) | §9 Process Architecture, §6 Core Entity Types | – | REQ-071 (catalog latency), REQ-072 (canvas latency) |
| [UC-11](use-cases/UC-11-define-plateau-and-go-live.md) | §11 Temporal Model | [ADR-017](../adrs/ADR-017-fully-open-layers.md) | – |
| [UC-12](use-cases/UC-12-manage-viewpoints.md) | §12 Domain Views | – | – |
| [UC-13](use-cases/UC-13-manage-navigation-tree.md) | §12 Domain Views | – | – |
| [UC-14](use-cases/UC-14-view-change-history.md) | §6 Core Entity Types | [ADR-016](../adrs/ADR-016-persistence-strategy.md) | – |
| [UC-15](use-cases/UC-15-restore-entity-state.md) | §6 Core Entity Types | [ADR-016](../adrs/ADR-016-persistence-strategy.md) | – |
| [UC-16](use-cases/UC-16-partial-entity-restore.md) | §6 Core Entity Types | [ADR-016](../adrs/ADR-016-persistence-strategy.md) | – |
| [UC-17](use-cases/UC-17-manage-continuum-building-blocks.md) | §4 Enterprise Continuum & TRM | [ADR-002](../adrs/ADR-002-continuum-repository.md) | – |
| [UC-18](use-cases/UC-18-import-continuum-package.md) | §4 Enterprise Continuum & TRM | [ADR-002](../adrs/ADR-002-continuum-repository.md) | – |
| [UC-19](use-cases/UC-19-configure-trm.md) | §4 Enterprise Continuum & TRM | [ADR-002](../adrs/ADR-002-continuum-repository.md) | – |
| [UC-20](use-cases/UC-20-analyze-continuum-conformance.md) | §4 Enterprise Continuum & TRM | [ADR-002](../adrs/ADR-002-continuum-repository.md) | – |
| [UC-21](use-cases/UC-21-configure-property-visibility.md) | §6 Core Entity Types, §8 Organization/Roles | – | REQ-129 (encryption), REQ-131 (graph traversal latency) |

---

### UC-09: Document Solution Architecture According to Arc42

**Primary Actor**: SH-04 | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-067](req/REQ-067-arc42-chapter-collection.md) | Configure Arc42ChapterCollection in the metamodel | functional | should | [US-070](user-stories/US-070-configure-arc42-chapter-collection.md) |
| [REQ-068](req/REQ-068-arc42-wysiwyg-editor.md) | Arc42 WYSIWYG editor with Mermaid/PlantUML rendering | functional | should | [US-072](user-stories/US-072-arc42-mermaid-plantuml.md) |
| [REQ-069](req/REQ-069-edit-arc42-documentation.md) | Edit and display Arc42 documentation for an entity | functional | should | [US-071](user-stories/US-071-answer-arc42-question.md) |
| [REQ-070](req/REQ-070-entity-mention-autocomplete.md) | Entity mention via /@ (autocomplete + ID-stable linking) | functional | should | [US-073](user-stories/US-073-entity-mention-autocomplete.md) |
| [REQ-080](req/REQ-080-spell-checking-wysiwyg.md) | Spell checking in the WYSIWYG editor | functional | could | [US-082](user-stories/US-082-spell-checking-wysiwyg.md) |
| [REQ-132](req/REQ-132-document-item-structure.md) | DocumentItem as atomic unit of the DocumentCollection | functional | must | [US-129](user-stories/US-129-create-document-item.md) |
| [REQ-133](req/REQ-133-document-item-nesting.md) | Nesting of DocumentItems (hierarchical chapter structure) | functional | must | [US-130](user-stories/US-130-document-item-nesting.md) |
| [REQ-134](req/REQ-134-document-item-cross-reference.md) | DocumentItem cross-reference via {{ in the WYSIWYG editor | functional | must | [US-131](user-stories/US-131-document-item-cross-reference.md) |

**Concept**: §14 Extensibility, §18 Reporting | **ADRs**: –

---

### UC-10: Model Business Processes (BPMN)

**Primary Actor**: SH-08 (Anna) | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-076](req/REQ-076-bpmn-process-types-metamodel.md) | BPMN process types in the metamodel (19 EntityTypes + 9 Connections) | functional | should | [US-074](user-stories/US-074-prozessdiagramm-anlegen.md) |
| [REQ-077](req/REQ-077-role-orgunit-person-process-assignment.md) | Process assignment of roles, OrgUnit, and persons | functional | should | [US-075](user-stories/US-075-lane-orgunit-rolle-zuordnen.md), [US-076](user-stories/US-076-usertask-person-zuordnen.md) |
| [REQ-081](req/REQ-081-bpmn-dataobject-process-lineage.md) | BPMN DataObject integration and process data lineage | functional | should | [US-083](user-stories/US-083-bpmn-dataobject-enrichment.md) |

**Concept**: §9 Process Architecture, §6 Core Entity Types | **ADRs**: [ADR-007](../adrs/ADR-007-canvas-library.md) (Vue Flow)

---

### UC-11: Define Plateau and Perform Go-Live

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-084](req/REQ-084-create-plateau.md) | Create plateau | functional | must | [US-084](user-stories/US-084-create-plateau.md) |
| [REQ-085](req/REQ-085-plateau-overview.md) | Structured plateau overview | functional | must | [US-085](user-stories/US-085-plateau-overview.md) |
| [REQ-086](req/REQ-086-go-live-two-step-confirmation.md) | Go-live two-step confirmation | functional | must | [US-086](user-stories/US-086-go-live-confirmation.md) |
| [REQ-087](req/REQ-087-go-live-no-enforcement.md) | Go-live no enforcement | functional | must | [US-087](user-stories/US-087-go-live-no-enforcement.md) |
| [REQ-088](req/REQ-088-go-live-atomic-transition.md) | Go-live atomic transition | functional | must | [US-088](user-stories/US-088-go-live-atomic-transition.md) |
| [REQ-089](req/REQ-089-plateau-realized-read-only.md) | Realized plateau read-only | functional | must | [US-089](user-stories/US-089-plateau-realized-readonly.md) |

**Concept**: §11 Temporal Model | **ADRs**: [ADR-017](../adrs/ADR-017-fully-open-layers.md) (Fully Open Layers)

---

### UC-12: Manage Viewpoint

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-090](req/REQ-090-create-edit-delete-viewpoint.md) | Create, edit, delete viewpoint | functional | must | [US-090](user-stories/US-090-viewpoint-crud.md) |
| [REQ-091](req/REQ-091-viewpoint-notation-mapping.md) | Viewpoint notation mapping | functional | must | [US-091](user-stories/US-091-viewpoint-notation-mapping.md) |
| [REQ-092](req/REQ-092-system-viewpoint-read-only.md) | System viewpoints read-only | functional | must | [US-092](user-stories/US-092-system-viewpoint-readonly.md) |
| [REQ-093](req/REQ-093-viewpoint-delete-warning.md) | Viewpoint deletion warning | functional | must | [US-093](user-stories/US-093-viewpoint-delete-warning.md) |

**Concept**: §12 Domain Views | **ADRs**: –

---

### UC-13: Manage Navigation Tree

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-094](req/REQ-094-navigation-tree-folder-crud.md) | Folder CRUD | functional | must | [US-094](user-stories/US-094-navigation-tree-folder-crud.md) |
| [REQ-095](req/REQ-095-navigation-tree-item-soft-reference.md) | Item management with soft reference | functional | must | [US-095](user-stories/US-095-navigation-tree-item-soft-reference.md) |
| [REQ-096](req/REQ-096-navigation-tree-drag-drop.md) | Drag & drop with cycle protection | functional | must | [US-096](user-stories/US-096-navigation-tree-drag-drop.md) |
| [REQ-097](req/REQ-097-navigation-tree-multiple-placement.md) | Multiple placement | functional | must | [US-097](user-stories/US-097-navigation-tree-multiple-placement.md) |

**Concept**: §12 Domain Views | **ADRs**: –

---

### UC-14: View Change History

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-098](req/REQ-098-version-history-timeline.md) | Version history timeline | functional | must | [US-098](user-stories/US-098-version-history-timeline.md) |
| [REQ-099](req/REQ-099-version-history-diff-view.md) | Field-by-field diff view | functional | must | [US-099](user-stories/US-099-version-history-diff.md) |
| [REQ-100](req/REQ-100-version-history-snapshot-retrieval.md) | Complete snapshot retrieval | functional | must | [US-100](user-stories/US-100-version-history-snapshot.md) |

**Concept**: §6 Core Entity Types | **ADRs**: [ADR-016](../adrs/ADR-016-persistence-strategy.md) (Persistence Strategy)

---

### UC-15: Restore Entity State

**Primary Actor**: SH-03 | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-101](req/REQ-101-entity-full-restore.md) | Full restore | functional | should | [US-101](user-stories/US-101-entity-full-restore.md) |
| [REQ-102](req/REQ-102-restore-audit-snapshot.md) | Audit snapshot before restore | functional | should | [US-102](user-stories/US-102-restore-audit-snapshot.md) |
| [REQ-103](req/REQ-103-restore-atomic-transaction.md) | Atomic restore transaction | functional | should | [US-103](user-stories/US-103-restore-transaction.md) |

**Concept**: §6 Core Entity Types | **ADRs**: [ADR-016](../adrs/ADR-016-persistence-strategy.md) (Persistence Strategy)

---

### UC-16: Partially Restore Entity

**Primary Actor**: SH-03 | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-104](req/REQ-104-partial-restore-wizard.md) | Three-step wizard | functional | should | [US-104](user-stories/US-104-partial-restore-wizard.md) |
| [REQ-105](req/REQ-105-partial-restore-field-selection.md) | Selective field selection | functional | should | [US-105](user-stories/US-105-partial-restore-field-selection.md) |
| [REQ-106](req/REQ-106-partial-restore-restored-fields.md) | restoredFields tracking | functional | should | [US-106](user-stories/US-106-partial-restore-restored-fields.md) |

**Concept**: §6 Core Entity Types | **ADRs**: [ADR-016](../adrs/ADR-016-persistence-strategy.md) (Persistence Strategy)

---

### UC-17: Manage Reusable Architecture Building Blocks

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-107](req/REQ-107-create-refine-abb.md) | Create ABB and manage refinement hierarchy | functional | must | [US-107](user-stories/US-107-create-refine-abb.md) |
| [REQ-108](req/REQ-108-sbb-governance-status-verwalten.md) | Manage SBB governance status | functional | must | [US-108](user-stories/US-108-manage-sbb-governance.md) |
| [REQ-109](req/REQ-109-architecture-pattern-verwalten.md) | Create and manage architecture pattern | functional | must | [US-109](user-stories/US-109-create-architecture-pattern.md) |
| [REQ-110](req/REQ-110-compose-reference-architecture.md) | Compose reference architecture from building blocks | functional | must | [US-110](user-stories/US-110-compose-reference-architecture.md) |
| [REQ-111](req/REQ-111-continuum-schreibschutz.md) | Write protection for imported and built-in continuum building blocks | functional | must | [US-111](user-stories/US-111-continuum-scope-protection.md) |

**Concept**: §4 Enterprise Continuum & TRM | **ADRs**: [ADR-002](../adrs/ADR-002-continuum-repository.md) (Continuum Repository)

---

### UC-18: Import Continuum Package

**Primary Actor**: SH-03 | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-112](req/REQ-112-activate-builtin-packages.md) | Activate embedded continuum packages | functional | should | [US-112](user-stories/US-112-activate-builtin-packages.md) |
| [REQ-113](req/REQ-113-package-file-upload.md) | Import continuum package via file upload | functional | should | [US-113](user-stories/US-113-package-file-upload.md) |
| [REQ-114](req/REQ-114-atomarer-paket-import.md) | Atomic package import with conflict handling | functional | should | [US-114](user-stories/US-114-atomic-import-conflict-handling.md) |

**Concept**: §4 Enterprise Continuum & TRM | **ADRs**: [ADR-002](../adrs/ADR-002-continuum-repository.md) (Continuum Repository)

---

### UC-19: Configure Technical Reference Model

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-115](req/REQ-115-trm-kategorie-hierarchie-verwalten.md) | Manage TRM category hierarchy | functional | must | [US-115](user-stories/US-115-manage-trm-hierarchy.md) |
| [REQ-116](req/REQ-116-trm-sbb-governance-assignment.md) | SBB governance assignment per TRM category | functional | must | [US-116](user-stories/US-116-trm-sbb-governance-assignment.md) |
| [REQ-117](req/REQ-117-trm-review-date.md) | Set and filter TRM category review date automatically | functional | should | [US-117](user-stories/US-117-trm-review-date.md) |

**Concept**: §4 Enterprise Continuum & TRM | **ADRs**: [ADR-002](../adrs/ADR-002-continuum-repository.md) (Continuum Repository)

---

### UC-20: Analyze Architecture Conformance to the Continuum

**Primary Actor**: SH-03 | **Prio**: should | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-118](req/REQ-118-abb-coverage-analysis.md) | ABB coverage analysis (gap ratio) | functional | should | [US-118](user-stories/US-118-abb-coverage-analysis.md) |
| [REQ-119](req/REQ-119-standards-drift-analysis.md) | Standards drift analysis (prohibited and deviation report) | functional | should | [US-119](user-stories/US-119-standards-drift-analysis.md) |
| [REQ-120](req/REQ-120-technology-diversity-analysis.md) | Technology diversity analysis with executive summary export | functional | should | [US-120](user-stories/US-120-technology-diversity-analysis.md) |

**Concept**: §4 Enterprise Continuum & TRM | **ADRs**: [ADR-002](../adrs/ADR-002-continuum-repository.md) (Continuum Repository)

---

### UC-21: Configure Property Visibility

**Primary Actor**: SH-03 | **Prio**: must | **Status**: draft

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-124](req/REQ-124-propertydefinition-visibilitymode.md) | Extend PropertyDefinition with visibilityMode | functional | must | [US-124](user-stories/US-124-configure-property-visibilitymode.md) |
| [REQ-125](req/REQ-125-property-visibility-configuration-form.md) | Configuration form for property visibility in the metamodel editor | functional | must | [US-125](user-stories/US-125-property-visibility-configuration-form.md) |
| [REQ-126](req/REQ-126-property-visibility-enforcement-role.md) | Enforcement of role-restricted property visibility | functional | must | [US-126](user-stories/US-126-role-restricted-property-enforcement.md) |
| [REQ-127](req/REQ-127-property-visibility-enforcement-connection-scoped.md) | Enforcement of connection-scoped property visibility (ABAC) | functional | must | [US-127](user-stories/US-127-connection-scoped-property-domain-architect.md) |
| [REQ-128](req/REQ-128-property-visibility-admin-override.md) | Admin override for property visibility | functional | should | [US-128](user-stories/US-128-admin-override-property-visibility.md) |

**Concept**: §6 Core Entity Types, §8 Organization/Roles/Persons | **ADRs**: –

---

## 3. REQ Type Overview

| Type | Example REQs | Count |
|---|---|---|
| functional | REQ-001–002, REQ-004, REQ-009–011, REQ-013–014, REQ-018, … REQ-124–128, REQ-132–134 | **111** |
| non-functional | REQ-008, REQ-071–075, REQ-082–083, REQ-129–131 | **11** |
| business-rule | REQ-003, REQ-007, REQ-015, REQ-019, REQ-020 | **5** |
| compliance | REQ-005, REQ-016, REQ-025, REQ-034 | **4** |
| constraint | REQ-006, REQ-017 | **2** |
| data | REQ-012 | **1** |
| **Total** | | **134** |

*Note: `priority: could` is a priority, not a type — REQ-080 (spell checking) is `type: functional, priority: could`.*

### NFR Overview

| REQ | Title | Category | Prio | Core Target Value |
|---|---|---|---|---|
| [REQ-008](req/REQ-008-login-latency.md) | Login latency | performance | should | p95 < 300ms with 10k persons |
| [REQ-071](req/REQ-071-catalog-query-latency.md) | Catalog query latency | performance | must | p95 < 500ms with 3 joins and 10k entities |
| [REQ-072](req/REQ-072-canvas-rendering-latency.md) | Canvas rendering latency | performance | must | Time-to-interactive < 1,500ms with 150 entities |
| [REQ-073](req/REQ-073-system-availability.md) | System availability | availability | must | ≥ 99.5 % / month (excl. maintenance); RTO ≤ 30 min |
| [REQ-074](req/REQ-074-concurrent-user-scalability.md) | Concurrent users / scalability | scalability | should | 50 users without latency degradation > 2×; 500k entities |
| [REQ-075](req/REQ-075-platform-independent-deployment.md) | Platform independence and cloud operability | portability | must | Docker Compose + Helm on Linux/macOS/Win; no vendor lock-in (DB, S3, OIDC exchangeable) |
| [REQ-082](req/REQ-082-data-backup-retention.md) | Data backup and point-in-time recovery | reliability | must | RPO ≤ 24h; RTO ≤ 4h; backup retention ≥ 30 days |
| [REQ-083](req/REQ-083-audit-log-retention.md) | Audit log retention and query performance | compliance | should | Retention ≥ 2 years; p95 query 30 days ≤ 5s; append-only guaranteed |
| [REQ-129](req/REQ-129-data-encryption.md) | Data encryption at rest and in transit | security | must | TLS 1.2+; AES-256 at rest; 0 hardcoded keys; secrets scanner in CI |
| [REQ-130](req/REQ-130-wcag-accessibility.md) | Accessibility WCAG 2.1 Level AA | accessibility | should | 0 critical/serious violations (Axe); canvas Level A + alternative list view |
| [REQ-131](req/REQ-131-graph-traversal-latency.md) | Graph traversal latency (ABAC connection-scoped) | performance | must | Traversal overhead p95 ≤ 200 ms cold / ≤ 10 ms warm (cache TTL 60 s) |

---

## 4. Story Points Overview

| UC | USs | Total SP | Remark |
|---|---|---|---|
| UC-01 | US-001–012 | ~29 | incl. US-008 (performance test) |
| UC-02 | US-013–019 | ~17 | |
| UC-03 | US-020–031 | ~29 | |
| UC-04 | US-032–037 | ~18 | |
| UC-05 | US-038–045, US-121–123 | ~47 | US-045 (diagram path) = 8 SP; US-121=3, US-122=5, US-123=5 (canvas grid + routing) |
| UC-06 | US-046–054 + US-069 | **39** | Details: US-046=3, US-047=5, US-048=5, US-049=5, US-050=3, US-051=3, US-052=2, US-053=3, US-054=2; US-069 (wizard)=8 |
| UC-07 | US-055–062 | **28** | US-055=3, US-056=5, US-057=8, US-058=3, US-059=2, US-060=3, US-061=5, US-062=2 |
| UC-08 | US-063–068 | **24** | US-063=3, US-064=5, US-065=5, US-066=3, US-067=5, US-068=3 |
| UC-09 | US-070–073, US-129–131 | **35** | US-070=5, US-071=5, US-072=5, US-073=5; US-129=5, US-130=5, US-131=5 (DocumentItem) |
| UC-10 | US-074–076 | **16** | US-074=8, US-075=5, US-076=3 |
| UC-11 | US-084–089 | **21** | US-084=5, US-085=3, US-086=3, US-087=2, US-088=5, US-089=3 |
| UC-12 | US-090–093 | **15** | US-090=8, US-091=3, US-092=2, US-093=2 |
| UC-13 | US-094–097 | **18** | US-094=5, US-095=5, US-096=5, US-097=3 |
| UC-14 | US-098–100 | **13** | US-098=5, US-099=5, US-100=3 |
| UC-15 | US-101–103 | **11** | US-101=5, US-102=3, US-103=3 |
| UC-16 | US-104–106 | **16** | US-104=8, US-105=5, US-106=3 |
| UC-17 | US-107–111 | **20** | US-107=5, US-108=5, US-109=3, US-110=5, US-111=2 |
| UC-18 | US-112–114 | **13** | US-112=3, US-113=5, US-114=5 |
| UC-19 | US-115–117 | **12** | US-115=5, US-116=5, US-117=2 |
| UC-20 | US-118–120 | **13** | US-118=5, US-119=5, US-120=3 |
| UC-21 | US-124–128 | **22** | US-124=3, US-125=3, US-126=5, US-127=8, US-128=3 |
| **Total** | **131 USs** | **~472 SP** | |

---

## 5. Coverage Gaps

### REQs without US

All functional REQs have at least one associated US. ✓

NFRs (`type: non-functional`) deliberately have no user stories — they are cross-cutting requirements and are verified by verification methods (automated tests, monitoring, compliance checks), not by story decomposition:

| REQ | Category | Verification |
|---|---|---|
| REQ-008 | performance | Load test (k6/Gatling) |
| REQ-071 | performance | Load test |
| REQ-072 | performance | Lighthouse / Playwright |
| REQ-073 | availability | Uptime monitoring |
| REQ-074 | scalability | Load test |
| REQ-075 | portability | CI matrix (Docker, Helm) |
| REQ-082 | reliability | Backup restore test |
| REQ-083 | compliance | Audit log query test |
| REQ-129 | security | testssl.sh, gitleaks |
| REQ-130 | accessibility | Axe-Core CI, screen reader |
| REQ-131 | performance | Load test (graph traversal) |

### USs without REQ reference

All USs reference at least one REQ. ✓

### UCs without concept reference

All 21 UCs have at least one concept reference. ✓

### UCs without primary stakeholder

All 21 UCs have a `primary_actor`. ✓

### UC-07 REQ/US Overview

| REQ | Title | USs |
|---|---|---|
| [REQ-051](req/REQ-051-create-dashboard.md) | Create dashboard | US-055, US-062 |
| [REQ-052](req/REQ-052-configure-widget.md) | Configure widget | US-056, US-057, US-058, US-059 |
| [REQ-053](req/REQ-053-property-aggregation-datasource.md) | PropertyAggregation-DataSource | US-056, US-057 |
| [REQ-054](req/REQ-054-catalog-query-datasource.md) | CatalogQuery-DataSource | US-058 |
| [REQ-055](req/REQ-055-retrieve-dashboard-data.md) | Calculate dashboard data live | US-061 |
| [REQ-056](req/REQ-056-dashboard-access-visibility.md) | Access and visibility | US-055, US-062 |
| [REQ-057](req/REQ-057-widget-grid-layout.md) | Widget grid layout | US-060 |

### UC-10: Model Business Processes

**Primary Actor**: SH-08 | **Prio**: must | **Status**: proposed

| REQ | Title | Type | Prio | USs |
|---|---|---|---|---|
| [REQ-076](req/REQ-076-bpmn-process-types-metamodel.md) | BPMN process elements and OrganizationalUnit in the metamodel | functional | must | [US-074](user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) |
| [REQ-077](req/REQ-077-role-orgunit-person-process-assignment.md) | Assign roles, organizational units, and persons to process elements | functional | must | [US-075](user-stories/US-075-assign-lane-orgunit-and-role.md), [US-076](user-stories/US-076-assign-task-to-person.md) |

**Concept**: §9 Process Architecture, §6 Core Entity Types | **ADRs**: ADR-007 (Nested Nodes)

---

### Open Items (Known Gaps)

All known gaps have been closed. As of 2026-06-28:

| Gap | Status |
|---|---|
| ADR-001–005 (Gruppe A) | ✓ all accepted |
| ADR-007 Canvas library | ✓ accepted (Vue Flow) |
| ADR-008 GUI architecture | ✓ accepted |
| ADR-016 Persistence strategy | ✓ accepted (EntityVersion) |
| ADR-017 Fully Open Layers | ✓ accepted |
| Walking Skeleton | ✓ UC-06 identified; docs/walking-skeleton.md present |
| NFR gap (DoD ≥5) | ✓ 8 NFRs with measurable target values |
| UC-06 TreeNode (US-054 blocked) | ✓ UC-13 (navigation tree) created; US-054 unblocked |
| UC for plateau/go-live | ✓ UC-11 created + REQ-084–089 + US-084–089 |
| UC for viewpoint management | ✓ UC-12 created + REQ-090–093 + US-090–093 |
| UC for navigation tree | ✓ UC-13 created + REQ-094–097 + US-094–097 |
| UC-11–16 without REQs/USs | ✓ REQ-084–106 + US-084–106 created (2026-06-28) |
| Enterprise Continuum not modeled | ✓ 5 continuum BOs + UC-17–20 + REQ-107–120 + US-107–120 created (2026-06-28) |
| Canvas diagram requirements (grid, routing, anchor points) | ✓ REQ-121–123 + US-121–123 created (2026-06-28) |
| Property-level authorization (role.md: "follows later UC") | ✓ UC-21 + REQ-124–128 + US-124–128 created (2026-06-28) |
| NFR gaps (encryption, accessibility, graph traversal) | ✓ REQ-129–131 created (2026-06-28) |
| DocumentCollection: DocumentItem model | ✓ REQ-132–134 + US-129–131 created (2026-06-28); business-objects/document-collection.md v0.3.0 |

---

## 6. Orphaned Concept Chapters (Selection)

Concept chapters that no UC refers to yet – potential scope candidates:

| Concept Chapter | Topic | Status |
|---|---|---|
| §9 Responsibilities / RACI | Roles and responsibilities in the EA team | no UC |
| §10 Governance | Approval workflows | no UC |
| §13 Integration | Import/export, XMI, API integrations | no UC |
| §17 Example diagrams | Visualization examples | no UC (diagram UC still TBD) |
| §18 Reporting | Export formats, PDF reports | partially UC-07 (dashboard = live reporting); PDF export TBD |
| §19 Search | Full-text search across the repository | no UC |
| §22 Data protection | GDPR aspects, audit retention | no UC |

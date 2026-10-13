## 1. Positioning and Objectives

This document describes the business and conceptual foundation of an enterprise architecture repository. It does **not** define the technical persistence (the DB choice follows after requirements consolidation), but rather the structure used to model architecture information.

**Guiding Principles**

- **Standards as configuration, not as code**: TOGAF Content Metamodel, Arc42, BPMN 2.0, ArchiMate are mapped as schemas onto a generic meta-metamodel.
- **Git-friendly, pipeline-capable**: Every entity can be represented as a human-readable file that is diffable and PR-reviewable.
- **Time as a first-class citizen**: Current state, target states, and landscape maps (Bebauungspläne) are not special cases but basic concepts of the metamodel.
- **Views instead of silos**: Domains, layers, and landscape maps are filters over the repository, not separate data stores.
- **ITSM coexistence, not competition**: The EA repository complements the CMDB and service catalog with a planning and strategy view. Mastership is defined at property level, not at entity level.
- **API-first**: All functionality is accessible through a stable, versioned API. UI, renderers, integrations, additional modules – all use the same API. No privileged access paths.
- **Modular architecture**: The core is small and independent. Business extensions (BPMN, ITSM connectors, PPM connectors, renderers) are modules that dock on via the API.
- **Persistence neutrality**: The API defines query capabilities; the implementation underneath is interchangeable. Enables migration to other databases without an API breaking change.

---

[🏠 Overview](../README.md) · [Meta-Metamodel](02-meta-metamodel.md) →

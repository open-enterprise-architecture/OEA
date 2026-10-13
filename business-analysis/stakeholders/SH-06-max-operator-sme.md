# Stakeholder: Max – Operator in a Regulated SME

**ID**: SH-06
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Max is a professional-level IT operator in a regulated SME (finance, health, or critical infrastructure). He has a classical education but is automation-affine – he uses Ansible and scripts where it makes sense, but is not a DevOps engineer in the narrower sense. His infrastructure is hybrid (own servers plus individual cloud services), his deployment stack pragmatically mixed. He operates OEA for his company and is responsible for it running reliably and meeting all compliance requirements.

## Role and Responsibility

- **Current role**: IT operator / system administrator, professional level (5-10 years of experience)
- **Organization/industry**: SME in a regulated sector (finance, health, critical infrastructure, possibly affected by FINMA/BaFin/MaRisk/KRITIS/NIS2)
- **Experience level**: high in operations and security, medium-high in automation
- **Main daily tasks**:
  - Server operations (on-prem and cloud services)
  - Patch management and security updates
  - Backup discipline and restore tests
  - Monitoring, alerting, incident response
  - Compliance preparation (audit trails, access logs, encryption evidence)
  - Identity management integration (LDAP/AD/OIDC)
  - Documentation of operating procedures

## Relation to OEA

- **Usage frequency**: daily (in the background: monitoring), weekly active (maintenance, updates)
- **Main usage**: operate, monitor, secure, update
- **Technical level**: high (Linux, Git, Ansible, Docker/Podman, TLS/PKI, backup strategies)
- **Preferred interface**: CLI for everything, configuration files, health endpoints, structured logs

## Current Pain Points

- **Tools that run "only in the cloud"**: Many modern tools presuppose hyperscaler stacks. In a regulated SME this is often not possible (data protection, industry standards) or not economically sensible.
- **Proliferation of configuration**: Tools that want to configure "everything via UI" are unmaintainable. Click-through instructions for recovery after a disaster are unrealistic.
- **Vendor updates without a clear roadmap**: Sudden breaking changes, undocumented DB migrations, "we recommend a reinstall" – this is a nightmare for regulated environments.
- **Lack of audit suitability**: Tools often log poorly or not at all. Who changed what and when? In FINMA audits he must be able to answer that.
- **Identity lock-in**: Tools with their own user management are operationally unacceptable. He must not maintain users in 15 different tools in parallel.
- **Backup-hostile architectures**: When a tool distributes its data across complex schemas, consistent backup is difficult. He prefers tools that can be backed up easily.
- **Health endpoints missing or lying**: Monitoring shows "OK", but the tool is actually dead. Bad health checks are a security risk.
- **"Works on my machine" mentality of developers**: Tools that run in exactly one configuration only are problematic in heterogeneous environments.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Deployable in containerized form (Docker/Podman/Kubernetes as needed) | functional | blocking |
| Fully declarative configuration in one file | functional | blocking |
| Configuration versionable in Git (infrastructure as code) | functional | blocking |
| Audit trail suitability (DORA/MaRisk/FINMA/NIS2) | compliance | blocking |
| Identity federation (LDAP/AD/OIDC) instead of local users | security | blocking |
| Backup strategy clearly documented and consistent | reliability | blocking |
| On-premises operation possible without cloud obligation | portability | blocking |
| Structured logs (JSON) and usable health endpoints | observability | worrying |
| Clear update paths and migration scripts | maintainability | worrying |
| Sensible defaults (not every parameter has to be set) | usability | worrying |
| Encryption at rest and in transit as standard | security | blocking |
| Recoverable in <4h after total failure | reliability | worrying |
| Resource footprint documented (CPU, RAM, storage) | cost | worrying |

## Success Criteria

- [ ] First installation possible in <2h on a test server (container image + config file + start)
- [ ] Configuration entirely in one file (e.g. YAML), with schema validation at startup
- [ ] After the first configuration, no reconfiguration effort for normal updates
- [ ] Disaster recovery test successfully executable in <4h
- [ ] Audit log accessible via REST API for SIEM integration
- [ ] Identity integration with an existing OIDC/LDAP solution without custom code
- [ ] Backup strategy consistent as a single file (or DB dump + files)
- [ ] Health endpoint delivers honest status (not just "200 OK without content")
- [ ] Structured logs (JSON) usable for log aggregation
- [ ] Update to a new version without data loss, with a clear migration path
- [ ] Resource requirements under typical load documented (can plan hardware)
- [ ] Compliance reports automatically generatable (access logs, change history)

## Involved Use Cases

- [UC-01: Login](../../requirements/use-cases/UC-01-login.md) – other stakeholder (decides on available authentication mechanisms: SSO via OIDC or local authentication)
- [UC-02: System Admin Bootstrapping](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) – primary actor

## Concept References

- [§20 GRC/GDPR/ISMS Integration](../../concept/60-integrations/20-grc-gdpr-isms-integration.md) – audit trail and compliance
- [§21 API Architecture and Modularity](../../concept/70-platform/21-api-architecture.md) – deployment architecture
- [§21.8 Security, Audit Trail](../../concept/70-platform/21-api-architecture.md) – property-level AuthZ and audit trail
- [§22.11 Repository Changelog](../../concept/70-platform/22-analyzability.md) – audit reports

## Notes

Max is the **operations persona** that was systematically missing in the first five personas. Without him there is no productive use – modelers and consumers are of no use if the tool does not run reliably.

**Most demanding operator profile**: Regulated SMEs are often harder to operate than corporate groups because they are under regulatory pressure but do not have the staff of a large company. Max has to meet many obligations alone.

**Configuration philosophy as the core**: "Configure once, then leave alone" is not only a convenience requirement but a **security and compliance requirement**. Configuration drift is a known risk in regulated environments. The tool should:

- Configuration declarative in a file (YAML/TOML), schema-validated at startup
- Configuration versionable in Git (GitOps pattern)
- Detect and warn about configuration drift (desired vs. actual)
- Sensible defaults so that not every parameter has to be set explicitly
- Clear separation of "configuration" (static, declarative) and "runtime data" (dynamic, in the repository)

**Hybrid reality**: "Own servers + individual cloud services" is typical for SMEs and shapes the requirements:

- Container suitability is mandatory (for on-prem servers)
- But: no hard cloud dependency (also fully operable locally)
- Cloud services only as optional integration (e.g. S3-compatible backup)

**Compliance is not optional**: FINMA, MaRisk, BAIT, DORA, NIS2, KRITIS – different requirements depending on the industry, but all with a similar thrust:

- Complete audit trails
- Demonstrable access controls
- Backup and recovery concepts
- Incident response capability
- Encryption at rest and in transit

The tool can score here because many OSS alternatives are weak in exactly these points.

**Connection to CIO (SH-05)**: Max provides the CIO with operational confirmation that the tool is usable in a corporate group environment. When Max says "yes, I can operate this", the strategic hurdle is half cleared.

**Connection to Lukas (SH-02) and Kurt (SH-03)**: Both work in mid-sized companies/SMEs with an on-premises tendency. They depend on someone like Max – if the tool is operable for Max, it is usable for their companies.

**Anti-pattern for the tool**: Max will reject the tool if:

- it runs only in the cloud
- it enforces its own user management without SSO
- configuration is only possible via UI
- updates require undocumented DB migrations
- logs are not machine-evaluable
- no health endpoints exist

Each of these points alone is already a killer in a regulated environment.

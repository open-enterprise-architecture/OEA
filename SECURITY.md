# Security Policy

Thank you for your efforts to make OEA secure. Responsible reporting of security vulnerabilities is an important contribution to the open-source community.

## Supported Versions

| Version | Supported | Notes |
|---|---|---|
| 0.x | ✓ | Active development, before v1.0 release |
| < 0.x | ✗ | Pre-alpha, not intended for production |

Once v1.0 is released, the latest major version and the current one will generally be supported.

## Reporting Security Vulnerabilities

**Please do NOT report security vulnerabilities publicly** (not as a GitHub/Gitea issue, not in discussions, not in pull requests).

### Secure Reporting Channel

Use one of the following channels:

1. **Email** (preferred): `security@oea.org` (TBD – must be set up before go-live)
2. **PGP-encrypted**: PGP key will be published at go-live
3. **Private security advisory** in GitHub/Gitea (if enabled)

### What Your Report Should Contain

So that we can respond quickly:

- **Description of the vulnerability**: What is the problem?
- **Reproduction**: Step-by-step instructions
- **Impact**: Which data or functions are affected?
- **Severity assessment**: critical / high / medium / low (your assessment)
- **Affected version(s)**: commit hash or release tag
- **Environment**: OS, configuration, deployment variant
- **Suggested fix** (optional): If you have an idea

### What Happens After Your Report

| Timeframe | Action |
|---|---|
| Within 48 hours | Acknowledgment of receipt to you |
| Within 5 business days | First technical assessment |
| Within 30 days | Remediation plan and expected publication period |
| Before public disclosure | Coordination with you on disclosure timing |

We follow the **coordinated disclosure** model: vulnerabilities are made public only after they are fixed, in coordination with the reporter.

## Security Guarantees (Status and Roadmap)

OEA is developed with security as a design constraint:

- **Property-level authorization** as a mandatory feature
- **Audit trail** append-only, stored separately
- **Encryption at rest and in transit** as standard
- **OIDC/LDAP** for AuthN, never local accounts only
- **Structured logs** without PII leaks
- **Secure defaults**: TLS on, AuthN required, audit log active

For details see [`concept/70-platform/21-api-architecture.md`](concept/70-platform/21-api-architecture.md) §21.8 and [`concept/60-integrations/20-grc-gdpr-isms-integration.md`](concept/60-integrations/20-grc-gdpr-isms-integration.md).

## Known Security Aspects (Pre-Production)

Before v1.0, OEA is not yet ready for production use. The following aspects are deliberately still in progress:

- TBD: SBOM generation (Software Bill of Materials)
- TBD: Signed container images (Cosign)
- TBD: Reproducible builds
- TBD: Full penetration test before v1.0

If you want to use OEA in production, check the current status and contact us for an assessment of your use cases.

## Acknowledgment

Persons who responsibly report security vulnerabilities will be named in the release changelog on request. We do not offer a bug bounty program, but we appreciate every contribution to the security of OEA.

## Frequently Asked Questions

**Why is GPG encryption not mandatory?**
We want to keep the barrier to reporting low. PGP is available but not required.

**What if the vulnerability is being actively exploited?**
If you see signs of active exploitation, put this in the subject: "URGENT: ..." – we will respond faster.

**What if I discover the vulnerability in a third-party tool?**
OEA uses various dependencies. If the vulnerability is in a third-party tool, report it to the respective maintainer and inform us in parallel so that we can incorporate patches if needed.

**Who sees my report?**
Initially only the security owners (see [MAINTAINERS.md](MAINTAINERS.md)). For complex vulnerabilities, additional maintainers may be brought in, always under a confidentiality agreement.

## Thank You

Security reports are a valuable form of contribution. We appreciate every contribution.

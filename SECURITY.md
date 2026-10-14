# Security Policy

Thank you for your efforts to make OEA secure. Responsible reporting of security vulnerabilities is an important contribution to the open-source community.

## Supported Versions

| Version | Supported | Notes |
|---|---|---|
| 0.x | ✓ | Active development, before the v1.0 release |

OEA is pre-alpha: no production release exists yet. Once v1.0 is released, the latest minor version of the current major version receives security fixes.

## Reporting Security Vulnerabilities

**Please do NOT report security vulnerabilities publicly** – not as a GitHub issue, not in discussions, not in pull requests.

### Reporting Channel

Report vulnerabilities privately via GitHub:

1. Open the repository [open-enterprise-architecture/OEA](https://github.com/open-enterprise-architecture/OEA)
2. Go to **Security → Report a vulnerability** ([direct link](https://github.com/open-enterprise-architecture/OEA/security/advisories/new))
3. Describe the vulnerability as listed below

The report is visible only to the maintainers and to you. If you cannot use GitHub, contact the lead maintainer listed in [MAINTAINERS.md](MAINTAINERS.md) by e-mail and ask for a private channel – do not include vulnerability details in the first e-mail.

### What Your Report Should Contain

- **Description**: what is the problem?
- **Reproduction**: step-by-step instructions
- **Impact**: which data or functions are affected?
- **Severity assessment**: critical / high / medium / low (your assessment)
- **Affected version(s)**: commit hash or release tag
- **Environment**: OS, configuration, deployment variant
- **Suggested fix** (optional)

### What Happens After Your Report

| Timeframe | Action |
|---|---|
| Within 5 business days | Acknowledgment of receipt |
| Within 10 business days | First technical assessment |
| Within 30 days | Remediation plan and expected publication date |
| Before public disclosure | Coordination with you on disclosure timing |

OEA is maintained by a single maintainer; the timeframes are targets, not guarantees. We follow **coordinated disclosure**: vulnerabilities are published as a GitHub security advisory only after a fix is available, in coordination with the reporter.

## Security Baseline (Requirements Status)

No code exists yet. The security baseline is defined in the requirements and architecture decisions and is implemented together with the features (see the [security review](security-reviews/requirements-baseline-2026-10.md)):

- **Authentication**: OIDC with PKCE against an external identity provider (Entra ID, Authentik); optional local accounts with passkey or password plus TOTP; MFA mandatory for privileged accounts (REQ-166, REQ-171)
- **Authorization**: deny-by-default RBAC with a permission catalog, object-level checks and property-level visibility on every read channel ([ADR-032](adrs/ADR-032-authorization-model.md))
- **No anonymous access**: every access requires authentication (REQ-163)
- **Audit trail**: append-only for the application and administrators, hash chain with checkpoints, retention floor of one year ([ADR-033](adrs/ADR-033-audit-trail-hardening.md))
- **Sessions**: server-side revocation, idle and absolute timeouts (REQ-164, REQ-165)
- **Desktop client**: hardened Electron baseline, integrity-protected updates ([ADR-035](adrs/ADR-035-electron-security-baseline.md))
- **Secure defaults**: no authentication stub, no default secrets, startup guards ([ADR-036](adrs/ADR-036-dev-test-identity-provider.md))
- **Encryption**: TLS in transit; secrets encrypted at rest with an external key (REQ-129, REQ-188)

## Known Gaps (Pre-Production)

- OS code signing of the desktop client (Windows Authenticode, Apple notarization) is deferred until Enterprise license revenue covers the certificate costs; releases are integrity-protected by checksums and build provenance attestations instead (REQ-182)
- SBOM generation, dependency scanning and container image signing are required (REQ-191) but not yet implemented
- No penetration test has been performed; one is planned before v1.0

Do not use OEA in production before v1.0.

## Acknowledgment

Reporters are credited in the security advisory and the release notes on request. There is no bug bounty program.

## Frequently Asked Questions

**What if the vulnerability is being actively exploited?**
Say so at the beginning of the report title ("URGENT: …").

**What if the vulnerability is in a third-party dependency?**
Report it to the respective maintainer and inform us in parallel so that we can update the dependency.

**Who sees my report?**
Only the maintainers listed in [MAINTAINERS.md](MAINTAINERS.md). Additional people are involved only if necessary and under confidentiality.

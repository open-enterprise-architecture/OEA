# ADR-006: Auth Stack Choice (Identity Provider Integrations)

**Status**: accepted
**Date**: 2026-06-24
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer (UC-01 Login, REQ-001–REQ-011)
**Informed**: –

## Context and Problem

UC-01 (Login) and concept §21.8 specify that OEA must support OAuth 2.0/OIDC as standard authentication and optionally local authentication (passkey, username/password with TOTP). What was previously open is **against which concrete identity providers OEA provides tested, maintained integrations** – plain "OIDC standard" alone does not fully solve this problem, because claim mapping, discovery behavior, group/role claims, and admin tooling differ between providers. UC-01 named this as an open question ("Which concrete identity provider/auth stack is used for the Walking Skeleton?").

Additionally, it is unclear how the **initial system admin access** works before any person/role data or IdP connection exists in a freshly installed instance (bootstrapping problem).

## Decision Drivers

- **Enterprise connectivity**: Larger organizations (see SH-05, CIO corporate group) usually already operate Microsoft Entra ID as their central identity provider; frictionless integration is a prerequisite for adoption in this segment.
- **Self-hosting without vendor lock-in** (see SH-03 Kurt, SH-06 Max): Organizations without a Microsoft tenant need a fully self-hostable, open-source IdP option.
- **OSS credibility**: The chosen mandatory integrations should themselves be either market-dominant (Entra ID) or open-source-native (Authentik), not proprietary niche solutions.
- **Bootstrapping**: A fresh instance must be administrable without prior configuration of an external IdP.
- **Limit effort**: Not every conceivable OIDC implementation can be maintained/tested; the mandatory set must be small and concrete.
- **Standards conformance instead of provider lock-in**: The implementation should follow open RFCs/specifications (OIDC Core, RFC 6749, RFC 8414, RFC 7662), so that further enterprise IdPs (Okta, Ping Identity, Keycloak, etc.) are compatible without additional effort, even if only two providers are explicitly tested.

## Considered Options

### Option 1: Generic OIDC only, no concrete provider obligation

Any OIDC-compliant provider is supported, without OEA testing against a concrete provider or maintaining a reference configuration.

- **Pro**: minimal implementation effort, maximum theoretical flexibility
- **Con**: leaves the open question from UC-01 unresolved; practice shows that "generic OIDC" without a concrete reference integration leads to integration problems (claim mapping, group claims, discovery quirks); no tested guarantee for users

### Option 2: Microsoft Entra ID only

- **Pro**: directly covers the corporate group/enterprise case (SH-05); widely used standard in corporate IT
- **Con**: effectively excludes self-hosters without a Microsoft tenant (SH-03, SH-06) or forces them into third-party workarounds; contradicts the SME/OSS focus of the project

### Option 3: Authentik only

- **Pro**: fully open-source, self-hostable, fits the SME self-hosting profile (SH-03, SH-06)
- **Con**: generally not the already established IdP in corporate group IT (SH-05); would require a parallel identity infrastructure there – unrealistic for adoption

### Option 4: Entra ID and Authentik as mandatory integrations, local auth optional

OEA MUST provide and maintain tested OIDC integrations against Microsoft Entra ID and against Authentik. Additionally, local authentication (passkey, username/password with optional TOTP, see REQ-009–REQ-011) MAY be activated in parallel. Other OIDC providers may also work (standards conformance), but are not part of the maintained reference integrations.

- **Pro**: covers both corporate group enterprise (Entra ID) and self-hosting OSS (Authentik) without having to maintain arbitrarily many providers; both are market-relevant/OSS-credible; keeps local auth as fallback without any external IdP
- **Con**: two integrations to maintain instead of one; Authentik is a deliberate choice compared to Entra ID/Okta/Keycloak that not every self-hoster already uses

### Option 5: Entra ID, Authentik, and Keycloak as mandatory integrations

- **Pro**: Keycloak additionally covered as the most widespread OSS alternative
- **Con**: a third mandatory integration increases maintenance effort without clearly additional stakeholder benefit over Authentik; not requested

## Decision

We choose **Option 4**: Microsoft Entra ID and Authentik are **mandatory integrations** – OEA must provide tested, documented OIDC configurations for both. Local authentication (passkey, username/password with optional TOTP per REQ-009–REQ-011) is **optional** and can be activated in parallel to the IdP integrations, especially for instances without any external IdP.

The initial **system admin access** of an instance can likewise optionally be realized **locally** (built-in admin account, independent of person/role data in the repository) or **remotely** (via Entra ID or Authentik, e.g. via group/role claim). Both variants must be supported, because the bootstrapping problem (access before any IdP configuration) forces a local option, while organizations with established IdP governance may want to disable local admin access after initial configuration for security reasons.

**Implementation principle – RFC conformance instead of provider specifics**: The integration is implemented as a generic OIDC client against the relevant standards – OpenID Connect Core 1.0, OAuth 2.0 (RFC 6749), OAuth 2.0 Authorization Server Metadata / OIDC Discovery (RFC 8414) for automatic endpoint discovery, and optionally token introspection (RFC 7662, see REQ-001 AC3) for real-time revocation checks. Entra ID and Authentik are the two **tested reference integrations** (mandatory), but not the only technically supported providers: any further RFC-/standards-compliant enterprise IdP – e.g. **Okta**, **Ping Identity**, **ForgeRock**, **Google Workspace**, **AWS IAM Identity Center**, **OneLogin**, or in the OSS space **Keycloak** and **Zitadel** – should also work through pure standards conformance, without OEA maintaining dedicated provider code or guaranteeing compatibility for it.

## Consequences

### Positive Consequences

- The open question from UC-01 ("which identity provider/auth stack for the Walking Skeleton") is resolved: the Walking Skeleton fully implements at least one of the two mandatory integrations.
- Both corporate group (SH-05) and SME/self-hosting stakeholders (SH-03, SH-06) are served with a concrete, tested option.
- Local authentication remains as a fallback; OEA is never necessarily dependent on an external IdP.

### Negative Consequences / Trade-offs

- Two IdP integrations must be permanently maintained, tested, and updated on breaking changes of the respective providers – higher maintenance effort than with only one mandatory integration.
- Other widespread providers (e.g. Keycloak, Okta, Auth0) are not part of the maintained reference integrations; they may work via standard OIDC, but without guarantee/tests from OEA's side. This may be perceived as a gap by users with a different existing IdP.
- The optional local system admin access is an additional authentication path that must be secured independently (see follow-up decisions) – otherwise a bypass around the RBAC structure from §8.2/§21.8 arises.

### Follow-up Decisions

- ~~**Bootstrapping use case** for the initial system admin access is yet to be specified~~ → done: [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- Concrete claim mapping conventions for Entra ID (e.g. `groups` claim) and Authentik (e.g. Authentik groups/properties) to OEA roles are to be defined in a technical specification (solution design, not ADR level).
- Decision whether the local system admin access must be deactivatable after initial configuration of a mandatory IdP (security recommendation) – possibly its own requirement.

## References

**Concept Chapters**:
- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)
- [§8.2 Role](../concept/20-entities/08-organization-roles-persons.md)

**Related ADRs**:
- none

**Use Cases / Requirements**:
- [UC-01: Login](../requirements/use-cases/UC-01-login.md)
- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [REQ-001: OIDC-Based Login and Session Creation](../requirements/req/REQ-001-oidc-login-session.md)
- [REQ-009: Passkey Login](../requirements/req/REQ-009-passkey-login.md)
- [REQ-010: Username/Password Login with TOTP](../requirements/req/REQ-010-username-password-totp.md)
- [REQ-011: Username/Password Login without Second Factor](../requirements/req/REQ-011-username-password-minimal.md)
- [REQ-012: Configurable Token Lifetime for Local Authentication](../requirements/req/REQ-012-token-lifetime-local.md)

## Notes

Entra ID and Authentik were specified as the mandatory set by the repository owner, not derived from a purely technical evaluation. The rationale (corporate group vs. self-hosting coverage) was reconstructed retrospectively in order to document the decision in ADR format consistently with pros/cons.

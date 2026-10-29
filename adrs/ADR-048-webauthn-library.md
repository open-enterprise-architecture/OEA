# ADR-048: WebAuthn Library for Passkey Enrollment and Login

**Status**: proposed
**Date**: 2026-10-29
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer
**Informed**: all contributors

## Context and Problem

The merged sprint S5 plans passkeys for persons as its fourth change: registering a passkey ([US-022](../requirements/user-stories/US-022-passkey-enrollment.md), [REQ-023](../requirements/req/REQ-023-passkey-enrollment.md)) and logging in with it, also without entering a user name (discoverable credential, [US-009](../requirements/user-stories/US-009-passkey-login.md), [REQ-009](../requirements/req/REQ-009-passkey-login.md)). [ADR-006](./ADR-006-auth-stack-choice.md) allows local authentication with passkeys next to the IdP integrations.

The server side of WebAuthn has to verify registration and authentication responses. This means:

- parsing CBOR attestation objects and COSE keys;
- checking challenge, origin, RP ID hash, user-presence and user-verification flags, and the signature counter;
- optionally verifying attestation statements.

Getting one of these steps wrong lets an attacker log in. OEA should not write this code itself, so a library is needed: a new distributed dependency under [ADR-043](./ADR-043-third-party-license-policy.md). It has to fit the backend stack of [ADR-012](./ADR-012-backend-stack.md):

- Spring Boot 4.1 and Spring Security 7, used for OIDC only;
- Jackson 3 (`tools.jackson`) as the only JSON library;
- OEA's own sessions, audit and throttling (ADR-032, `io.oea.app.login`);
- endpoints that are defined first in `api/openapi.yaml` ([ADR-013](./ADR-013-api-style.md)).

The client side runs in the web portal (browsers) and in the Electron client (Chromium, [ADR-035](./ADR-035-electron-security-baseline.md)). It calls `navigator.credentials.create()` and `get()` and has to convert between the JSON of the API and the binary structures of the browser API.

## Decision Drivers

- **Security:** a maintained, conformance-tested implementation of the WebAuthn Level 2/3 verification steps, including discoverable credentials and user verification.
- **Fit with the stack:**
  - Jackson 3, no second Jackson line on the classpath;
  - no framework that brings its own login endpoints, user model or session handling;
  - the OEA contract, audit, throttling and sessions stay in charge.
- **License** allowed by ADR-043 (permissive preferred), and an active project with recent releases.
- **Small dependency footprint** in the backend jar and the frontend bundle.
- **Testability:** registration and authentication can be exercised in integration tests without a hardware authenticator (software authenticator or recorded responses).

## Considered Options

### Option 1: WebAuthn4J (`com.webauthn4j:webauthn4j-core`) used directly

A framework-independent Java library (Apache-2.0). Version 0.31.11.RELEASE is current. It depends on Jackson 3 (`tools.jackson.core:jackson-databind`, `tools.jackson.dataformat:jackson-dataformat-cbor`) and SLF4J. It is the library behind Spring Security's passkey support.

- **Pro:**
  - Jackson 3 matches the stack.
  - Few transitive dependencies (the CBOR dataformat of the existing Jackson line).
  - Permissive license.
  - Actively released, and conformance-tested through its use in Spring Security.
  - OEA keeps its own endpoints, persistence (`local_credentials`, type `passkey`), sessions, audit and throttling: the library only verifies.
  - Includes a test module with a software authenticator.
- **Con:**
  - OEA writes the glue itself: storing challenges and credentials, building the creation and request options.
  - Releases are still `0.x`; minor versions may change the API.

### Option 2: Spring Security WebAuthn (`spring-security-webauthn`)

The passkey support of Spring Security 7, managed by the Spring Boot BOM. It is built on WebAuthn4J and contributes filters and endpoints (`/webauthn/register/options`, `/login/webauthn`, …), a `UserCredentialRepository` and a `PublicKeyCredentialUserEntityRepository`.

- **Pro:**
  - Least code for a standard Spring Security login.
  - Version managed by Spring Boot.
  - Follows the Spring Security release line.
- **Con:**
  - Brings its own endpoints and login flow outside `api/openapi.yaml` (ADR-013).
  - Authenticates into a Spring Security `Authentication` instead of OEA sessions.
  - Its login bypasses the OEA audit events (`auth.login`, `auth.login-failed`), the throttling of REQ-168 and the second-factor rules of ADR-042.
  - Adapting it means overriding most of its parts and still carrying its filter chain. OEA uses Spring Security only for OIDC and request authorization, not for local logins.

### Option 3: Yubico `java-webauthn-server` (`com.yubico:webauthn-server-core`)

A mature, framework-independent library (BSD-2-Clause). Version 2.10.1 is current, by a FIDO alliance member.

- **Pro:**
  - Stable `2.x` API.
  - Thorough documentation, and conformance-tested.
  - OEA keeps its own endpoints as in option 1.
- **Con:**
  - Depends on Jackson 2 (`com.fasterxml.jackson.core:jackson-databind`). That puts a second Jackson line on the classpath next to Jackson 3, with two sets of security updates.
  - It also pulls in Guava, a separate CBOR library (`com.upokecenter:cbor`) and Apache HttpClient 5, which OEA does not use otherwise.

### Frontend: browser API with `@simplewebauthn/browser`

For the client, `@simplewebauthn/browser` (MIT, no dependencies, version 14.0.0) is compared with calling the browser API directly using `PublicKeyCredential.parseCreationOptionsFromJSON()` and `toJSON()`.

- **The JSON helpers of the browser API** need no dependency, but they are not available in every browser the web portal supports.
- **`@simplewebauthn/browser`** covers the Base64URL conversion and browser differences, and maps the WebAuthn errors to stable error codes, with a small, dependency-free footprint.

## Decision

We choose **Option 1: WebAuthn4J used directly**, and in the frontend **`@simplewebauthn/browser`**.

**Rationale:**

- WebAuthn4J is the only option that fits the existing Jackson 3 line without a second JSON stack, so the backend jar gains only the CBOR dataformat and the library.
- It verifies and leaves the protocol of the OEA API, the sessions, the audit and the throttling where ADR-013, ADR-032 and REQ-168 put them. Option 2 would have to be bent around exactly these points.
- Option 3 is as suitable functionally, but its dependency footprint (Jackson 2, Guava, HttpClient) contradicts the drivers.
- In the client, the library avoids hand-written binary conversion and browser-specific branches at a size of a few kilobytes.

## Consequences

### Positive Consequences

- The passkey endpoints are designed in `api/openapi.yaml` like every other operation, audited with the existing events, and throttled like the password login.
- Credentials are stored as `local_credentials` of type `passkey`, as the business object [local-credential](../business-objects/local-credential.md) already plans, next to password and TOTP.
- A single JSON library in the backend; the WebAuthn4J test module allows integration tests without hardware.

### Negative Consequences / Trade-offs

- **Glue code:** OEA writes and tests the ceremony itself: issuing and storing challenges (single use, short validity), building creation and request options, mapping verification errors to the generic login failure of REQ-006.
- **`0.x` releases:** a minor upgrade of WebAuthn4J may need code changes. Dependabot updates are reviewed against its release notes, and the integration tests guard the verification.
- **Attestation:** verifying attestation statements against the FIDO Metadata Service is not part of S5. Passkeys are accepted with attestation `none`, which is common for consumer passkeys. Whether an instance may require attested authenticators is a later decision.
- **Licenses and inventory:** `THIRD-PARTY-LICENSES.md` gains WebAuthn4J, the Jackson CBOR dataformat (Apache-2.0) and `@simplewebauthn/browser` (MIT). All are allowed by ADR-043 and are regenerated with the change.

## References

**Related ADRs**: [ADR-006](./ADR-006-auth-stack-choice.md) (local authentication next to the IdPs), [ADR-012](./ADR-012-backend-stack.md) (backend stack), [ADR-013](./ADR-013-api-style.md) (spec-first contract), [ADR-032](./ADR-032-authorization-model.md) (sessions, audit), [ADR-035](./ADR-035-electron-security-baseline.md) (Electron), [ADR-042](./ADR-042-setup-phase-without-second-factor.md) (second factor), [ADR-043](./ADR-043-third-party-license-policy.md) (license policy)
**Requirements**: [REQ-009](../requirements/req/REQ-009-passkey-login.md), [REQ-023](../requirements/req/REQ-023-passkey-enrollment.md), [US-009](../requirements/user-stories/US-009-passkey-login.md), [US-022](../requirements/user-stories/US-022-passkey-enrollment.md)
**Concept**: [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)
**Sources**: WebAuthn4J on Maven Central (`com.webauthn4j:webauthn4j-core` 0.31.11.RELEASE, Apache-2.0, Jackson 3); Yubico `java-webauthn-server` 2.10.1 (BSD-2-Clause, Jackson 2); Spring Security reference, [Passkeys](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html); npm `@simplewebauthn/browser` 14.0.0 (MIT)

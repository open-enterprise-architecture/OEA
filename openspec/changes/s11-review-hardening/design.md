# Design

## Context

### Client address

- **Where the address comes from:** every component takes the client address from `HttpServletRequest.getRemoteAddr()`. These are:
  - `IdentityController.java:129,266`: local, TOTP, passkey and OIDC logins;
  - `SetupController.java:93`: the setup token;
  - `AuditWriter.java:154`: the audit source address.
- **Throttle:** `LoginThrottle` locks the key `address:<address>` after `ADDRESS_FAILURES = 100` failures within 10 minutes.
- **Origin check:** `CsrfOriginFilter.originAllowed` compares `Origin` with `getScheme()://getServerName():getServerPort()`.
- **Deployment:** the operator setup binds the backend to `127.0.0.1` and requires a TLS reverse proxy (`compose.yaml:63`, `manual/setup/installation.md:58`). No forwarded-header handling is configured.
- **Consequence:** behind the proxy, every request comes from the proxy. With Docker's port publishing, that peer is usually the gateway of the Compose network (`172.16.0.0/12`). In addition, `getScheme()` is `http`.
- **Spring Boot 4.1 support:** with `server.forward-headers-strategy=native`, Tomcat's `RemoteIpValve` is installed. It takes the client address, scheme, host and port from `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-Port`, but only when the peer matches `server.tomcat.remoteip.internal-proxies`. The default of that property is loopback, link-local, `10/8`, `100.64/10`, `172.16/12`, `192.168/16`, `fc00::/7` and `fe80::/10`.

### Security schemes

- `api/openapi.yaml:82-85` declares `bearerAuth`, `cookieAuth` and `oauth2ClientCredentials` as global alternatives. The token endpoint uses `clientSecretBasic`.
- `OeaSessionFilter` authenticates only the session cookie, and `/oauth2/token` is `x-oea-status: planned`.
- `OperationCoverageCheck` already stops the start of an instance whose operations and planned markers disagree (s2b-ci-and-contract D1).
- `scripts/check_openapi_authorization.py` checks `x-oea-status` of operations.

### Packages

`LayerRules` checks only `api`, `app` and `core`, with `consideringOnlyDependenciesInLayers()`. Imports between the top-level packages (measured on `main` @ `1e8ebe46`):

| From → To | Imports |
|---|---|
| `api → app` | 112 |
| `api → config.security` | 16 |
| `api → config.oidc` | 1 |
| `api → config.web` | 1 |
| `api → audit` | 1 |
| `app → core` | 124 |
| `app → audit` | 82 |
| `app → config.oidc` | 10 |
| `app → config.secrets` | 8 |
| `app → config.webauthn` | 6 |
| `app → config.startup` | 2 |
| `audit → config.secrets` | 1 |
| `audit → config.startup` | 1 |
| `config.security → app` | 9 |
| `config.web → app` | 2 |
| `config.database → app` | 1 |
| `config.web → audit` | 4 |

Package cycles:

- **`app.access ↔ app.login`:** `access` uses `login.PersonPasswordProperties`; `login` uses `access.PasswordPolicies` and `access.SecondFactorPolicies`.
- **`app.access ↔ app.metamodel`:** `access.PersonEntityLinks` uses `metamodel.PropertyAccess`; `metamodel` uses `access.InvalidAccessRequestException`.
- **`api ↔ api.entity`:** `ApiExceptionHandler` uses `EntityBatchController.validationError`; `api.entity` uses `api.ContractViolationResponse`.
- **`config.migration ↔ config.startup`:** `MigrateCommand` uses `startup.ConfigurationGuards`; `DatabaseStartupChecks` uses `migration.SchemaHistoryValidator`.

## Goals / Non-Goals

**Goals:**

- The client address and the request origin are correct behind a trusted proxy and cannot be spoofed from anywhere else (AA-01).
- The contract states which security schemes the release accepts, and the instance checks that this holds (API-1, first part).
- Every package of the backend belongs to a layer, the layer rules cover every layer, and no package cycle remains (ARCH-1, ARCH-2).

**Non-Goals:**

- Service accounts and bearer authentication (#264).
- A rule for SQL in `app` (#258, #259).
- Splitting classes (#260).
- Any behavior change beyond AA-01.

## Architecture Constraints

- [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md): the change amends the layer model (D6). On the owner's decision, the amendment is part of this proposal, so the ADR is updated before the code.
- [ADR-013](../../../adrs/ADR-013-api-style.md): contract changes go into `api/openapi.yaml` first. The security-scheme markers use the same `x-oea-status` extension as the operations.
- [ADR-036 §8](../../../adrs/ADR-036-dev-test-identity-provider.md): the backend runs only behind a TLS reverse proxy. This change makes that setup work as intended.
- [ADR-032](../../../adrs/ADR-032-authorization-model.md): authorization stays deny-by-default and generated from the contract. A credential of a planned scheme authenticates nothing.

## Decisions

### D1 – Forwarded headers through Tomcat's `RemoteIpValve`

`application.yml` sets `server.forward-headers-strategy: native`. Boot then installs the `RemoteIpValve` with:

- `X-Forwarded-For` as the client-address header;
- `X-Forwarded-Proto` as the scheme header;
- `X-Forwarded-Host` and `X-Forwarded-Port`.

The valve walks `X-Forwarded-For` from right to left and skips trusted proxies. The first untrusted address becomes the client address. A request whose peer is not a trusted proxy keeps its connection values, and its forwarded headers are ignored.

No component code changes: `getRemoteAddr()`, `getScheme()`, `getServerName()` and `getServerPort()` already return the forwarded values. This covers the throttle, the audit and the origin check alike. `X-Forwarded-Proto: https` without a port header sets the port to 443, so the origin `https://host` matches.

The implementation confirms these defaults of Boot 4.1.2 in a test (task 1.3).

*Alternative:* `framework` (Spring's `ForwardedHeaderFilter`). It honors the headers of every peer and has no list of trusted peers, so any client could choose its address. Rejected.

### D2 – Trusted proxies: default private networks, replaceable with `OEA_TRUSTED_PROXIES`

- **Default:** Tomcat's list, i.e. loopback and the private networks (owner decision 2026-11-13). It covers the Compose gateway without any configuration, and the backend port is bound to `127.0.0.1` anyway.
- **Override:** the operator replaces the list with `OEA_TRUSTED_PROXIES`, a comma-separated CIDR list. It is bound as `oea.web.trusted-proxies` in `application.yml`.
- **Applying the list:** a `WebServerFactoryCustomizer` in `config.web` applies it to the valve's internal proxies.
- **Blank value:** a blank value keeps the default. The reason is that `compose.yaml` passes the variable as `${OEA_TRUSTED_PROXIES:-}`, and Spring would turn an empty value into an empty list that trusts no proxy.
- **Invalid value:** a value that is not a valid CIDR list stops the start with a `StartupGuardException` naming the variable.
- **Optional variable:** `OEA_TRUSTED_PROXIES` is not a secret, so `compose.yaml` may leave it empty.

*Alternative:* trust only loopback by default. Then the correction would have no effect behind Compose until the operator enters the gateway address. Rejected by the owner.

### D3 – Tests over a real HTTP connection

`MockMvc` bypasses Tomcat and the valve, so the throttle, audit and origin tests run as `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a real HTTP client. The pattern is `RequestSizeLimitTests`.

The test peer is `127.0.0.1`, which is trusted by default. For the "untrusted peer" scenarios, a second test context sets `oea.web.trusted-proxies=192.0.2.0/24`.

The throttle boundary uses the existing limit of 100 failures:

- 99 failures from `203.0.113.10`: the next login with the correct password from that address succeeds.
- 100 failures: the next login is answered with 401 and `Retry-After`. A login from `203.0.113.20` still succeeds.

### D4 – Planned security schemes

**Contract:**

- `bearerAuth`, `oauth2ClientCredentials` and `clientSecretBasic` get `x-oea-status: planned`, as an extension of the Security Scheme Object.
- The info text (`openapi.yaml:36-40`) says that the session cookie is the only scheme the release accepts, and that the others follow with service accounts and the Electron client.
- The global `security` list stays unchanged. It describes the target, like the planned operations.

**Startup check:**

- A new `SecuritySchemeCoverageCheck` in `config.security`, next to `OperationCoverageCheck`, compares the unmarked schemes of the contract with the constant `OeaSessionFilter.ACCEPTED_SCHEMES = Set.of("cookieAuth")`.
- On a mismatch the instance does not start, and the exception names the scheme.
- The contract is read through `ContractConfiguration`, the same way as the authorization catalog.

**CI check:** `check_openapi_authorization.py` accepts `x-oea-status` on a security scheme only with the value `planned`.

**Request with only a bearer token:** a request carrying only `Authorization: Bearer …` is already unauthenticated today (401). A test pins this behavior.

### D5 – Resolving the package cycles

| Cycle | Resolution |
|---|---|
| `app.access ↔ app.login` | `PersonPasswordProperties` moves from `app.login` to `app.access`, next to `PasswordPolicies`, which owns the password rules. `login → access` remains |
| `app.access ↔ app.metamodel` | `InvalidAccessRequestException` moves to a new package `app.shared` for types used by several features. `app.shared` imports nothing from `app`, and `access → metamodel` remains |
| `api ↔ api.entity` | `EntityBatchController.validationError` moves into `io.oea.api`, as a static method of a new class `BatchValidationErrors`. `api.entity → api` remains |
| `config.migration ↔ config.startup` | `ConfigurationGuards` moves to `infrastructure` (D6), so `MigrateCommand` no longer imports `config.startup` |

The class names and the public behavior stay the same. Only package declarations and imports change, and Spring finds the beans by scanning `io.oea`.

### D6 – Layer model with `infrastructure` and `audit` (ADR-028 amendment)

| Layer | Packages | May be accessed by |
|---|---|---|
| API | `io.oea.api..` | – |
| Config | `io.oea.config..` | API (web security types such as `OeaAuthentication` and the cookies) |
| Application | `io.oea.app..` | API, Config |
| Audit | `io.oea.audit..` | API, Config, Application |
| Infrastructure | `io.oea.infrastructure..` | API, Config, Application, Audit |
| Core | `io.oea.core..` | Application |

What moves to `io.oea.infrastructure` (technical adapters without any domain knowledge):

- `oidc`: all classes of `config.oidc`;
- `webauthn`: all classes of `config.webauthn`;
- `secrets`: `SecretCipher`, `KeyRing`, `KekProperties` and `SecretsConfiguration` from `config.secrets`. `ReencryptCommand` stays in `config.secrets`, because it is a command-line entry point;
- `guards`: `ConfigurationGuards` and `LoopbackAddresses` from `config.startup`, plus the types they need.

`Infrastructure` may access only itself. The rule uses `consideringOnlyDependenciesInAnyPackage("io.oea..")` and `ensureAllClassesAreContainedInArchitecture()`, so a new top-level package is reported instead of being ignored. Two more layers make every class part of the architecture:

- the root package `io.oea` (`OeaBackendApplication`), which may access every layer;
- the empty `io.oea.integration`, as an optional layer.

A second rule, `slices().matching("io.oea.(**)").should().beFreeOfCycles()`, forbids cycles between all packages, root packages included. Both rules get fixtures and tests showing that they detect a violation, like `layerRulesDetectWrongDependencies`.

ADR-028 receives an amendment section "Amendment 2026-11-13: infrastructure and audit layers" with this table, the cycle rule and a reference to the review. The ADR stays `accepted`. The complete module ADR (ARCH-3, #258) remains separate.

*Alternative:* move the web security types (`OeaAuthentication`, the cookies) from `config` to `api`. That would shift about 20 classes and gain little, because `api → config` is not a cycle. Deferred to #258.

## Data Model

No change. No Flyway migration.

## API

Delta to `api/openapi.yaml`:

- `components.securitySchemes.bearerAuth`, `.oauth2ClientCredentials` and `.clientSecretBasic` get `x-oea-status: planned`.
- `info.description`, section "Clients authenticate with": a sentence on the implementation status.

No change to operations, schemas or the generated interfaces.

## Risks / Trade-offs

- **[An operator runs the backend on a private network directly reachable by clients]** → any client in that network could set `X-Forwarded-For`. *Mitigation:* the manual names `OEA_TRUSTED_PROXIES` for exactly this case, and the default Compose setup binds the port to `127.0.0.1`.
- **[The proxy passes a client-supplied `X-Forwarded-For` through]** → spoofing is still limited: the valve takes the right-most untrusted address, i.e. the one the proxy appended. *Mitigation:* the manual tells operators to make the proxy overwrite or append the header.
- **[Moving the packages breaks imports in tests and in other branches]** → the change is mechanical, and the full test suite checks it. *Mitigation:* the implementation PR contains no other changes.
- **[The origin check behaves differently once the valve sets the scheme]** → it now sees `https` behind the proxy, which is the intended behavior. *Mitigation:* the existing CSRF tests keep running unchanged, and the new tests cover both origins.

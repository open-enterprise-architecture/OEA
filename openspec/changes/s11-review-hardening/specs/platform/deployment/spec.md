## ADDED Requirements

### Requirement: Client address behind a trusted reverse proxy

The backend SHALL take the client address, scheme, host and port of a request from the `X-Forwarded-*` headers only when the direct peer is a trusted proxy, and from the connection otherwise. The throttling per source address, the audit source address and the origin check SHALL use this client address and origin.

Trace: REQ-168, REQ-175, REQ-187, ADR-036 §8; AA-01 of the application security audit 2026-11

#### Scenario: Two clients behind the trusted proxy are throttled separately

- **WHEN** 100 failed local logins arrive within 10 minutes through a trusted proxy with `X-Forwarded-For: 203.0.113.10`, and then a login with the correct password arrives through the same proxy with `X-Forwarded-For: 203.0.113.20`
- **THEN** further logins for `203.0.113.10` are answered with 401 and `Retry-After`, and the login for `203.0.113.20` succeeds

#### Scenario: Throttling just below the limit

- **WHEN** 99 failed local logins for different login names arrive within 10 minutes through a trusted proxy with `X-Forwarded-For: 203.0.113.10`
- **THEN** the next login with the correct password from `203.0.113.10` succeeds

#### Scenario: Forwarded header from an untrusted peer

- **WHEN** a request whose direct peer is not a trusted proxy carries `X-Forwarded-For: 203.0.113.30`
- **THEN** the header is ignored, and the throttling and the audit use the address of the direct peer

#### Scenario: Audit source address

- **WHEN** a failed login arrives through a trusted proxy with `X-Forwarded-For: 203.0.113.40`
- **THEN** the audit event `auth.login-failed` records the source address `203.0.113.40`

#### Scenario: Origin behind a TLS proxy

- **WHEN** a cookie-authenticated `POST` with a valid CSRF token and `Origin: https://oea.example.org` arrives through a trusted proxy that sends `X-Forwarded-Proto: https` and `X-Forwarded-Host: oea.example.org`
- **THEN** the origin is accepted

#### Scenario: Foreign origin behind a TLS proxy

- **WHEN** the same request carries `Origin: https://evil.example.org`
- **THEN** it is rejected with 403 and has no effect

### Requirement: Trusted proxies configurable

By default, the backend SHALL trust loopback and the private networks as proxies; the operator SHALL be able to replace this list with `OEA_TRUSTED_PROXIES`.

Trace: REQ-168, ADR-036 §8; AA-01 of the application security audit 2026-11; owner decision 2026-11-13

#### Scenario: Default trusts the Compose gateway

- **WHEN** a request with `X-Forwarded-For: 203.0.113.50` arrives from a peer in `172.16.0.0/12` and `OEA_TRUSTED_PROXIES` is not set
- **THEN** the client address is `203.0.113.50`

#### Scenario: Operator narrows the trusted proxies

- **WHEN** the operator sets `OEA_TRUSTED_PROXIES` to an address range that does not contain the direct peer, and a request from that peer carries `X-Forwarded-For`
- **THEN** the header is ignored

### Requirement: Proxy requirements documented

The installation guide of the user manual SHALL state which headers the reverse proxy sends and overwrites, which proxies are trusted by default, and how the operator narrows them.

Trace: REQ-196, ADR-045; AA-01 of the application security audit 2026-11

#### Scenario: Installation guide names the headers

- **WHEN** the installation guide is read in English or German
- **THEN** it names `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-Port`, the default trusted networks and the variable `OEA_TRUSTED_PROXIES`

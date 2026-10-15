# Spec Delta

## Purpose

Defines browser sessions of OEA: how the session cookie is protected, when a session ends, how state-changing requests are protected against cross-site requests, and how a principal reads its own session.

## ADDED Requirements

### Requirement: Protected session cookie

A successful login SHALL create a server-side session and send it to the browser only as a cookie with the `__Host-` prefix and the attributes `HttpOnly`, `Secure`, `Path=/` and `SameSite=Strict`; no session or token SHALL be readable by scripts or stored in browser storage.

Trace: REQ-165 AC3, REQ-187 AC4

#### Scenario: Cookie after login

- **WHEN** the system admin logs in through the web frontend
- **THEN** the response sets the cookie `__Host-OEA_SESSION` with `HttpOnly`, `Secure`, `Path=/` and `SameSite=Strict`, and the response body contains no session identifier or token

### Requirement: Session timeouts

A session SHALL end after the idle timeout (default 30 minutes, configurable 5 minutes to 8 hours) or the absolute lifetime (default 12 hours, configurable 1 hour to 7 days), whichever comes first.

Trace: REQ-165 AC1

#### Scenario: Idle or expired session

- **WHEN** a request arrives with a session that has been idle longer than the idle timeout or is older than the absolute lifetime
- **THEN** it is rejected with 401 and the session is ended

### Requirement: Logout

Logout SHALL end the session server-side; the cookie SHALL be cleared and the session SHALL never be accepted again.

Trace: REQ-164 AC1, REQ-175

#### Scenario: Request after logout

- **WHEN** the system admin logs out and a request is sent with the previous session cookie
- **THEN** the request is rejected with 401, and an audit event `auth.logout` exists

### Requirement: Protection of state-changing requests

Every state-changing request authenticated by the session cookie SHALL carry a valid CSRF token bound to the session and SHALL come from an allowed origin; responses SHALL carry the security headers of REQ-187.

Trace: REQ-187 AC1, REQ-187 AC3

#### Scenario: Missing CSRF token

- **WHEN** a cookie-authenticated `POST` arrives without a valid CSRF token or with a foreign `Origin`
- **THEN** it is rejected with 403 and has no effect

#### Scenario: Security headers

- **WHEN** any API response is inspected
- **THEN** it carries `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, a content security policy with `frame-ancestors 'none'`, `Strict-Transport-Security` and, for authenticated responses, `Cache-Control: no-store`

### Requirement: Current principal

An authenticated principal SHALL be able to read its own principal type, display name, whether its session was confirmed with a second factor, and its effective permissions.

Trace: REQ-003, ADR-032 §7

#### Scenario: Reading the own session

- **WHEN** the logged-in system admin requests the current principal
- **THEN** the response names the system admin account, states whether the session is second-factor-verified and lists the permissions of `system-admin`

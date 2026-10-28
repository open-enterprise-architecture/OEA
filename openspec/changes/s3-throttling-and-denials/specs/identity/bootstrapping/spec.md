# Spec Delta

## ADDED Requirements

### Requirement: Throttling of the setup token

A rejected setup attempt with a missing, wrong or expired setup token SHALL count as a failed attempt of its source address, together with the failed local logins of that address; after 100 failed attempts within 10 minutes, setup attempts from that address SHALL be answered with 429, the problem type `urn:oea:problem:too-many-requests` and `Retry-After` without evaluating the token. Rejections for other reasons SHALL NOT count.

Trace: REQ-168 AC3, security re-review WS-04, issue #61

#### Scenario: Setup attempts above the threshold

- **WHEN** 100 setup attempts with a wrong setup token from one source address occur within 10 minutes and a further attempt with the correct token follows from that address
- **THEN** the further attempt is answered with 429 and `Retry-After`, and the instance is not bootstrapped

#### Scenario: Other addresses unaffected

- **WHEN** one source address is throttled and a setup attempt with the correct token arrives from another address
- **THEN** the bootstrapping completes

#### Scenario: Setup accepted again after the waiting time

- **WHEN** the waiting time of a throttled address has elapsed and a setup attempt with the correct token arrives from it
- **THEN** the bootstrapping completes

### Requirement: Throttled setup attempts are audited

Reaching the threshold SHALL write one event `auth.throttled` with scope `address`; every attempt rejected because of throttling SHALL be audited as `system.bootstrap-rejected` with reason `throttled`, subject to the flood suppression of security events; the setup token SHALL never be recorded.

Trace: REQ-168 AC3, REQ-175, issue #61

#### Scenario: Throttled attempt in the audit trail

- **WHEN** a setup attempt is rejected because its source address is throttled
- **THEN** the audit trail contains an event `system.bootstrap-rejected` with outcome `denied` and reason `throttled`, and no record contains the setup token

#### Scenario: Setup page shows the waiting time

- **WHEN** the setup page receives a 429 answer with `Retry-After` 30
- **THEN** it shows "Too many failed attempts. Try again in 30 seconds."

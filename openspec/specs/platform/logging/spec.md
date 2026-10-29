# platform/logging Specification

## Purpose

Defines the application log of the backend and its commands: the selectable JSON format for log platforms, the correlation with HTTP requests, and the rule that log entries never contain secrets.

## Requirements

### Requirement: Selectable JSON log format

The operator SHALL be able to switch the console log of the backend and of every backend command from plain text to JSON in one of the formats `ecs`, `logstash` or `gelf`; without a selection the log SHALL stay plain text. Every JSON entry SHALL be one line with timestamp, level, logger and message.

Trace: REQ-075, issue #63

#### Scenario: JSON log selected

- **WHEN** the operator selects the format `ecs` and the backend starts
- **THEN** every log line of the backend is a JSON object with timestamp, level, logger and message

#### Scenario: Default plain text

- **WHEN** the backend starts without a selected log format
- **THEN** the log lines are plain text as before

### Requirement: Request ID in log entries

A JSON log entry written while an HTTP request is processed SHALL carry the request ID of that request as a field, the same value as in the `X-Request-Id` header and in the audit record.

Trace: REQ-175, ADR-033, issue #63

#### Scenario: Correlation with the audit trail

- **WHEN** the JSON log format is selected and a request that writes an audit record also writes a log entry
- **THEN** the log entry carries the request ID of the audit record

### Requirement: No secrets in log entries

No log entry, in any format, SHALL contain a setup token, password, TOTP secret or code, session token, client secret or refresh token, neither in its message nor in any field.

Trace: REQ-175 AC3, ADR-033, issue #63

#### Scenario: Bootstrapping and login in JSON mode

- **WHEN** the JSON log format is selected and an operator reads the setup token, bootstraps the instance and logs in
- **THEN** no log line contains the setup token, the password or the session token

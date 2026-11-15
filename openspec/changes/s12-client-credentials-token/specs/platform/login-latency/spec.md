# Spec Delta

## ADDED Requirements

### Requirement: Processing time of token requests

Every request to the token endpoint SHALL record its server-side processing time as the metric `oea.token.processing`, tagged with the outcome. With 1,000 active service accounts, the 95th percentile of the processing time of successful token requests SHALL stay below 300 ms, verified by an automated measurement in CI.

Trace: US-008 AC2, REQ-008

#### Scenario: Token requests within the target

- **WHEN** service accounts of an instance with 1,000 active service accounts request tokens after a warm-up
- **THEN** the 95th percentile of their processing time is below 300 ms

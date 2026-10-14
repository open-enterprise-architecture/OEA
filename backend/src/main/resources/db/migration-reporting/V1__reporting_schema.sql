-- Reporting schemas (ADR-034, ADR-037). Runs as oea_reporting_owner, the owner of the
-- schemas reporting and reporting_ctx created by provisioning. Views follow after the
-- walking skeleton.

GRANT USAGE ON SCHEMA reporting TO oea_app, oea_analytics;
-- Startup validation reads the schema history as the runtime role.
GRANT SELECT ON reporting.flyway_schema_history TO oea_app;

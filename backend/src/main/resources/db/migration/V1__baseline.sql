-- Baseline of schema public (ADR-037). Runs as oea_owner, the owner of schema public.
-- Business tables follow with the first user stories.

-- Startup validation reads the schema history as the runtime role.
GRANT USAGE ON SCHEMA public TO oea_app;
GRANT SELECT ON public.flyway_schema_history TO oea_app;

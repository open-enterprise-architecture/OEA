-- Audit source CLI for host-side commands such as the break-glass recovery
-- (change us-019-admin-safeguards, design D6). Runs as oea_owner. Additive change only.

ALTER TABLE audit.events DROP CONSTRAINT events_source_check;
ALTER TABLE audit.events ADD CONSTRAINT events_source_check
    CHECK (source IN ('UI', 'API', 'Job', 'Import', 'CLI'));

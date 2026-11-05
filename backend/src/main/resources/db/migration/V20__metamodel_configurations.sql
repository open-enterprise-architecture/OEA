-- Metamodel configurations as a resource: names, versions, solution extensions and the permission
-- that maintains them (us-037-solution-extensions-and-lock-mode D1, D5; US-035, US-037, REQ-035,
-- REQ-037, ADR-032). Runs as oea_owner.

ALTER TABLE metamodel_configurations
    ADD COLUMN name    TEXT,
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1 CONSTRAINT metamodel_configurations_version CHECK (version >= 1);

UPDATE metamodel_configurations SET name = 'Instance metamodel' WHERE scope = 'instance';

ALTER TABLE metamodel_configurations
    ALTER COLUMN name SET NOT NULL,
    ADD CONSTRAINT metamodel_configurations_name CHECK (length(name) BETWEEN 1 AND 100),
    -- an extension belongs to a solution; the reference gets its foreign key with solutions (S10)
    ADD CONSTRAINT metamodel_configurations_solution CHECK ((scope = 'solution') = (solution_id IS NOT NULL));

CREATE UNIQUE INDEX metamodel_configurations_unique_name ON metamodel_configurations (lower(name));

-- extensions without metatypes can be deleted (design D1); the instance configuration never
CREATE FUNCTION metamodel_configurations_keep_instance() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_catalog
AS $$
BEGIN
    IF OLD.scope = 'instance' THEN
        RAISE EXCEPTION 'the instance configuration cannot be deleted';
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER metamodel_configurations_keep_instance BEFORE DELETE ON metamodel_configurations
    FOR EACH ROW EXECUTE FUNCTION metamodel_configurations_keep_instance();
REVOKE ALL ON FUNCTION metamodel_configurations_keep_instance() FROM PUBLIC;

GRANT DELETE ON metamodel_configurations TO oea_app;

-- the new permission follows metamodel:write in the catalog (design D5)
UPDATE permissions SET sort_order = sort_order + 1 WHERE sort_order > 12;
INSERT INTO permissions (id, category, admin_class, sort_order) VALUES
    ('solution-metamodel:write', 'solution-metamodel', false, 13);
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('enterprise-architect', 'solution-metamodel:write'),
    ('metamodel-admin', 'solution-metamodel:write');

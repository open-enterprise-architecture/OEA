-- Component hierarchy (change us-135-component-hierarchy; docs/data-model.puml, ADR-021, ADR-022).
-- Runs as oea_owner. entities.parent_entity_id, is_structural_grouper and auto_created exist since V6.

-- The built-in element metatype of structuring aids (US-136). Its UUID is the constant
-- BuiltInMetatypes.STRUCTURING_AID. It is read-only (scope built-in) and has no property mappings.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM metatypes
               WHERE config_id = '00000000-0000-0000-0000-000000000001' AND lower(name) = 'structuring aid') THEN
        RAISE EXCEPTION 'The metatype name "Structuring aid" is taken in the instance metamodel; rename it before the upgrade.';
    END IF;
END
$$;

INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, sort_order, scope)
VALUES ('c3f523b8-f07f-5475-9ab0-6f2ea39b537e', '00000000-0000-0000-0000-000000000001', 'Structuring aid', NULL,
        false, 0, 'built-in');

-- The composition mark (US-135, REQ-142): the connection metatype of the automatic connection
ALTER TABLE metatypes
    ADD COLUMN is_composition BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT metatypes_composition_is_connection CHECK (NOT is_composition OR is_connection);

-- At most one composition metatype per instance (the service allows the mark in the instance configuration only)
CREATE UNIQUE INDEX metatypes_one_composition ON metatypes ((true)) WHERE is_composition;

-- The parent is a system field of the history (version of the child; old and new value are the parent ID as text)
ALTER TABLE entity_system_field_history DROP CONSTRAINT entity_system_field_history_field_name_check;
ALTER TABLE entity_system_field_history ADD CONSTRAINT entity_system_field_history_field_name_check
    CHECK (field_name IN ('name', 'description', 'is_logical', 'metatype_id', 'source_entity_id', 'target_entity_id',
                          'parent_entity_id'));

CREATE INDEX entities_parent ON entities (parent_entity_id)
    WHERE deleted_at IS NULL AND parent_entity_id IS NOT NULL;

-- The built-in role Solution Architect arranges components and creates structuring aids (owner decision 2026-11-18)
INSERT INTO role_permissions (role_id, permission_id) VALUES ('solution-architect', 'entity:write');

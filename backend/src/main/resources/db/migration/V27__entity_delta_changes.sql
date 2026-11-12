-- The delta `modified` (us-041-modified-delta D1; US-041, REQ-040): a change row holds a property
-- (property_def_id) or a system field (system_field, values in the text columns), with the before
-- value recorded by the server at capture and the after value; no value means none. The rule of
-- exactly one after value for a delta `new` moves to the application.

ALTER TABLE entity_delta_property_values
    ALTER COLUMN property_def_id DROP NOT NULL,
    ADD COLUMN system_field TEXT CHECK (system_field IN ('name', 'description')),
    ADD COLUMN before_value_text TEXT,
    ADD COLUMN before_value_int  INT8,
    ADD COLUMN before_value_bool BOOLEAN,
    ADD COLUMN before_value_date DATE,
    ADD COLUMN before_value_ref  INTEGER REFERENCES entities (id),
    ADD COLUMN before_value_enum TEXT,
    DROP CONSTRAINT entity_delta_property_values_one_value,
    ADD CONSTRAINT entity_delta_property_values_field CHECK (num_nonnulls(property_def_id, system_field) = 1),
    ADD CONSTRAINT entity_delta_property_values_after CHECK (num_nonnulls(after_value_text, after_value_int,
        after_value_bool, after_value_date, after_value_ref, after_value_enum) <= 1),
    ADD CONSTRAINT entity_delta_property_values_before CHECK (num_nonnulls(before_value_text, before_value_int,
        before_value_bool, before_value_date, before_value_ref, before_value_enum) <= 1);

CREATE UNIQUE INDEX entity_delta_property_values_system_field
    ON entity_delta_property_values (delta_id, system_field) WHERE system_field IS NOT NULL;

-- the last edit of a delta `modified` (updateSolutionDelta); the column grant also allows locking
-- the delta row during an edit
ALTER TABLE entity_deltas
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN updated_by BIGINT REFERENCES persons (id);

GRANT UPDATE (updated_at, updated_by) ON entity_deltas TO oea_app;

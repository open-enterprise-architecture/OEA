-- Entity deltas of a solution in project mode (us-040-entity-deltas D1; US-040, US-042, REQ-040).
-- A delta `new` carries the proposed entity itself and has no entity row before the go-live (S14,
-- owner decision 2026-11-12); `retiring` names an entity of the baseline. `modified` follows with
-- US-041, which adds the before values and system fields to entity_delta_property_values.

CREATE TABLE entity_deltas (
    id          UUID        PRIMARY KEY,
    solution_id UUID        NOT NULL REFERENCES solutions (id),
    delta_type  TEXT        NOT NULL CHECK (delta_type IN ('new', 'modified', 'retiring')),
    -- null for new until the go-live
    entity_id   INTEGER     REFERENCES entities (id),
    -- new only: the proposed entity
    metatype_id UUID        REFERENCES metatypes (id),
    name        TEXT        CHECK (length(name) BETWEEN 1 AND 255),
    description TEXT        CHECK (length(description) <= 2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT      NOT NULL REFERENCES persons (id),
    CONSTRAINT entity_deltas_new_shape CHECK (
        (delta_type = 'new') = (entity_id IS NULL)
        AND (delta_type = 'new') = (metatype_id IS NOT NULL AND name IS NOT NULL))
);

CREATE INDEX entity_deltas_solution ON entity_deltas (solution_id);

-- one delta per entity and solution; one new delta per metatype and name (case-insensitive)
CREATE UNIQUE INDEX entity_deltas_entity_unique ON entity_deltas (solution_id, entity_id)
    WHERE entity_id IS NOT NULL;
CREATE UNIQUE INDEX entity_deltas_new_unique ON entity_deltas (solution_id, metatype_id, lower(name))
    WHERE delta_type = 'new';

-- typed values like entity_property_values (ADR-022); after values only until US-041
CREATE TABLE entity_delta_property_values (
    id               UUID    PRIMARY KEY,
    delta_id         UUID    NOT NULL REFERENCES entity_deltas (id) ON DELETE CASCADE,
    property_def_id  UUID    NOT NULL REFERENCES property_definitions (id),
    after_value_text TEXT,
    after_value_int  INT8,
    after_value_bool BOOLEAN,
    after_value_date DATE,
    after_value_ref  INTEGER REFERENCES entities (id),
    after_value_enum TEXT,
    CONSTRAINT entity_delta_property_values_unique UNIQUE (delta_id, property_def_id),
    CONSTRAINT entity_delta_property_values_one_value CHECK (num_nonnulls(after_value_text, after_value_int,
        after_value_bool, after_value_date, after_value_ref, after_value_enum) = 1)
);

GRANT SELECT, INSERT, DELETE ON entity_deltas, entity_delta_property_values TO oea_app;

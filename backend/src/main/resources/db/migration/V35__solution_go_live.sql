-- Go-live of a solution in project mode (change us-152-solution-go-live D1; US-152;
-- docs/data-model.puml, business-objects/solution.md). Runs as oea_owner.
--
-- A delta `new` records the entity the go-live created for it. The go-live sets the status of the
-- solution, and soft-deletes entities of deltas `retiring` (ADR-019). Metamodel violations taken
-- live are stored as open issues of the solution.

ALTER TABLE entity_deltas
    DROP CONSTRAINT entity_deltas_new_shape,
    ADD CONSTRAINT entity_deltas_new_shape CHECK (
        (delta_type = 'new') = (metatype_id IS NOT NULL AND name IS NOT NULL)
        AND (delta_type = 'new' OR entity_id IS NOT NULL));

GRANT UPDATE (entity_id) ON entity_deltas TO oea_app;
GRANT UPDATE (status) ON solutions TO oea_app;

CREATE TABLE solution_open_issues (
    id              UUID        PRIMARY KEY,
    solution_id     UUID        NOT NULL REFERENCES solutions (id),
    entity_id       INTEGER     NOT NULL REFERENCES entities (id),
    field           TEXT        NOT NULL CHECK (length(field) BETWEEN 1 AND 64),
    property_def_id UUID        REFERENCES property_definitions (id),
    kind            TEXT        NOT NULL CHECK (kind IN ('mandatory-missing', 'invalid-value')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT solution_open_issues_unique UNIQUE (solution_id, entity_id, field)
);

CREATE INDEX solution_open_issues_entity ON solution_open_issues (entity_id);

REVOKE ALL ON solution_open_issues FROM PUBLIC;
GRANT SELECT, INSERT ON solution_open_issues TO oea_app;

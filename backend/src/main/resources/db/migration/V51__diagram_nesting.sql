-- Nested shapes, hierarchy lines and the setting "Ask for a new parent" (change us-074-diagram-nesting;
-- docs/data-model.puml, business-objects/diagram.md, ADR-019, ADR-030). Runs as oea_owner.

-- A shape may lie in another shape of the same diagram (a container). SET NULL, not CASCADE: the save deletes the
-- removed shapes before it upserts the others, so a cascade would delete a nested shape that the request keeps
-- under another container, and the hidden shapes of deleted entities nested in a removed container. The service
-- checks the same diagram, the drawing order, the depth, cycles and containment.
ALTER TABLE diagram_shapes ADD COLUMN parent_shape_id UUID NULL REFERENCES diagram_shapes (id) ON DELETE SET NULL;
CREATE INDEX diagram_shapes_parent ON diagram_shapes (parent_shape_id) WHERE parent_shape_id IS NOT NULL;

-- Containers grow with their children: width and height up to 10 000 pt
ALTER TABLE diagram_shapes DROP CONSTRAINT diagram_shapes_width_pt_check;
ALTER TABLE diagram_shapes DROP CONSTRAINT diagram_shapes_height_pt_check;
ALTER TABLE diagram_shapes ALTER COLUMN width_pt TYPE NUMERIC(7,2);
ALTER TABLE diagram_shapes ALTER COLUMN height_pt TYPE NUMERIC(7,2);
ALTER TABLE diagram_shapes ADD CONSTRAINT diagram_shapes_width_pt_check CHECK (width_pt BETWEEN 1 AND 10000);
ALTER TABLE diagram_shapes ADD CONSTRAINT diagram_shapes_height_pt_check CHECK (height_pt BETWEEN 1 AND 10000);

-- A line from a container shape to the shape of a child entity that the diagram shows elsewhere; shown only while
-- the hierarchy holds. Replaced as a whole on each save, like the edges.
CREATE TABLE diagram_hierarchy_lines (
    id              UUID PRIMARY KEY,
    diagram_id      UUID NOT NULL REFERENCES diagrams (id) ON DELETE CASCADE,
    parent_shape_id UUID NOT NULL REFERENCES diagram_shapes (id) ON DELETE CASCADE,
    child_shape_id  UUID NOT NULL REFERENCES diagram_shapes (id) ON DELETE CASCADE,
    CHECK (parent_shape_id <> child_shape_id),
    UNIQUE (parent_shape_id, child_shape_id)
);

CREATE INDEX diagram_hierarchy_lines_diagram ON diagram_hierarchy_lines (diagram_id);
CREATE INDEX diagram_hierarchy_lines_child ON diagram_hierarchy_lines (child_shape_id);

REVOKE ALL ON diagram_hierarchy_lines FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON diagram_hierarchy_lines TO oea_app;

-- The override per viewpoint: true, false, or NULL for the global setting
ALTER TABLE viewpoints ADD COLUMN ask_new_parent BOOLEAN NULL;

-- The global setting, one row like visibility_settings (V24)
CREATE TABLE diagram_settings (
    id             SMALLINT    PRIMARY KEY CHECK (id = 1),
    ask_new_parent BOOLEAN     NOT NULL DEFAULT true,
    version        INTEGER     NOT NULL DEFAULT 1,
    updated_at     TIMESTAMPTZ NULL,
    updated_by     BIGINT      NULL REFERENCES persons (id)
);
INSERT INTO diagram_settings (id) VALUES (1);

REVOKE ALL ON diagram_settings FROM PUBLIC;
GRANT SELECT, UPDATE ON diagram_settings TO oea_app;

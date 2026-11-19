-- Diagrams as objects (change us-074-diagrams; docs/data-model.puml, business-objects/diagram.md, ADR-030,
-- ADR-032). Runs as oea_owner. The shapes and connections of a diagram follow with the canvas.

CREATE TABLE diagrams (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT        NOT NULL CHECK (name = btrim(name) AND name <> '' AND length(name) <= 200),
    description  TEXT        CHECK (length(description) <= 2000),
    -- no cascade: the service deletes and audits the diagrams before it deletes their viewpoint
    viewpoint_id UUID        NOT NULL REFERENCES viewpoints (id),
    version      INTEGER     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT      NOT NULL REFERENCES persons (id),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT      NOT NULL REFERENCES persons (id),
    deleted_at   TIMESTAMPTZ,
    deleted_by   BIGINT      REFERENCES persons (id),
    CONSTRAINT diagrams_deleted_check CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- Names are stored trimmed, so lower(name) is the comparison ignoring case (as viewpoints_name_unique);
-- the name of a deleted diagram is free again (design D8 of us-074-diagrams)
CREATE UNIQUE INDEX diagrams_name_unique ON diagrams (lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX diagrams_viewpoint ON diagrams (viewpoint_id);

REVOKE ALL ON diagrams FROM PUBLIC;

-- Runtime role: a deleted diagram is marked and stays restorable; it is removed for good only with its viewpoint
GRANT SELECT, INSERT, UPDATE, DELETE ON diagrams TO oea_app;

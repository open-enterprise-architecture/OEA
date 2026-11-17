-- Generic viewpoints of the instance (us-090-viewpoints D1; US-090, US-093, UC-12): a name, a
-- description, and the element and connection metatypes that belong to the view. Notations follow
-- with the diagrams (owner decision 2026-11-17).

CREATE TABLE viewpoints (
    id          UUID        PRIMARY KEY,
    name        TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
    description TEXT        CHECK (length(description) <= 1000),
    version     INTEGER     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT      NOT NULL REFERENCES persons (id),
    updated_at  TIMESTAMPTZ,
    updated_by  BIGINT      REFERENCES persons (id)
);

-- names are stored trimmed, so lower(name) is the case-insensitive comparison
CREATE UNIQUE INDEX viewpoints_name_unique ON viewpoints (lower(name));

-- a deleted metatype leaves the viewpoints that allow it; the kind (element or connection) and
-- the configuration are checked by the service
CREATE TABLE viewpoint_metatypes (
    viewpoint_id UUID NOT NULL REFERENCES viewpoints (id) ON DELETE CASCADE,
    metatype_id  UUID NOT NULL REFERENCES metatypes (id) ON DELETE CASCADE,
    PRIMARY KEY (viewpoint_id, metatype_id)
);

CREATE TABLE viewpoint_connection_types (
    viewpoint_id UUID NOT NULL REFERENCES viewpoints (id) ON DELETE CASCADE,
    metatype_id  UUID NOT NULL REFERENCES metatypes (id) ON DELETE CASCADE,
    PRIMARY KEY (viewpoint_id, metatype_id)
);

CREATE INDEX viewpoint_metatypes_metatype ON viewpoint_metatypes (metatype_id);
CREATE INDEX viewpoint_connection_types_metatype ON viewpoint_connection_types (metatype_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON viewpoints, viewpoint_metatypes, viewpoint_connection_types TO oea_app;

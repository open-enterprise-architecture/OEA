-- Notation, system-defined viewpoints and the notation mapping (us-091-viewpoint-notations D1;
-- US-091, US-092, REQ-090 to REQ-092; docs/data-model.puml, business-objects/viewpoint.md).
-- Runs as oea_owner.

-- Existing viewpoints get the neutral notation and stay user-defined through the defaults.
ALTER TABLE viewpoints
    ADD COLUMN notation TEXT NOT NULL DEFAULT 'generic'
        CONSTRAINT viewpoints_notation_check CHECK (notation IN ('generic', 'archimate3', 'uml', 'bpmn2')),
    ADD COLUMN viewpoint_type TEXT NOT NULL DEFAULT 'user-defined'
        CONSTRAINT viewpoints_viewpoint_type_check CHECK (viewpoint_type IN ('system-defined', 'user-defined')),
    ADD COLUMN source_package_id TEXT,
    ADD COLUMN source_package_version TEXT;

-- a viewpoint is system-defined exactly when a package installed it (BR-02)
ALTER TABLE viewpoints ADD CONSTRAINT viewpoints_origin
    CHECK ((viewpoint_type = 'system-defined') = (source_package_id IS NOT NULL));

CREATE INDEX viewpoints_source_package ON viewpoints (source_package_id) WHERE source_package_id IS NOT NULL;

-- the optional notation mapping: one notation element per allowed metatype (BR-06, BR-07); the
-- service checks that the metatype is allowed and that the element fits the notation
CREATE TABLE viewpoint_mappings (
    viewpoint_id     UUID    NOT NULL REFERENCES viewpoints (id) ON DELETE CASCADE,
    metatype_id      UUID    NOT NULL REFERENCES metatypes (id) ON DELETE CASCADE,
    notation_element TEXT    NOT NULL CHECK (length(notation_element) BETWEEN 1 AND 100),
    default_width    INTEGER CHECK (default_width BETWEEN 1 AND 2000),
    default_height   INTEGER CHECK (default_height BETWEEN 1 AND 2000),
    visual_hint      TEXT    CHECK (visual_hint ~ '^#[0-9a-fA-F]{6}$'),
    PRIMARY KEY (viewpoint_id, metatype_id)
);

CREATE INDEX viewpoint_mappings_metatype ON viewpoint_mappings (metatype_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON viewpoint_mappings TO oea_app;

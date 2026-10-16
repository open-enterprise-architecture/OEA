-- Repository (change us-141-entity-batch-upsert; docs/data-model.puml, ADR-016, ADR-019, ADR-022, ADR-026).
-- Runs as oea_owner. entity_connections follows with connections (US-141 AC2).

CREATE TABLE entities (
    id                    SERIAL      PRIMARY KEY,
    metatype_id           UUID        NOT NULL REFERENCES metatypes (id),
    name                  TEXT        NOT NULL CHECK (btrim(name) <> '' AND length(name) <= 255),
    description           TEXT        NOT NULL DEFAULT '' CHECK (length(description) <= 2000),
    is_logical            BOOLEAN     NOT NULL DEFAULT true,
    version               INTEGER     NOT NULL DEFAULT 1 CHECK (version >= 1),
    parent_entity_id      INTEGER     REFERENCES entities (id),
    is_structural_grouper BOOLEAN     NOT NULL DEFAULT false,
    auto_created          BOOLEAN     NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            BIGINT      NOT NULL REFERENCES persons (id),
    updated_at            TIMESTAMPTZ,
    updated_by            BIGINT      REFERENCES persons (id),
    external_id           TEXT        CHECK (external_id <> ''),
    deleted_at            TIMESTAMPTZ,
    deleted_by            BIGINT      REFERENCES persons (id),
    deletion_reason       TEXT
);

-- Deleted entities keep their external ID (ADR-019 decides about reuse)
CREATE UNIQUE INDEX entities_external_id ON entities (external_id) WHERE external_id IS NOT NULL;
CREATE INDEX entities_active ON entities (metatype_id) WHERE deleted_at IS NULL;

CREATE TABLE entity_property_values (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       INTEGER NOT NULL REFERENCES entities (id),
    property_def_id UUID    NOT NULL REFERENCES property_definitions (id),
    value_text      TEXT,
    value_int       INT8,
    value_bool      BOOLEAN,
    value_date      DATE,
    value_ref       INTEGER REFERENCES entities (id),
    value_enum      TEXT,
    CONSTRAINT entity_property_values_unique UNIQUE (entity_id, property_def_id),
    CONSTRAINT entity_property_values_one_value
        CHECK (num_nonnulls(value_text, value_int, value_bool, value_date, value_ref, value_enum) <= 1)
);

-- Entity history (ADR-022 delta versioning, ADR-033 §10): append-only for the runtime role
CREATE TABLE entity_version_headers (
    id         BIGSERIAL   PRIMARY KEY,
    entity_id  INTEGER     NOT NULL REFERENCES entities (id),
    version    INTEGER     NOT NULL CHECK (version >= 1),
    changed_by BIGINT      NOT NULL REFERENCES persons (id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_version_headers_unique UNIQUE (entity_id, version)
);

CREATE TABLE entity_system_field_history (
    id             BIGSERIAL   PRIMARY KEY,
    entity_id      INTEGER     NOT NULL REFERENCES entities (id),
    entity_version INTEGER     NOT NULL,
    field_name     TEXT        NOT NULL CHECK (field_name IN ('name', 'description', 'is_logical', 'metatype_id',
                                                              'source_entity_id', 'target_entity_id')),
    old_value      TEXT,
    new_value      TEXT,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by     BIGINT      NOT NULL REFERENCES persons (id)
);

CREATE INDEX entity_system_field_history_entity ON entity_system_field_history (entity_id, entity_version);

CREATE TABLE entity_property_value_history (
    id              BIGSERIAL   PRIMARY KEY,
    entity_id       INTEGER     NOT NULL REFERENCES entities (id),
    property_def_id UUID        NOT NULL REFERENCES property_definitions (id),
    entity_version  INTEGER     NOT NULL,
    value_text      TEXT,
    value_int       INT8,
    value_bool      BOOLEAN,
    value_date      DATE,
    value_ref       INTEGER     REFERENCES entities (id),
    value_enum      TEXT,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by      BIGINT      NOT NULL REFERENCES persons (id),
    CONSTRAINT entity_property_value_history_one_value
        CHECK (num_nonnulls(value_text, value_int, value_bool, value_date, value_ref, value_enum) <= 1)
);

CREATE INDEX entity_property_value_history_entity ON entity_property_value_history (entity_id, entity_version);

REVOKE ALL ON entities, entity_property_values, entity_version_headers, entity_system_field_history,
    entity_property_value_history FROM PUBLIC;

-- Runtime role: entities are never hard-deleted (ADR-019); removing a value deletes its row;
-- the history is append-only (no UPDATE, DELETE, TRUNCATE)
GRANT SELECT, INSERT, UPDATE ON entities TO oea_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON entity_property_values TO oea_app;
GRANT SELECT, INSERT ON entity_version_headers, entity_system_field_history, entity_property_value_history TO oea_app;
GRANT USAGE ON SEQUENCE entities_id_seq, entity_version_headers_id_seq, entity_system_field_history_id_seq,
    entity_property_value_history_id_seq TO oea_app;

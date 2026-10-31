-- Editing of the metamodel through the API (change us-032-metatype-editing, design D3;
-- docs/data-model.puml). Runs as oea_owner.

-- Base type of a metatype: it inherits the property mappings of all its base types; cycles are
-- rejected by the service. The re-import setting decides whether a package re-import removes the
-- organization mappings of the metatype ('inherit' follows the base type, then the global setting).
ALTER TABLE metatypes
    ADD COLUMN base_metatype_id               UUID REFERENCES metatypes (id),
    ADD COLUMN reimport_organization_mappings TEXT NOT NULL DEFAULT 'inherit'
        CHECK (reimport_organization_mappings IN ('inherit', 'keep', 'remove')),
    ADD CONSTRAINT metatypes_base_not_self CHECK (base_metatype_id <> id);

CREATE INDEX metatypes_base ON metatypes (base_metatype_id) WHERE base_metatype_id IS NOT NULL;

ALTER TABLE metamodel_configurations
    ADD COLUMN reimport_organization_mappings TEXT NOT NULL DEFAULT 'keep'
        CHECK (reimport_organization_mappings IN ('keep', 'remove'));

-- A deleted definition that the entity history references is retired: hidden, not mappable, its
-- name stays taken.
ALTER TABLE property_definitions
    ADD COLUMN retired_at TIMESTAMPTZ;

-- Entities that miss a mandatory property mapped after they were written; cleared when the entity
-- gets a value or the property is removed from its metatype.
CREATE TABLE entity_mandatory_marks (
    entity_id       INTEGER     NOT NULL REFERENCES entities (id),
    property_def_id UUID        NOT NULL REFERENCES property_definitions (id),
    marked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (entity_id, property_def_id)
);

CREATE INDEX entity_mandatory_marks_property ON entity_mandatory_marks (property_def_id);

CREATE INDEX entity_property_values_property ON entity_property_values (property_def_id);

REVOKE ALL ON entity_mandatory_marks FROM PUBLIC;
GRANT SELECT, INSERT, DELETE ON entity_mandatory_marks TO oea_app;
-- Organization metamodel content can be deleted by metamodel administrators (US-032).
GRANT DELETE ON metatypes, property_categories, property_definitions TO oea_app;

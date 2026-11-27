-- Organization model, account mapping and derived account link (change us-075-lane-assignments
-- D2, D5; REQ-077, REQ-127; docs/data-model.puml, business-objects/process.md and person.md).
-- Runs as oea_owner.

-- Which metatypes play the parts organizational unit, role, person, belongs-to, performs-role
-- and responsible; one row like visibility_settings (V24), a part is unset (NULL) until set
CREATE TABLE organization_model (
    id                        SMALLINT    PRIMARY KEY CHECK (id = 1),
    org_unit_metatype_id      UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    role_metatype_id          UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    person_metatype_id        UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    belongs_to_metatype_id    UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    performs_role_metatype_id UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    responsible_metatype_id   UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    version                   INTEGER     NOT NULL DEFAULT 1,
    updated_at                TIMESTAMPTZ NULL,
    updated_by                BIGINT      NULL REFERENCES persons (id)
);
INSERT INTO organization_model (id) VALUES (1);

REVOKE ALL ON organization_model FROM PUBLIC;
GRANT SELECT, UPDATE ON organization_model TO oea_app;

-- The account mapping: the string property of the person metatype and the account attribute
-- that is compared with it; one row, unset while property_def_id is NULL
CREATE TABLE person_link_mapping (
    id                SMALLINT PRIMARY KEY CHECK (id = 1),
    property_def_id   UUID     NULL REFERENCES property_definitions (id) ON DELETE SET NULL,
    account_attribute TEXT     NULL CHECK (account_attribute IN ('email', 'idp-subject'))
);
INSERT INTO person_link_mapping (id) VALUES (1);

REVOKE ALL ON person_link_mapping FROM PUBLIC;
GRANT SELECT, UPDATE ON person_link_mapping TO oea_app;

-- The element linked through the mapping; the manual link stays entity_id, the effective link
-- is coalesce(entity_id, mapped_entity_id)
ALTER TABLE persons ADD COLUMN mapped_entity_id INTEGER REFERENCES entities (id);
CREATE UNIQUE INDEX persons_mapped_entity_id ON persons (mapped_entity_id) WHERE mapped_entity_id IS NOT NULL;

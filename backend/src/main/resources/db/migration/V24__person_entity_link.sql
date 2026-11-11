-- Account link and person metatype for connection-scoped visibility (change
-- us-127-connection-scoped-visibility D1; REQ-127, ADR-032 DM-7; docs/data-model.puml,
-- business-objects/person.md). Runs as oea_owner.

-- the element of the EA model that represents the person; one account per element
ALTER TABLE persons ADD COLUMN entity_id INTEGER REFERENCES entities (id);
CREATE UNIQUE INDEX persons_entity_id ON persons (entity_id) WHERE entity_id IS NOT NULL;

-- the entity type whose elements represent persons; one row, present before the setup
CREATE TABLE visibility_settings (
    id                 SMALLINT PRIMARY KEY CHECK (id = 1),
    person_metatype_id UUID     REFERENCES metatypes (id)
);
INSERT INTO visibility_settings (id) VALUES (1);

REVOKE ALL ON visibility_settings FROM PUBLIC;
GRANT SELECT, UPDATE ON visibility_settings TO oea_app;

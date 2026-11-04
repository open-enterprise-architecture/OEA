-- Connections: entities of a connection metatype with a fixed source and target
-- (us-036-connection-metatypes D1; US-036, REQ-036, ADR-010). Runs as oea_owner.

CREATE TABLE entity_connections (
    entity_id        INTEGER PRIMARY KEY REFERENCES entities (id),
    source_entity_id INTEGER NOT NULL REFERENCES entities (id),
    target_entity_id INTEGER NOT NULL REFERENCES entities (id)
);

CREATE INDEX entity_connections_source ON entity_connections (source_entity_id);
CREATE INDEX entity_connections_target ON entity_connections (target_entity_id);

-- endpoints never change (design D4): the runtime role only inserts and reads
REVOKE ALL ON entity_connections FROM PUBLIC;
GRANT SELECT, INSERT ON entity_connections TO oea_app;

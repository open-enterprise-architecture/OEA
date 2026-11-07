-- Read and write roles of a role-restricted property (change us-124-property-visibility D1;
-- REQ-124, ADR-032 §5; docs/data-model.puml). Runs as oea_owner. The mode and the scoping type
-- stay in property_definitions (V5); the service checks that every write role is a read role.

CREATE TABLE property_visibility_roles (
    property_def_id UUID NOT NULL REFERENCES property_definitions (id),
    role_id         TEXT NOT NULL REFERENCES authorization_roles (id),
    access          TEXT NOT NULL CHECK (access IN ('read', 'write')),
    PRIMARY KEY (property_def_id, role_id, access)
);

-- a change replaces the roles of a property: delete, then insert
REVOKE ALL ON property_visibility_roles FROM PUBLIC;
GRANT SELECT, INSERT, DELETE ON property_visibility_roles TO oea_app;

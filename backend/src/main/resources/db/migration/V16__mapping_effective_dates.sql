-- Necessity of property mappings by effective date (change us-137-mapping-effective-date, design D1;
-- ADR-022, ADR-049; docs/data-model.puml). Runs as oea_owner.

-- Every entry records its scope: entries of administrators are 'organization', also when they
-- override the necessity of a package mapping (ADR-049); rows of a package keep the package scope.
ALTER TABLE metatype_property_mappings
    ADD COLUMN scope                TEXT,
    ADD COLUMN effective_applied_at TIMESTAMPTZ;

UPDATE metatype_property_mappings m
SET scope = p.scope, effective_applied_at = m.created_at
FROM property_definitions p WHERE p.id = m.property_def_id;

ALTER TABLE metatype_property_mappings
    ALTER COLUMN scope SET NOT NULL,
    ADD CONSTRAINT metatype_property_mappings_scope CHECK (scope IN ('built-in', 'imported', 'organization'));

-- entries whose effects (marks) the scheduled check still has to apply
CREATE INDEX metatype_property_mappings_pending ON metatype_property_mappings (valid_from)
    WHERE effective_applied_at IS NULL;

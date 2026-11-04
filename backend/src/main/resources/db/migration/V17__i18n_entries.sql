-- Translated labels of metamodel content and the language of a person
-- (change us-140-dynamic-labels, design D2 and D6; ADR-025, ADR-049; docs/data-model.puml).
-- Runs as oea_owner.

-- One row per element, field, locale and scope: package labels ('imported') and organization
-- labels are separate rows, so a re-import replaces only the package rows and the organization
-- label wins on the same key (ADR-049). The reference is polymorphic and has no foreign key;
-- every delete path of an element removes its rows in the same transaction.
CREATE TABLE i18n_entries (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type TEXT        NOT NULL CHECK (entity_type IN ('metatype', 'property_category', 'property_def', 'enum_value')),
    entity_id   UUID        NOT NULL,
    locale      TEXT        NOT NULL CHECK (locale IN ('de', 'en', 'fr', 'it')),
    field       TEXT        NOT NULL CHECK (field IN ('name', 'label')),
    value       TEXT        NOT NULL CHECK (length(value) BETWEEN 1 AND 200),
    scope       TEXT        NOT NULL CHECK (scope IN ('imported', 'organization')),
    package_id  TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT i18n_entries_key UNIQUE (entity_type, entity_id, locale, field, scope),
    CONSTRAINT i18n_entries_origin CHECK ((scope = 'imported') = (package_id IS NOT NULL))
);

CREATE INDEX i18n_entries_locale ON i18n_entries (locale);
CREATE INDEX i18n_entries_entity ON i18n_entries (entity_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON i18n_entries TO oea_app;

-- No value means the language of the browser; the former default 'de' was never chosen by anyone.
ALTER TABLE persons ALTER COLUMN ui_language DROP DEFAULT;
ALTER TABLE persons ALTER COLUMN ui_language DROP NOT NULL;
UPDATE persons SET ui_language = NULL;
ALTER TABLE persons ADD CONSTRAINT persons_ui_language CHECK (ui_language IN ('de', 'en', 'fr', 'it'));

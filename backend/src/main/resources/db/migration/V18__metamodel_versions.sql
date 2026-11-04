-- Versions of metamodel elements for optimistic locking (scr-030-metamodel-configuration D1;
-- UC-04 A5, REQ-032): every change increments the version; If-Match names the version read.

ALTER TABLE metatypes
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1 CONSTRAINT metatypes_version CHECK (version >= 1);

ALTER TABLE property_definitions
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1 CONSTRAINT property_definitions_version CHECK (version >= 1);

ALTER TABLE property_categories
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1 CONSTRAINT property_categories_version CHECK (version >= 1);

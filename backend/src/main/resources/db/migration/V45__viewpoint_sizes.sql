-- Default sizes per allowed metatype of a viewpoint, in pt (s17-viewpoint-default-sizes D1;
-- UC-10, UC-12, US-091; docs/data-model.puml, business-objects/viewpoint.md). Runs as oea_owner.

-- an element metatype has a width and a height, a connection metatype a length (all 1 to 2000 pt)
CREATE TABLE viewpoint_sizes (
    viewpoint_id UUID    NOT NULL REFERENCES viewpoints (id) ON DELETE CASCADE,
    metatype_id  UUID    NOT NULL REFERENCES metatypes (id) ON DELETE CASCADE,
    width_pt     INTEGER CHECK (width_pt  BETWEEN 1 AND 2000),
    height_pt    INTEGER CHECK (height_pt BETWEEN 1 AND 2000),
    length_pt    INTEGER CHECK (length_pt BETWEEN 1 AND 2000),
    PRIMARY KEY (viewpoint_id, metatype_id),
    CHECK ((width_pt IS NOT NULL AND height_pt IS NOT NULL AND length_pt IS NULL)
        OR (width_pt IS NULL AND height_pt IS NULL AND length_pt IS NOT NULL))
);

CREATE INDEX viewpoint_sizes_metatype ON viewpoint_sizes (metatype_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON viewpoint_sizes TO oea_app;

-- the sizes stored in mappings were canvas units (px at 96 dpi); pt = round(px * 0.75), at least 1;
-- a missing width or height of an element comes from the default 90 x 30 pt
INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, width_pt, height_pt)
SELECT l.viewpoint_id, l.metatype_id,
       CASE WHEN l.default_width IS NULL THEN 90
            ELSE GREATEST(1, ROUND(l.default_width * 0.75)::INTEGER) END,
       CASE WHEN l.default_height IS NULL THEN 30
            ELSE GREATEST(1, ROUND(l.default_height * 0.75)::INTEGER) END
FROM viewpoint_mappings l JOIN metatypes m ON m.id = l.metatype_id
WHERE NOT m.is_connection AND (l.default_width IS NOT NULL OR l.default_height IS NOT NULL);

-- a connection has no height: its width becomes the length
INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, length_pt)
SELECT l.viewpoint_id, l.metatype_id, GREATEST(1, ROUND(l.default_width * 0.75)::INTEGER)
FROM viewpoint_mappings l JOIN metatypes m ON m.id = l.metatype_id
WHERE m.is_connection AND l.default_width IS NOT NULL;

ALTER TABLE viewpoint_mappings DROP COLUMN default_width, DROP COLUMN default_height;

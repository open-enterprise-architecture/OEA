-- Conflicts of parallel solutions (change us-044-conflict-warning, design D0 and D3; US-044,
-- REQ-042; docs/data-model.puml). Runs as oea_owner.

-- Whether the baseline of a solution marks entities of the metatype that another active solution
-- changes; off by default, not inherited, kept by a package re-import (owner decision 2026-11-13).
ALTER TABLE metatypes
    ADD COLUMN show_parallel_changes BOOLEAN NOT NULL DEFAULT false;

-- The conflicts of a delta are read by entity across all solutions.
CREATE INDEX entity_deltas_entity ON entity_deltas (entity_id) WHERE entity_id IS NOT NULL;

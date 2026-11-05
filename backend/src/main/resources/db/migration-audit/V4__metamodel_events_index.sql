-- Index for reading the metamodel changes newest first (change us-034-metamodel-changes,
-- design D3). Runs as oea_owner. Additive change only: neither the hash chain nor grants change.

CREATE INDEX events_metamodel_occurred ON audit.events (occurred_at DESC, id DESC)
    WHERE event_type LIKE 'metamodel.%';

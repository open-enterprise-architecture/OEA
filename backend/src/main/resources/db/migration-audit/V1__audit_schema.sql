-- Audit schema (ADR-024, ADR-033 §1-§5, ADR-037; docs/data-model.puml).
-- Runs as oea_owner, the owner of schema audit. Additive changes only.
-- The hash-chain trigger and audit.purge_expired() follow with the first audited action.

CREATE TABLE audit.events (
    id              BIGSERIAL   PRIMARY KEY,
    event_type      TEXT        NOT NULL,
    event_category  TEXT        NOT NULL CHECK (event_category IN ('change', 'security', 'access', 'system')),
    severity        TEXT        NOT NULL CHECK (severity IN ('info', 'warning', 'high', 'critical')),
    outcome         TEXT        NOT NULL CHECK (outcome IN ('success', 'failure', 'denied')),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source          TEXT        NOT NULL CHECK (source IN ('UI', 'API', 'Job', 'Import')),
    source_ip       INET,
    request_id      TEXT,
    -- Actor (pseudonymous, ADR-033 §3)
    actor_type      TEXT        NOT NULL CHECK (actor_type IN
                        ('person', 'service-account', 'system-admin-account', 'system', 'unauthenticated')),
    actor_person_id BIGINT,
    actor_admin_id  UUID,
    -- Target
    target_type     TEXT,
    target_id       TEXT,
    entity_id       INTEGER,
    entity_name     TEXT,
    details         JSONB,
    -- External integration (ADR-026, ADR-033 §7)
    agent_id        TEXT,
    agent_label     TEXT        CHECK (char_length(agent_label) <= 100),
    agent_run       TEXT,
    -- Hash chain (ADR-033 §5)
    chain_seq       BIGINT      NOT NULL UNIQUE,
    hash_version    SMALLINT    NOT NULL,
    prev_hash       BYTEA       NOT NULL CHECK (octet_length(prev_hash) = 32),
    record_hash     BYTEA       NOT NULL CHECK (octet_length(record_hash) = 32),
    CONSTRAINT events_actor_reference CHECK (
        (actor_type IN ('person', 'service-account')) = (actor_person_id IS NOT NULL)
        AND (actor_type = 'system-admin-account') = (actor_admin_id IS NOT NULL))
);

CREATE TABLE audit.event_changes (
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT    NOT NULL REFERENCES audit.events (id),
    property_def_id  UUID,
    property_name    TEXT      NOT NULL,
    system_field     BOOLEAN   NOT NULL DEFAULT false,
    -- Values (REQ-160 AC4)
    value_protection TEXT      NOT NULL DEFAULT 'plain' CHECK (value_protection IN ('plain', 'encrypted')),
    old_value        TEXT,
    new_value        TEXT,
    old_value_enc    BYTEA,
    new_value_enc    BYTEA,
    key_id           TEXT,
    -- Hash chain
    chain_seq        BIGINT    NOT NULL UNIQUE,
    hash_version     SMALLINT  NOT NULL,
    prev_hash        BYTEA     NOT NULL CHECK (octet_length(prev_hash) = 32),
    record_hash      BYTEA     NOT NULL CHECK (octet_length(record_hash) = 32),
    CONSTRAINT event_changes_value_protection CHECK (
        (value_protection = 'encrypted' AND old_value IS NULL AND new_value IS NULL AND key_id IS NOT NULL)
        OR (value_protection = 'plain' AND old_value_enc IS NULL AND new_value_enc IS NULL AND key_id IS NULL))
);

CREATE INDEX event_changes_event_id ON audit.event_changes (event_id);

CREATE TABLE audit.retention_settings (
    id                    BIGSERIAL   PRIMARY KEY,
    retention_days        INTEGER     NOT NULL CHECK (retention_days >= 365),
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_from        TIMESTAMPTZ NOT NULL,
    recorded_by_person_id BIGINT,
    recorded_by_admin_id  UUID,
    -- Hash chain
    chain_seq             BIGINT      NOT NULL UNIQUE,
    hash_version          SMALLINT    NOT NULL,
    prev_hash             BYTEA       NOT NULL CHECK (octet_length(prev_hash) = 32),
    record_hash           BYTEA       NOT NULL CHECK (octet_length(record_hash) = 32),
    CONSTRAINT retention_settings_single_recorder CHECK (
        recorded_by_person_id IS NULL OR recorded_by_admin_id IS NULL)
);

CREATE TABLE audit.chain_head (
    id         SMALLINT    PRIMARY KEY CHECK (id = 1),
    last_seq   BIGINT      NOT NULL,
    last_hash  BYTEA       NOT NULL CHECK (octet_length(last_hash) = 32),
    updated_at TIMESTAMPTZ NOT NULL
);

-- Privileges (ADR-033 §2, ADR-037): nothing for PUBLIC; the runtime role may only
-- append and read; chain_head stays owner-only; auditors read everything.
REVOKE ALL ON ALL TABLES IN SCHEMA audit FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA audit FROM PUBLIC;

GRANT USAGE ON SCHEMA audit TO oea_app, oea_audit_cleanup, oea_auditor;

GRANT INSERT, SELECT ON audit.events, audit.event_changes, audit.retention_settings TO oea_app;
GRANT USAGE ON SEQUENCE audit.events_id_seq, audit.event_changes_id_seq, audit.retention_settings_id_seq TO oea_app;
-- Startup validation reads the schema history as the runtime role.
GRANT SELECT ON audit.flyway_schema_history TO oea_app;

GRANT SELECT ON ALL TABLES IN SCHEMA audit TO oea_auditor;

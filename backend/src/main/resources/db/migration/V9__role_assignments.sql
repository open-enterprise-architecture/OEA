-- Local role assignments (change us-144-local-role-assignments; ADR-032 DM-4; docs/data-model.puml).
-- Runs as oea_owner. Never hard-deleted: a revocation sets revoked_*; reaching valid_to expires the
-- assignment without a status change. The approval columns stay unused until four-eyes approval
-- (US-150).

CREATE TABLE role_assignments (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id            BIGINT      NOT NULL REFERENCES persons (id),
    role_id              TEXT        NOT NULL REFERENCES authorization_roles (id),
    source               TEXT        NOT NULL DEFAULT 'local' CHECK (source IN ('local')),
    status               TEXT        NOT NULL DEFAULT 'granted'
                                     CHECK (status IN ('pending', 'granted', 'rejected', 'revoked')),
    valid_from           TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to             TIMESTAMPTZ,
    reason               TEXT        CHECK (char_length(reason) <= 500),
    granted_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by_person_id BIGINT      REFERENCES persons (id),
    granted_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    -- SoD-3: the assignment combined metamodel and visibility administration (flag at creation)
    sod_conflict         BOOLEAN     NOT NULL DEFAULT false,
    -- Four-eyes approval (Q15, setting privilegedAssignmentApproval; US-150)
    approval_required    BOOLEAN     NOT NULL DEFAULT false,
    decided_at           TIMESTAMPTZ,
    decided_by_person_id BIGINT      REFERENCES persons (id),
    decided_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    decision_comment     TEXT        CHECK (char_length(decision_comment) <= 500),
    -- Revocation
    revoked_at           TIMESTAMPTZ,
    revoked_by_person_id BIGINT      REFERENCES persons (id),
    revoked_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    revocation_reason    TEXT        CHECK (char_length(revocation_reason) <= 500),
    -- Expiry recorded once in the audit trail (design D4)
    expiry_recorded_at   TIMESTAMPTZ,
    CONSTRAINT role_assignments_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT role_assignments_one_granter CHECK ((granted_by_person_id IS NULL) <> (granted_by_admin_id IS NULL)),
    CONSTRAINT role_assignments_not_self_granted CHECK (granted_by_person_id IS DISTINCT FROM person_id),
    CONSTRAINT role_assignments_revocation CHECK ((status = 'revoked') = (revoked_at IS NOT NULL)),
    CONSTRAINT role_assignments_one_revoker CHECK (
        revoked_at IS NULL OR ((revoked_by_person_id IS NULL) <> (revoked_by_admin_id IS NULL))),
    CONSTRAINT role_assignments_decision CHECK (
        NOT (approval_required AND status IN ('granted', 'rejected')) OR decided_at IS NOT NULL)
);

CREATE INDEX role_assignments_granted ON role_assignments (person_id) WHERE status = 'granted';

REVOKE ALL ON role_assignments FROM PUBLIC;
-- Runtime role: no DELETE (DM-4, no hard delete)
GRANT SELECT, INSERT, UPDATE ON role_assignments TO oea_app;

-- ============================================================
--  Flyway Migration V1 — Schéma initial Todo Enterprise
--  Exécuté automatiquement par Flyway au démarrage Spring Boot
-- ============================================================

-- Extension UUID (déjà créée par init.sql mais idempotente)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── Unités de gestion ──────────────────────────────────────
CREATE TABLE units (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived    BOOLEAN     NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE units IS 'Unités de gestion organisationnelles';

-- ─── Équipes ─────────────────────────────────────────────────
CREATE TABLE teams (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    unit_id     UUID        NOT NULL REFERENCES units(id),
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived    BOOLEAN     NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE teams IS 'Équipes rattachées aux unités de gestion';

-- ─── Utilisateurs ───────────────────────────────────────────
CREATE TABLE users (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    keycloak_id VARCHAR(36) NOT NULL UNIQUE,    -- Sub du JWT Keycloak/Cognito
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    role        VARCHAR(30)  NOT NULL,           -- GESTIONNAIRE, MANAGER, SUPER_ADMINISTRATEUR
    team_id     UUID         REFERENCES teams(id),
    unit_id     UUID         REFERENCES units(id),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'Utilisateurs de l''application — synchronisés depuis Keycloak/Cognito';
COMMENT ON COLUMN users.keycloak_id IS 'Valeur du claim "sub" dans le JWT';

CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);
CREATE INDEX idx_users_role        ON users(role);
CREATE INDEX idx_users_team_id     ON users(team_id);

-- ─── Tâches (To-Dos) ─────────────────────────────────────────
CREATE TABLE tasks (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'A_FAIRE',
                                           -- A_FAIRE, VALIDEE, REJETEE, DONE
    priority        VARCHAR(20)  NOT NULL DEFAULT 'NORMALE',
                                           -- BASSE, NORMALE, HAUTE, URGENTE
    due_date        DATE,
    owner_id        UUID         NOT NULL REFERENCES users(id),
    team_id         UUID         NOT NULL REFERENCES teams(id),
    rejection_reason TEXT,                 -- Obligatoire si status = REJETEE (RG-06)
    rejected_by_id  UUID         REFERENCES users(id),
    rejected_at     TIMESTAMPTZ,
    validated_by_id UUID         REFERENCES users(id),
    validated_at    TIMESTAMPTZ,
    done_by_id      UUID         REFERENCES users(id),
    done_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0    -- Optimistic locking JPA
);

COMMENT ON TABLE  tasks                 IS 'Actes de gestion (to-dos)';
COMMENT ON COLUMN tasks.status          IS 'Statut : A_FAIRE | VALIDEE | REJETEE | DONE';
COMMENT ON COLUMN tasks.rejection_reason IS 'Motif obligatoire si statut REJETEE (RG-06)';
COMMENT ON COLUMN tasks.version         IS 'Optimistic locking — évite les écritures concurrentes';

-- Index pour les requêtes métier fréquentes
CREATE INDEX idx_tasks_owner_id  ON tasks(owner_id);
CREATE INDEX idx_tasks_team_id   ON tasks(team_id);
CREATE INDEX idx_tasks_status    ON tasks(status);
CREATE INDEX idx_tasks_due_date  ON tasks(due_date) WHERE status != 'DONE';

-- Contrainte : motif de rejet obligatoire si statut REJETEE
ALTER TABLE tasks ADD CONSTRAINT chk_rejection_reason
    CHECK (status != 'REJETEE' OR rejection_reason IS NOT NULL);

-- ─── Commentaires ───────────────────────────────────────────
CREATE TABLE task_comments (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id    UUID        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES users(id),
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE task_comments IS 'Fil de discussion sur une tâche (tous rôles)';

CREATE INDEX idx_comments_task_id ON task_comments(task_id);

-- ─── Pièces jointes ─────────────────────────────────────────
CREATE TABLE task_attachments (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id      UUID         NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    s3_key       VARCHAR(500) NOT NULL,     -- Clé S3 (chemin dans le bucket)
    mime_type    VARCHAR(100),
    size_bytes   BIGINT,
    thumbnail_key VARCHAR(500),             -- Clé S3 de la miniature (si image)
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                            -- PENDING, VALID, INVALID, PROCESSED
    uploaded_by  UUID         NOT NULL REFERENCES users(id),
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE task_attachments IS 'Pièces jointes — métadonnées (fichier dans S3)';

CREATE INDEX idx_attachments_task_id ON task_attachments(task_id);

-- ─── Journal d'audit (Super-Admin) ──────────────────────────
CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type  VARCHAR(50)  NOT NULL,   -- TASK, USER, TEAM, UNIT
    entity_id    UUID         NOT NULL,
    action       VARCHAR(50)  NOT NULL,   -- CREATED, UPDATED, STATUS_CHANGED, DELETED
    old_value    JSONB,
    new_value    JSONB,
    performed_by UUID         REFERENCES users(id),
    performed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (performed_at);     -- Partitionnement mensuel pour les volumes

COMMENT ON TABLE audit_log IS 'Journal d''audit complet — partitionné par mois';

-- Partition initiale (mois courant + suivant)
CREATE TABLE audit_log_2026_05 PARTITION OF audit_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE audit_log_2026_06 PARTITION OF audit_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE INDEX idx_audit_entity    ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_performer ON audit_log(performed_by);

-- ─── Données initiales ───────────────────────────────────────
-- Unité et équipe de démo (correspondant au realm Keycloak)
INSERT INTO units (id, name)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Unité de gestion A');

INSERT INTO teams (id, unit_id, name)
VALUES ('11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Équipe Alpha');

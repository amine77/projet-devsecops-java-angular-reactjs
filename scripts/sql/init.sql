-- ==========================================================
--  Initialisation PostgreSQL — Todo Enterprise
--  Exécuté automatiquement au premier démarrage du container
-- ==========================================================

-- Base principale
CREATE DATABASE tododb;
-- Base Keycloak (partagée sur le même serveur en dev local)
CREATE DATABASE keycloak;

\c tododb;

-- Extension UUID (Java UUID → PostgreSQL UUID natif)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==========================================================
--  Schéma applicatif
--  Note : JPA crée les tables via Hibernate en dev.
--         Ce fichier crée uniquement ce que JPA ne peut pas
--         créer seul (extensions, schemas séparés).
-- ==========================================================

-- Schema d'audit (séparé du schéma public)
CREATE SCHEMA IF NOT EXISTS audit;

COMMENT ON DATABASE tododb IS 'Base principale Todo Enterprise';

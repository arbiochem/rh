CREATE TABLE IF NOT EXISTS agents
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    matricule TEXT NOT NULL UNIQUE,

    nom TEXT NOT NULL,

    prenom TEXT NOT NULL,

    telephone TEXT,

    email TEXT,

    actif INTEGER NOT NULL DEFAULT 1,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Schéma pour la gestion d'équipe (SQLite / Turso)
-- À exécuter une seule fois sur ta base Turso (via tursodb, le shell, ou au démarrage de l'appli)

CREATE TABLE IF NOT EXISTS equipes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom TEXT NOT NULL UNIQUE,
    description TEXT,
    date_creation TEXT NOT NULL DEFAULT (datetime('now')),
    actif INTEGER NOT NULL DEFAULT 1
);

-- Table de liaison utilisateur <-> équipe (plusieurs membres par équipe)
CREATE TABLE IF NOT EXISTS membres_equipe (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    equipe_id INTEGER NOT NULL,
    utilisateur_id INTEGER NOT NULL,
    role_equipe TEXT NOT NULL DEFAULT 'MEMBRE', -- 'CHEF' ou 'MEMBRE'
    date_ajout TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (equipe_id) REFERENCES equipes(id) ON DELETE CASCADE,
    UNIQUE (equipe_id, utilisateur_id) -- un utilisateur ne peut être ajouté qu'une fois par équipe
);

CREATE INDEX IF NOT EXISTS idx_membres_equipe_id ON membres_equipe(equipe_id);
CREATE INDEX IF NOT EXISTS idx_membres_utilisateur_id ON membres_equipe(utilisateur_id);


CREATE TABLE IF NOT EXISTS affectations
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    agent_id INTEGER NOT NULL,

    sites TEXT NOT NULL,

    date_affectation TEXT NOT NULL,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (agent_id)
        REFERENCES agents(id)
        ON DELETE CASCADE,

    UNIQUE (
        agent_id,
        date_affectation
    )
);

CREATE TABLE IF NOT EXISTS utilisateurs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER',
    actif INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX IF NOT EXISTS idx_affectations_date
ON affectations(date_affectation);


CREATE INDEX IF NOT EXISTS idx_affectations_agent_date
ON affectations(agent_id, date_affectation);
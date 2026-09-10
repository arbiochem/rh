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


CREATE TABLE IF NOT EXISTS sites
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    nom TEXT NOT NULL UNIQUE,

    adresse TEXT,

    actif INTEGER NOT NULL DEFAULT 1,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS affectations
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    agent_id INTEGER NOT NULL,

    site_id INTEGER NOT NULL,

    date_affectation TEXT NOT NULL,

    heure_debut TEXT,

    heure_fin TEXT,

    observation TEXT,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (agent_id)
        REFERENCES agents(id)
        ON DELETE CASCADE,

    FOREIGN KEY (site_id)
        REFERENCES sites(id)
        ON DELETE CASCADE,

    UNIQUE (
        agent_id,
        site_id,
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
-- Utilisateur administrateur par defaut : login "admin" / mot de passe "admin123"
-- IMPORTANT : changez ce mot de passe des la premiere connexion.
INSERT OR IGNORE INTO utilisateurs (id, username, password, role, actif)
VALUES (1, 'admin', '$2b$10$ZlcBuQM5hTEZK82FW1v/PuhaQQOxBZkOe73wzrRViGsCsJnRTvx2S', 'ADMIN', 1);


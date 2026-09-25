-- Table des pointages dans SQL Server (base rh_pointage).
-- Idempotent : ne fait rien si la table existe deja.
-- Executee automatiquement par PointageSyncService avant la premiere
-- synchronisation ; peut aussi etre lancee a la main (SSMS / sqlcmd).

IF OBJECT_ID(N'dbo.pointages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.pointages
    (
        id         BIGINT IDENTITY(1,1) NOT NULL
                   CONSTRAINT PK_pointages PRIMARY KEY,
        telephone  NVARCHAR(30)  NOT NULL,
        lieu       NVARCHAR(255) NULL,
        date_heure NVARCHAR(30)  NOT NULL,
        type       NVARCHAR(30)  NULL,
        synced_at  DATETIME2     NOT NULL
                   CONSTRAINT DF_pointages_synced_at DEFAULT SYSDATETIME()
    );

    CREATE INDEX IX_pointages_date_heure
        ON dbo.pointages (date_heure, telephone);
END

using System.Diagnostics;
using System.Globalization;
using System.Net.Http.Json;
using System.Text.Json;

namespace app_commercial;

public static class PointageService
{
    private static readonly HttpClient _httpClient = new HttpClient(new SocketsHttpHandler
    {
        PooledConnectionLifetime = TimeSpan.FromSeconds(30),
        PooledConnectionIdleTimeout = TimeSpan.FromSeconds(15)
    })
    {
        Timeout = TimeSpan.FromSeconds(15)
    };

    private const string TursoDbUrl = "https://rh-mahefa.aws-us-west-2.turso.io/v2/pipeline";

    private const string TursoAuthToken = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3OTAyMjcxNjQsImlkIjoiMDFhMDZjMWQtZGQwMS03NGE1LWEwYmUtYjI0MGI4Njc1ZTI4Iiwia2lkIjoiRlFuZHY2cm0yQzY4SmpYMVB2VTNIcDh6ekN2eUdFRG1aUWpuMFRja0w5ayIsInJpZCI6IjdjNDY0ZDM0LTFhYzQtNGQ3MC1hZGY4LWE2ZGMzZWQ4OWNmYyJ9.--AaAuad2W4I_oJj-CQ79XzuqilIKS8CMtgDiV62I1qVQOw9klFEivliXs-8JiRvgNxvLh3fgtFD8Bojy3ieCQ";

    // Format unique pour toutes les dates enregistrées / lues.
    private const string FormatDate = "yyyy/MM/dd HH:mm:ss";

    private static readonly string OfflineQueuePath =
        Path.Combine(FileSystem.AppDataDirectory, "pointages_en_attente.json");

    private static readonly SemaphoreSlim _syncLock = new SemaphoreSlim(1, 1);       // une seule synchro à la fois
    private static readonly SemaphoreSlim _traitementLock = new SemaphoreSlim(1, 1); // un seul traitement de position à la fois
    private static readonly SemaphoreSlim _fileLock = new SemaphoreSlim(1, 1);       // accès au fichier de file d'attente

    // Distance minimale (mètres) pour considérer qu'on a changé de lieu.
    public const double DistanceMinChangementMetres = 80;

    // Délai minimal entre deux résolutions Nominatim (limite : 1 requête/seconde).
    private static readonly TimeSpan DelaiMinEntreTentatives = TimeSpan.FromSeconds(30);
    private static long _derniereTentativeMs = -1_000_000_000L;

    // --- Clés Preferences ---
    private const string PrefTelephone = "pointage_telephone";
    private const string PrefDernierLieu = "pointage_dernier_lieu";
    private const string PrefDerniereLat = "pointage_derniere_lat";
    private const string PrefDerniereLon = "pointage_derniere_lon";
    private const string PrefDerniereDateEntree = "pointage_derniere_date_entree";
    private const string PrefPositionEnAttente = "pointage_pos_en_attente";
    private const string PrefDerniereHeure = "pointage_derniere_heure";
    private const string PrefRecueLat = "pointage_recue_lat";
    private const string PrefRecueLon = "pointage_recue_lon";
    private const string PrefRecueTicks = "pointage_recue_ticks";

    // Plage horaire d'enregistrement : de 06:30 à 18:00 (inclus).
    private static readonly TimeSpan HeureDebut = new TimeSpan(6, 30, 0);
    private static readonly TimeSpan HeureFin = new TimeSpan(18, 0, 0);

    private static bool EstDansPlageHoraire(DateTime dt) =>
        dt.TimeOfDay >= HeureDebut && dt.TimeOfDay <= HeureFin;

    private static string MaintenantTexte() =>
        MaintenantFiable().ToString(FormatDate, CultureInfo.InvariantCulture);

    private static string AujourdHuiTexte() =>
        MaintenantFiable().ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);

    // ------------------------------------------------------------------
    //  Horloge fiable : insensible au changement de date/heure du téléphone
    //  Principe : on lit l'heure du serveur (Turso) quand on est en ligne, puis on
    //  y ajoute le temps écoulé mesuré par un compteur qui ne se règle pas
    //  (SystemClock.ElapsedRealtime sur Android). Changer l'heure du téléphone
    //  n'a donc aucun effet, même hors ligne. Un redémarrage hors ligne fait
    //  perdre la référence : on retombe alors sur l'heure du téléphone.
    // ------------------------------------------------------------------

    private const string PrefRefServeurTicks = "pointage_ref_serveur_ticks";
    private const string PrefRefElapsedMs = "pointage_ref_elapsed_ms";
    private const string PrefDernierElapsedVu = "pointage_dernier_elapsed_vu";
    private static readonly TimeSpan ValiditeReferenceHorloge = TimeSpan.FromMinutes(10);
    private static readonly SemaphoreSlim _horlogeLock = new SemaphoreSlim(1, 1);

    private static long TempsDepuisDemarrageMs()
    {
#if ANDROID
        return global::Android.OS.SystemClock.ElapsedRealtime(); // compte aussi la veille, non modifiable par l'utilisateur
#else
        return Environment.TickCount64;
#endif
    }

    private static bool TentativeTropRecente() =>
        TempsDepuisDemarrageMs() - _derniereTentativeMs < (long)DelaiMinEntreTentatives.TotalMilliseconds;

    private static void MarquerTentative() =>
        _derniereTentativeMs = TempsDepuisDemarrageMs();

    private static void InvaliderReferenceHorloge()
    {
        Preferences.Default.Remove(PrefRefServeurTicks);
        Preferences.Default.Remove(PrefRefElapsedMs);
    }

    /// <summary>Heure locale fiable (serveur + temps écoulé), ou heure du téléphone à défaut.</summary>
    private static DateTime MaintenantFiable()
    {
        long maintenantMs = TempsDepuisDemarrageMs();

        // Compteur en baisse = le téléphone a redémarré : la référence n'est plus valable.
        long dernierVu = Preferences.Default.Get(PrefDernierElapsedVu, 0L);
        if (maintenantMs < dernierVu)
            InvaliderReferenceHorloge();
        Preferences.Default.Set(PrefDernierElapsedVu, maintenantMs);

        long ticks = Preferences.Default.Get(PrefRefServeurTicks, 0L);
        long refMs = Preferences.Default.Get(PrefRefElapsedMs, -1L);

        if (ticks > 0 && refMs >= 0 && maintenantMs >= refMs)
        {
            var utc = new DateTime(ticks, DateTimeKind.Utc).AddMilliseconds(maintenantMs - refMs);
            return TimeZoneInfo.ConvertTimeFromUtc(utc, TimeZoneInfo.Local);
        }

        return DateTime.Now; // aucune référence fiable (jamais synchronisé, ou redémarrage hors ligne)
    }

    /// <summary>En ligne : relit l'heure du serveur si la référence a plus de 10 minutes.</summary>
    private static async Task SynchroniserHorlogeSiNecessaireAsync()
    {
        if (!EstConnecte)
            return;

        long maintenantMs = TempsDepuisDemarrageMs();
        long ticks = Preferences.Default.Get(PrefRefServeurTicks, 0L);
        long refMs = Preferences.Default.Get(PrefRefElapsedMs, -1L);

        bool referenceValide = ticks > 0 && refMs >= 0 && maintenantMs >= refMs;
        if (referenceValide && maintenantMs - refMs < ValiditeReferenceHorloge.TotalMilliseconds)
            return;

        if (!await _horlogeLock.WaitAsync(0))
            return;

        try
        {
            long avant = TempsDepuisDemarrageMs();
            string json = await ExecuterTursoAsync("SELECT strftime('%Y-%m-%d %H:%M:%S','now')");
            long apres = TempsDepuisDemarrageMs();

            using var doc = JsonDocument.Parse(json);
            string? texte = doc.RootElement
                .GetProperty("results")[0]
                .GetProperty("response")
                .GetProperty("result")
                .GetProperty("rows")[0][0]
                .GetProperty("value")
                .GetString();

            if (DateTime.TryParseExact(texte, "yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture,
                    DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out var utc))
            {
                Preferences.Default.Set(PrefRefServeurTicks, utc.Ticks);
                Preferences.Default.Set(PrefRefElapsedMs, (avant + apres) / 2); // milieu de la requête

                double ecartSec = Math.Abs((DateTime.UtcNow - utc).TotalSeconds);
                if (ecartSec > 120)
                    Debug.WriteLine($"[Pointage] Horloge du téléphone décalée de {ecartSec:F0} s : heure du serveur utilisée.");
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[Pointage] Synchro horloge impossible : {ex.Message}");
        }
        finally
        {
            _horlogeLock.Release();
        }
    }

    public static string? NumeroTelephone
    {
        get => Preferences.Default.Get<string?>(PrefTelephone, null);
        set
        {
            if (value == null) Preferences.Default.Remove(PrefTelephone);
            else Preferences.Default.Set(PrefTelephone, value);
        }
    }

    public static string? DernierLieuOuvert
    {
        get => Preferences.Default.Get<string?>(PrefDernierLieu, null);
        private set
        {
            if (value == null) Preferences.Default.Remove(PrefDernierLieu);
            else Preferences.Default.Set(PrefDernierLieu, value);
        }
    }

    /// <summary>
    /// Heure réelle (heure du téléphone) du dernier pointage enregistré.
    /// À afficher avec .ToString("HH:mm:ss") : c'est une heure, pas une durée.
    /// </summary>
    public static DateTime? DerniereHeurePointage
    {
        get
        {
            string? s = Preferences.Default.Get<string?>(PrefDerniereHeure, null);
            if (string.IsNullOrWhiteSpace(s)) return null;

            return DateTime.TryParseExact(s, FormatDate, CultureInfo.InvariantCulture,
                DateTimeStyles.None, out var d) ? d : null;
        }
    }

    private static (double lat, double lon)? DernierePosition
    {
        get
        {
            double lat = Preferences.Default.Get(PrefDerniereLat, double.NaN);
            double lon = Preferences.Default.Get(PrefDerniereLon, double.NaN);
            if (double.IsNaN(lat) || double.IsNaN(lon)) return null;
            return (lat, lon);
        }
        set
        {
            if (value == null)
            {
                Preferences.Default.Remove(PrefDerniereLat);
                Preferences.Default.Remove(PrefDerniereLon);
            }
            else
            {
                Preferences.Default.Set(PrefDerniereLat, value.Value.lat);
                Preferences.Default.Set(PrefDerniereLon, value.Value.lon);
            }
        }
    }

    // Dernière position GPS réellement reçue du service (avec l'heure de réception).
    private static void MemoriserPositionRecue(double lat, double lon)
    {
        Preferences.Default.Set(PrefRecueLat, lat);
        Preferences.Default.Set(PrefRecueLon, lon);
        Preferences.Default.Set(PrefRecueTicks, TempsDepuisDemarrageMs());
    }

    // Une fois le pointage enregistré, on vide la position mémorisée : la suivante sera obligatoirement neuve.
    private static void EffacerPositionRecue()
    {
        Preferences.Default.Remove(PrefRecueLat);
        Preferences.Default.Remove(PrefRecueLon);
        Preferences.Default.Remove(PrefRecueTicks);
    }

    private static (double lat, double lon)? PositionRecueRecente()
    {
        long vuMs = Preferences.Default.Get(PrefRecueTicks, -1L);
        if (vuMs < 0) return null;
        long ageMs = TempsDepuisDemarrageMs() - vuMs;
        if (ageMs < 0 || ageMs > (long)TimeSpan.FromMinutes(10).TotalMilliseconds) return null; // ancienne, ou redémarrage

        double lat = Preferences.Default.Get(PrefRecueLat, double.NaN);
        double lon = Preferences.Default.Get(PrefRecueLon, double.NaN);
        if (double.IsNaN(lat) || double.IsNaN(lon)) return null;
        return (lat, lon);
    }

    private static bool EstConnecte =>
        Connectivity.Current.NetworkAccess is NetworkAccess.Internet or NetworkAccess.ConstrainedInternet;

    /// <summary>
    /// Un lieu est valide s'il n'est ni vide, ni "Lieu inconnu", ni "Lieu GPS (...)".
    /// </summary>
    private static bool EstLieuValide(string? lieu)
    {
        if (string.IsNullOrWhiteSpace(lieu))
            return false;

        string l = lieu.Trim().ToLowerInvariant();
        return !l.Contains("lieu inconnu") && !l.Contains("lieu gps");
    }

    private static double DistanceMetres(double lat1, double lon1, double lat2, double lon2)
    {
        double R = 6371000;
        double dLat = (lat2 - lat1) * Math.PI / 180.0;
        double dLon = (lon2 - lon1) * Math.PI / 180.0;
        double a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2) +
                   Math.Cos(lat1 * Math.PI / 180.0) * Math.Cos(lat2 * Math.PI / 180.0) *
                   Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        double c = 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
        return R * c;
    }

    // ------------------------------------------------------------------
    //  Position hors ligne : mémorisée puis retraitée au retour du réseau
    // ------------------------------------------------------------------

    public class PositionEnAttente
    {
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public string DateHeure { get; set; } = "";
        public int Essais { get; set; }
    }

    private static readonly string PositionsEnAttentePath =
        Path.Combine(FileSystem.AppDataDirectory, "positions_en_attente.json");

    // Accès protégé par _traitementLock (toujours appelé sous ce verrou).
    private static List<PositionEnAttente> ChargerPositionsEnAttente()
    {
        try
        {
            if (!File.Exists(PositionsEnAttentePath))
                return new List<PositionEnAttente>();
            return JsonSerializer.Deserialize<List<PositionEnAttente>>(File.ReadAllText(PositionsEnAttentePath))
                   ?? new List<PositionEnAttente>();
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[Pointage] Erreur lecture positions en attente : {ex.Message}");
            return new List<PositionEnAttente>();
        }
    }

    private static void SauvegarderPositionsEnAttente(List<PositionEnAttente> liste)
    {
        try { File.WriteAllText(PositionsEnAttentePath, JsonSerializer.Serialize(liste)); }
        catch (Exception ex) { Debug.WriteLine($"[Pointage] Erreur sauvegarde positions en attente : {ex.Message}"); }
    }

    /// <summary>
    /// Hors ligne : ajoute la position À LA LISTE (avec l'heure réelle), sans écraser
    /// les précédentes. Ignorée si on n'a pas bougé de plus de 80 m depuis la dernière.
    /// </summary>
    private static void AjouterPositionEnAttente(double lat, double lon)
    {
        var liste = ChargerPositionsEnAttente();

        (double lat, double lon)? reference = null;
        if (liste.Count > 0)
            reference = (liste[^1].Latitude, liste[^1].Longitude);
        else if (DernierLieuOuvert != null)
            reference = DernierePosition;

        if (reference.HasValue &&
            DistanceMetres(reference.Value.lat, reference.Value.lon, lat, lon) < DistanceMinChangementMetres)
            return;

        liste.Add(new PositionEnAttente { Latitude = lat, Longitude = lon, DateHeure = MaintenantTexte() });
        SauvegarderPositionsEnAttente(liste);
        Debug.WriteLine($"[Pointage] Hors ligne : position mise en attente ({liste.Count} en file).");
    }

    /// <summary>
    /// Au retour du réseau : résout chaque position en attente, dans l'ordre,
    /// et enregistre Sortie/Entrée avec l'heure où la position a été captée.
    /// À appeler sous _traitementLock.
    /// </summary>
    private static async Task TraiterPositionsEnAttenteInterneAsync()
    {
        string? telephone = NumeroTelephone;
        if (string.IsNullOrWhiteSpace(telephone))
            return;

        var liste = ChargerPositionsEnAttente();
        if (liste.Count == 0)
            return;

        var restantes = new List<PositionEnAttente>();

        for (int i = 0; i < liste.Count; i++)
        {
            var p = liste[i];

            if (!EstConnecte)
            {
                restantes.AddRange(liste.Skip(i));
                break;
            }

            bool dateValide = DateTime.TryParseExact(p.DateHeure, FormatDate, CultureInfo.InvariantCulture,
                DateTimeStyles.None, out var dtPos);

            if (dateValide && !EstDansPlageHoraire(dtPos))
                continue; // captée hors plage horaire : on la jette

            string lieu = await RecupererNomLieuAsync(p.Latitude, p.Longitude);
            await Task.Delay(1100); // limite Nominatim : 1 requête/seconde

            if (!EstLieuValide(lieu))
            {
                p.Essais++;
                if (p.Essais < 3)
                    restantes.Add(p); // on réessaiera plus tard
                else
                    Debug.WriteLine($"[Pointage] Position {p.DateHeure} abandonnée (lieu introuvable).");
                continue;
            }

            string? ancienLieu = DernierLieuOuvert;

            if (ancienLieu != lieu)
            {
                if (ancienLieu != null)
                    await EnregistrerOuMettreEnFileAsync(telephone, "Sortie", ancienLieu, p.DateHeure, p.Latitude, p.Longitude, lieuResolu: true);

                await EnregistrerOuMettreEnFileAsync(telephone, "Entrée", lieu, p.DateHeure, p.Latitude, p.Longitude, lieuResolu: true);

                DernierLieuOuvert = lieu;
                if (dateValide && dtPos.Date == MaintenantFiable().Date)
                    MarquerEntreeAujourdHui(); // seulement si la position date d'aujourd'hui
                Preferences.Default.Set(PrefDerniereHeure, p.DateHeure);
            }

            DernierePosition = (p.Latitude, p.Longitude);
        }

        SauvegarderPositionsEnAttente(restantes);
        MarquerTentative();
    }

    private static async Task TraiterPositionsEnAttenteAsync()
    {
        await _traitementLock.WaitAsync();
        try { await TraiterPositionsEnAttenteInterneAsync(); }
        finally { _traitementLock.Release(); }
    }

    // ------------------------------------------------------------------
    //  Décision Entrée / Sortie
    // ------------------------------------------------------------------

    /// <summary>
    /// Point d'entrée appelé à chaque nouvelle position GPS (UI ou service).
    /// </summary>
    public static async Task TraiterNouvellePositionAsync(double latitude, double longitude)
    {
        MemoriserPositionRecue(latitude, longitude);

        await _traitementLock.WaitAsync();
        try
        {
            await TraiterInterneAsync(latitude, longitude);
        }
        finally
        {
            _traitementLock.Release();
        }

        // Aucune Entrée aujourd'hui (appareil immobile) : on la crée avec la position ACTUELLE.
        await VerifierPointageDuJourAsync(latitude, longitude);
    }

    private static async Task TraiterInterneAsync(double latitude, double longitude)
    {
        string? telephone = NumeroTelephone;
        if (string.IsNullOrWhiteSpace(telephone))
            return;

        // Heure du serveur (si en ligne) avant tout calcul de date/heure.
        await SynchroniserHorlogeSiNecessaireAsync();

        // Hors de la plage 06:30-18:00 : on n'enregistre rien.
        if (!EstDansPlageHoraire(MaintenantFiable()))
            return;

        // Hors ligne : on ajoute la position à la liste (heure réelle) et on s'arrête là.
        if (!EstConnecte)
        {
            AjouterPositionEnAttente(latitude, longitude);
            return;
        }

        // En ligne : d'abord traiter, dans l'ordre, les positions captées hors ligne.
        await TraiterPositionsEnAttenteInterneAsync();

        string? ancienLieu = DernierLieuOuvert;
        var derniere = DernierePosition;

        // Pas de déplacement significatif ET un lieu est déjà ouvert : rien à faire.
        if (derniere.HasValue && ancienLieu != null)
        {
            double distance = DistanceMetres(derniere.Value.lat, derniere.Value.lon, latitude, longitude);
            if (distance < DistanceMinChangementMetres)
                return;
        }

        // Limite de débit Nominatim.
        if (TentativeTropRecente())
            return;
        MarquerTentative();

        string nouveauLieu = await RecupererNomLieuAsync(latitude, longitude);

        // Lieu invalide : on NE met PAS à jour DernierePosition, ainsi le
        // prochain point GPS réessaiera au lieu de rester bloqué.
        if (!EstLieuValide(nouveauLieu))
        {
            Debug.WriteLine($"[Pointage] Lieu invalide ({nouveauLieu}), nouvel essai au prochain point.");
            return;
        }

        DernierePosition = (latitude, longitude);

        if (ancienLieu != null && ancienLieu == nouveauLieu)
            return; // toujours au même endroit résolu

        string dateHeure = MaintenantTexte();

        if (ancienLieu != null)
            await EnregistrerOuMettreEnFileAsync(telephone, "Sortie", ancienLieu, dateHeure, latitude, longitude, lieuResolu: true);

        await EnregistrerOuMettreEnFileAsync(telephone, "Entrée", nouveauLieu, dateHeure, latitude, longitude, lieuResolu: true);

        DernierLieuOuvert = nouveauLieu;
        MarquerEntreeAujourdHui();
        Preferences.Default.Set(PrefDerniereHeure, dateHeure);
        EffacerPositionRecue();
    }

    public static void MarquerEntreeAujourdHui()
    {
        Preferences.Default.Set(PrefDerniereDateEntree, AujourdHuiTexte());
    }

    /// <summary>
    /// Si aucune Entrée n'a été enregistrée aujourd'hui (appareil immobile),
    /// en enregistre une avec la dernière position connue.
    /// </summary>
    public static async Task VerifierPointageDuJourAsync(double? latitudeSecours = null, double? longitudeSecours = null)
    {
        await SynchroniserHorlogeSiNecessaireAsync();

        if (!EstDansPlageHoraire(MaintenantFiable()))
            return;

        string aujourdHui = AujourdHuiTexte();
        if (Preferences.Default.Get<string?>(PrefDerniereDateEntree, null) == aujourdHui)
            return;

        await _traitementLock.WaitAsync();
        try
        {
            // Revérification sous verrou (un autre traitement a pu enregistrer entre-temps).
            if (Preferences.Default.Get<string?>(PrefDerniereDateEntree, null) == aujourdHui)
                return;

            string? telephone = NumeroTelephone;
            if (string.IsNullOrWhiteSpace(telephone))
                return;

            // Position ACTUELLE uniquement : celle passée en paramètre, sinon la dernière
            // position GPS reçue il y a moins de 10 min. Jamais l'ancienne DernierePosition
            // (celle d'hier), qui enregistrait le mauvais lieu.
            double lat, lon;
            if (latitudeSecours.HasValue && longitudeSecours.HasValue)
            {
                lat = latitudeSecours.Value;
                lon = longitudeSecours.Value;
            }
            else
            {
                var recue = PositionRecueRecente();
                if (!recue.HasValue)
                    return; // pas de position actuelle fiable : on attend la prochaine
                (lat, lon) = recue.Value;
            }

            if (!EstConnecte)
                return; // on réessaiera au prochain passage du minuteur

            if (TentativeTropRecente())
                return;
            MarquerTentative();

            string lieu = await RecupererNomLieuAsync(lat, lon);
            if (!EstLieuValide(lieu))
                return;

            string dateHeure = MaintenantTexte();
            await EnregistrerOuMettreEnFileAsync(telephone, "Entrée", lieu, dateHeure, lat, lon, lieuResolu: true);

            DernierLieuOuvert = lieu;
            DernierePosition = (lat, lon);
            MarquerEntreeAujourdHui();
            Preferences.Default.Set(PrefDerniereHeure, dateHeure);
            EffacerPositionRecue();
        }
        finally
        {
            _traitementLock.Release();
        }
    }

    private static async Task EnregistrerOuMettreEnFileAsync(string telephone, string type, string lieu,
        string dateHeure, double lat, double lon, bool lieuResolu)
    {
        if (EstConnecte)
        {
            try
            {
                await EnregistrerDansTurso(telephone, type, lieu, dateHeure);
                return;
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[Pointage] Echec envoi direct, mise en file : {ex.Message}");
            }
        }

        await AjouterAFileAttenteAsync(telephone, type, lieu, dateHeure, lat, lon, lieuResolu);
    }

    // ------------------------------------------------------------------
    //  Nominatim
    // ------------------------------------------------------------------

    public static async Task<string> RecupererNomLieuAsync(double latitude, double longitude)
    {
        try
        {
            string url =
                "https://nominatim.openstreetmap.org/reverse" +
                $"?lat={latitude.ToString(CultureInfo.InvariantCulture)}" +
                $"&lon={longitude.ToString(CultureInfo.InvariantCulture)}" +
                "&format=json&addressdetails=1&zoom=18";

            using var req = new HttpRequestMessage(HttpMethod.Get, url);
            req.Headers.UserAgent.ParseAdd("app_commercial_frontend/1.0");

            using var response = await _httpClient.SendAsync(req);
            if (!response.IsSuccessStatusCode)
            {
                Debug.WriteLine($"[Pointage] Nominatim HTTP {(int)response.StatusCode}");
                return "Lieu inconnu";
            }

            string json = await response.Content.ReadAsStringAsync();
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;

            if (root.TryGetProperty("address", out var address))
            {
                // Ordre : commune / ville d'abord. Les noms très locaux (hameau, quartier, village)
                // viennent APRÈS : Nominatim les rattache parfois à un point situé à plusieurs km
                // (ex. "Andranovao" renvoyé alors qu'on est à Ambohimangakely).
                string[] champs =
                {
                    "municipality", "city", "town", "village",
                    "suburb", "city_district", "quarter", "neighbourhood", "hamlet", "county"
                };

                foreach (string champ in champs)
                {
                    if (address.TryGetProperty(champ, out var valeur))
                    {
                        string? nom = valeur.GetString();
                        if (!string.IsNullOrWhiteSpace(nom))
                            return nom;
                    }
                }
            }

            // Dernier recours : premier segment de display_name.
            if (root.TryGetProperty("display_name", out var dn))
            {
                string? complet = dn.GetString();
                if (!string.IsNullOrWhiteSpace(complet))
                {
                    string premier = complet.Split(',')[0].Trim();
                    if (!string.IsNullOrWhiteSpace(premier))
                        return premier;
                }
            }

            return "Lieu inconnu";
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[Pointage] Erreur Nominatim : {ex.Message}");
            return "Lieu inconnu";
        }
    }

    // ------------------------------------------------------------------
    //  Turso
    // ------------------------------------------------------------------

    private static async Task<string> ExecuterTursoAsync(string sql, params string[] args)
    {
        var payload = new
        {
            requests = new object[]
            {
                new
                {
                    type = "execute",
                    stmt = new
                    {
                        sql,
                        args = args.Select(a => (object)new { type = "text", value = a }).ToArray()
                    }
                },
                new { type = "close" }
            }
        };

        using var req = new HttpRequestMessage(HttpMethod.Post, TursoDbUrl)
        {
            Content = JsonContent.Create(payload)
        };
        req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", TursoAuthToken);

        using var response = await _httpClient.SendAsync(req);
        string body = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
            throw new Exception($"Erreur Turso ({response.StatusCode}) : {body}");

        return body;
    }

    public static async Task EnregistrerDansTurso(string telephone, string type, string lieu, string dateHeure)
    {
        await ExecuterTursoAsync(
            "INSERT INTO pointages (telephone, type, lieu, date_heure) VALUES (?, ?, ?, ?)",
            telephone, type, lieu, dateHeure);
    }

    public static async Task<(string? type, string? lieu, DateTime? dateHeure)> RecupererDernierPointageServeurAsync(string telephone)
    {
        try
        {
            string json = await ExecuterTursoAsync(
                "SELECT type, lieu, date_heure FROM pointages WHERE telephone = ? ORDER BY id DESC LIMIT 1",
                telephone);

            using var doc = JsonDocument.Parse(json);

            var rows = doc.RootElement
                .GetProperty("results")[0]
                .GetProperty("response")
                .GetProperty("result")
                .GetProperty("rows");

            if (rows.GetArrayLength() == 0)
                return (null, null, null);

            string type = rows[0][0].GetProperty("value").GetString() ?? "";
            string lieu = rows[0][1].GetProperty("value").GetString() ?? "";
            string dateStr = rows[0][2].GetProperty("value").GetString() ?? "";

            // Lecture stricte du format enregistré ; repli sur TryParse ; null si échec
            // (plutôt que 0001-01-01 00:00:00 qui donnait des heures aberrantes).
            DateTime? date = null;
            if (DateTime.TryParseExact(dateStr, FormatDate, CultureInfo.InvariantCulture,
                    DateTimeStyles.None, out var d1))
                date = d1;
            else if (DateTime.TryParse(dateStr, CultureInfo.InvariantCulture,
                    DateTimeStyles.None, out var d2))
                date = d2;

            return (type, lieu, date);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[Pointage] Erreur lecture dernier pointage serveur : {ex.Message}");
            return (null, null, null);
        }
    }

    // ------------------------------------------------------------------
    //  File d'attente hors ligne
    // ------------------------------------------------------------------

    public class PointageEnAttente
    {
        public string Telephone { get; set; } = "";
        public string Type { get; set; } = "";
        public string Lieu { get; set; } = "";
        public string DateHeure { get; set; } = "";
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public bool LieuResolu { get; set; }

        internal string Cle =>
            $"{Telephone}|{Type}|{DateHeure}|{Lieu}|{Latitude.ToString(CultureInfo.InvariantCulture)}|{Longitude.ToString(CultureInfo.InvariantCulture)}";
    }

    public static async Task<List<PointageEnAttente>> ChargerFileAttenteAsync()
    {
        try
        {
            if (!File.Exists(OfflineQueuePath))
                return new List<PointageEnAttente>();

            string json = await File.ReadAllTextAsync(OfflineQueuePath);
            return JsonSerializer.Deserialize<List<PointageEnAttente>>(json) ?? new List<PointageEnAttente>();
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[Pointage] Erreur lecture file d'attente : {ex.Message}");
            return new List<PointageEnAttente>();
        }
    }

    public static async Task SauvegarderFileAttenteAsync(List<PointageEnAttente> file)
    {
        try
        {
            string json = JsonSerializer.Serialize(file);
            await File.WriteAllTextAsync(OfflineQueuePath, json);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[Pointage] Erreur sauvegarde file d'attente : {ex.Message}");
        }
    }

    public static async Task AjouterAFileAttenteAsync(string telephone, string type, string lieu, string dateHeure,
        double latitude, double longitude, bool lieuResolu)
    {
        await _fileLock.WaitAsync();
        try
        {
            var file = await ChargerFileAttenteAsync();
            file.Add(new PointageEnAttente
            {
                Telephone = telephone,
                Type = type,
                Lieu = lieu,
                DateHeure = dateHeure,
                Latitude = latitude,
                Longitude = longitude,
                LieuResolu = lieuResolu
            });
            await SauvegarderFileAttenteAsync(file);
        }
        finally
        {
            _fileLock.Release();
        }
    }

    public static async Task SynchroniserFileAttenteAsync()
    {
        if (!EstConnecte)
            return;

        if (!await _syncLock.WaitAsync(0))
            return;

        try
        {
            // 1) Retraiter la position captée hors ligne, s'il y en a une.
            await TraiterPositionsEnAttenteAsync();

            // 2) Envoyer les pointages en file.
            List<PointageEnAttente> instantane;
            await _fileLock.WaitAsync();
            try { instantane = await ChargerFileAttenteAsync(); }
            finally { _fileLock.Release(); }

            if (instantane.Count == 0)
                return;

            var traites = new List<string>(); // pointages envoyés OU abandonnés

            foreach (var p in instantane.OrderBy(p => p.DateHeure, StringComparer.Ordinal))
            {
                try
                {
                    string lieuFinal = p.Lieu;
                    if (!p.LieuResolu)
                        lieuFinal = await RecupererNomLieuAsync(p.Latitude, p.Longitude);

                    if (!EstLieuValide(lieuFinal))
                    {
                        Debug.WriteLine($"[Pointage] {p.DateHeure} ignoré (lieu invalide : {lieuFinal})");
                        traites.Add(p.Cle);
                        continue;
                    }

                    await EnregistrerDansTurso(p.Telephone, p.Type, lieuFinal, p.DateHeure);
                    traites.Add(p.Cle);
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"[Pointage] Échec synchro {p.DateHeure} : {ex.Message}");
                    // reste dans la file
                }
            }

            // 3) Retirer uniquement les éléments traités, en rechargeant la file
            //    (des pointages ont pu être ajoutés pendant la synchro).
            await _fileLock.WaitAsync();
            try
            {
                var courante = await ChargerFileAttenteAsync();
                foreach (string cle in traites)
                {
                    int idx = courante.FindIndex(x => x.Cle == cle);
                    if (idx >= 0) courante.RemoveAt(idx);
                }
                await SauvegarderFileAttenteAsync(courante);
            }
            finally
            {
                _fileLock.Release();
            }
        }
        finally
        {
            _syncLock.Release();
        }
    }
}
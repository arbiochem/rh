using Microsoft.Maui.Devices.Sensors;
using Microsoft.Maui.Networking;
using System.Diagnostics;
using System.Net.Http.Json;
using System.Text.Json;

namespace app_commercial;

public partial class MainPage : ContentPage
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
    private const string TursoAuthToken = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3ODg1OTQ3OTQsImlkIjoiMDFhMDZjMWQtZGQwMS03NGE1LWEwYmUtYjI0MGI4Njc1ZTI4Iiwia2lkIjoiRlFuZHY2cm0yQzY4SmpYMVB2VTNIcDh6ekN2eUdFRG1aUWpuMFRja0w5ayIsInJpZCI6IjdjNDY0ZDM0LTFhYzQtNGQ3MC1hZGY4LWE2ZGMzZWQ4OWNmYyJ9.D4hAwXBm96zNYvE-UvS2ANNkunkFLDKGk88ndb4oskI1i4J6SYeIMtW9WNTWkSS3RxOljon0Gx7HckbqGvXSDQ";

    // Fichier local servant de file d'attente pour les pointages non encore synchronisés
    private static readonly string OfflineQueuePath =
        Path.Combine(FileSystem.AppDataDirectory, "pointages_en_attente.json");

    // Empêche deux synchronisations simultanées
    private static readonly SemaphoreSlim _syncLock = new SemaphoreSlim(1, 1);

    // Seuil de précision GPS acceptable, en mètres. En dessous de ce seuil,
    // on considère la position fiable pour distinguer deux quartiers/communes proches.
    private const double PrecisionMaxAcceptableMetres = 50;

    // Lieu actuellement "ouvert" (Entrée sans Sortie correspondante), y compris en tenant compte de la file locale
    private string? _lieuOuvert = null;

    public MainPage()
    {
        InitializeComponent();
        PhoneEntry.TextChanged += async (s, e) => await RafraichirEtatBoutons();

        // Dès que la connectivité revient, on tente de synchroniser la file d'attente
        Connectivity.Current.ConnectivityChanged += async (s, e) =>
        {
            if (e.NetworkAccess == NetworkAccess.Internet)
            {
                await SynchroniserFileAttenteAsync();
                MainThread.BeginInvokeOnMainThread(async () => await RafraichirEtatBoutons());
            }
        };

        // Tentative de synchronisation au démarrage si déjà connecté
        _ = SynchroniserFileAttenteAsync();
    }

    private async void OnEntreeClicked(object sender, EventArgs e) => await EnregistrerPointage("Entrée");

    private async void OnSortieClicked(object sender, EventArgs e) => await EnregistrerPointage("Sortie");

    private bool EstConnecte => Connectivity.Current.NetworkAccess == NetworkAccess.Internet;

    private async Task EnregistrerPointage(string type)
    {
        if (string.IsNullOrWhiteSpace(PhoneEntry.Text))
        {
            await DisplayAlert("Erreur", "Veuillez saisir un numéro de téléphone.", "OK");
            return;
        }

        EntreeBtn.IsEnabled = false;
        SortieBtn.IsEnabled = false;

        try
        {
            LoadingIndicator.IsVisible = true;
            LoadingIndicator.IsRunning = true;
            StatusLabel.Text = "Récupération de la position GPS...";

            // Le GPS fonctionne sans connexion internet
            var location = await ObtenirPositionGps();
            if (location == null)
            {
                StatusLabel.Text = "Impossible d'obtenir la position GPS.";
                return;
            }

            bool enLigne = EstConnecte;
            string lieu;

            if (enLigne)
            {
                StatusLabel.Text = "Recherche du lieu...";
                lieu = await RecupererNomLieuAsync(location.Latitude, location.Longitude);
            }
            else
            {
                // Hors ligne : impossible d'interroger Nominatim. On utilise les coordonnées
                // arrondies comme identifiant de lieu provisoire, pour que la logique Entrée/Sortie
                // reste cohérente tant que la synchronisation n'a pas eu lieu.
                lieu = $"Lieu GPS ({location.Latitude:F4}, {location.Longitude:F4})";
            }

            // Règle métier : une Entrée sans Sortie correspondante bloque une nouvelle Entrée pour ce lieu
            if (type == "Entrée" && _lieuOuvert == lieu)
            {
                StatusLabel.Text = $"Entrée déjà enregistrée pour {lieu}. Veuillez faire Sortie d'abord.";
                return;
            }

            string dateHeure = DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss");

            if (enLigne)
            {
                try
                {
                    StatusLabel.Text = "Enregistrement en base de données...";
                    await EnregistrerDansTurso(PhoneEntry.Text, type, lieu, dateHeure);
                    StatusLabel.Text = $"{type} enregistrée à {DateTime.Now:HH:mm:ss} ({lieu})";
                }
                catch (Exception)
                {
                    // La requête a échoué malgré la connexion détectée (ex : serveur injoignable) :
                    // on bascule sur la file d'attente locale plutôt que de perdre le pointage.
                    await AjouterAFileAttenteAsync(PhoneEntry.Text, type, lieu, dateHeure,
                        location.Latitude, location.Longitude, lieuResolu: true);
                    StatusLabel.Text = $"{type} enregistrée hors ligne ({lieu}) — sera synchronisée.";
                }
            }
            else
            {
                await AjouterAFileAttenteAsync(PhoneEntry.Text, type, lieu, dateHeure,
                    location.Latitude, location.Longitude, lieuResolu: false);
                StatusLabel.Text = $"{type} enregistrée hors ligne ({lieu}) — sera synchronisée dès la reconnexion.";
            }

            // Mise à jour de l'état local (visuel Entrée/Sortie)
            _lieuOuvert = (type == "Entrée") ? lieu : null;
        }
        catch (Exception ex)
        {
            string detail = ex.InnerException != null
                ? $"{ex.GetType().Name}: {ex.Message} | Inner: {ex.InnerException.Message}"
                : $"{ex.GetType().Name}: {ex.Message}";
            StatusLabel.Text = "Erreur : " + detail;
            Debug.WriteLine(detail);
        }
        finally
        {
            LoadingIndicator.IsVisible = false;
            LoadingIndicator.IsRunning = false;
            AppliquerEtatBoutons();
        }
    }

    // Ajuste visuellement les boutons selon _lieuOuvert
    private void AppliquerEtatBoutons()
    {
        bool entreeOuverte = _lieuOuvert != null;
        EntreeBtn.IsEnabled = !entreeOuverte;
        SortieBtn.IsEnabled = entreeOuverte;
    }

    // Interroge Turso (si en ligne) ET la file d'attente locale pour déterminer l'état réel
    private async Task RafraichirEtatBoutons()
    {
        if (string.IsNullOrWhiteSpace(PhoneEntry.Text))
        {
            _lieuOuvert = null;
            AppliquerEtatBoutons();
            return;
        }

        try
        {
            string? dernierType = null;
            string? dernierLieu = null;
            DateTime dernierHorodatage = DateTime.MinValue;

            // 1) Dernier pointage connu côté serveur (si en ligne)
            if (EstConnecte)
            {
                var (typeServeur, lieuServeur, dateServeur) = await RecupererDernierPointageServeurAsync(PhoneEntry.Text);
                if (typeServeur != null && dateServeur.HasValue)
                {
                    dernierType = typeServeur;
                    dernierLieu = lieuServeur;
                    dernierHorodatage = dateServeur.Value;
                }
            }

            // 2) Dernier pointage en attente localement pour ce téléphone (peut être plus récent)
            var file = await ChargerFileAttenteAsync();
            var dernierLocal = file
                .Where(p => p.Telephone == PhoneEntry.Text)
                .OrderByDescending(p => p.DateHeure)
                .FirstOrDefault();

            if (dernierLocal != null && DateTime.TryParse(dernierLocal.DateHeure, out var dateLocale))
            {
                if (dateLocale >= dernierHorodatage)
                {
                    dernierType = dernierLocal.Type;
                    dernierLieu = dernierLocal.Lieu;
                }
            }

            _lieuOuvert = (dernierType == "Entrée") ? dernierLieu : null;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Erreur vérification état : {ex.Message}");
        }
        finally
        {
            AppliquerEtatBoutons();
        }
    }

    private async Task<(string? type, string? lieu, DateTime? dateHeure)> RecupererDernierPointageServeurAsync(string telephone)
    {
        try
        {
            _httpClient.DefaultRequestHeaders.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", TursoAuthToken);

            var payload = new
            {
                requests = new object[]
                {
                    new
                    {
                        type = "execute",
                        stmt = new
                        {
                            sql = "SELECT type, lieu, date_heure FROM pointages WHERE telephone = ? ORDER BY id DESC LIMIT 1",
                            args = new object[]
                            {
                                new { type = "text", value = telephone }
                            }
                        }
                    },
                    new { type = "close" }
                }
            };

            var response = await _httpClient.PostAsJsonAsync(TursoDbUrl, payload);
            if (!response.IsSuccessStatusCode)
                return (null, null, null);

            string json = await response.Content.ReadAsStringAsync();
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
            DateTime.TryParse(dateStr, out var date);

            return (type, lieu, date);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Erreur lecture dernier pointage serveur : {ex.Message}");
            return (null, null, null);
        }
    }

    // ---------------------------------------------------------------------
    // Capture GPS avec gestion de la précision (version corrigée)
    // ---------------------------------------------------------------------
    //
    // Hors connexion, le GPS n'a pas d'assistance réseau (A-GPS) : le premier
    // "fix" peut être imprécis de plusieurs centaines de mètres, voire plus.
    // Comme cette position est stockée telle quelle dans la file d'attente
    // et réutilisée sans modification lors de la synchronisation, une position
    // imprécise se traduit directement par un mauvais lieu résolu (ex :
    // "Ambohimangakely" au lieu de "Talatamaty"). On essaie donc d'obtenir
    // une position suffisamment précise avant de l'accepter, avec plus de
    // temps et plusieurs tentatives quand on est hors ligne.
    private async Task<Location?> ObtenirPositionGps()
    {
        var status = await Permissions.RequestAsync<Permissions.LocationWhenInUse>();
        if (status != PermissionStatus.Granted)
        {
            await DisplayAlert("Permission refusée", "L'accès à la position est nécessaire.", "OK");
            return null;
        }

        bool horsLigne = !EstConnecte;
        var timeoutParTentative = horsLigne ? TimeSpan.FromSeconds(20) : TimeSpan.FromSeconds(10);
        int nombreTentatives = horsLigne ? 3 : 1;

        Location? meilleureLocation = null;

        for (int tentative = 0; tentative < nombreTentatives; tentative++)
        {
            try
            {
                var request = new GeolocationRequest(GeolocationAccuracy.Best, timeoutParTentative);
                var location = await Geolocation.Default.GetLocationAsync(request);

                if (location == null)
                    continue;

                // On garde la position la plus précise obtenue jusqu'ici
                if (meilleureLocation == null ||
                    (location.Accuracy.HasValue &&
                     (!meilleureLocation.Accuracy.HasValue || location.Accuracy < meilleureLocation.Accuracy)))
                {
                    meilleureLocation = location;
                }

                // Si la précision est déjà suffisante, inutile de continuer à essayer
                if (meilleureLocation?.Accuracy.HasValue == true &&
                    meilleureLocation.Accuracy <= PrecisionMaxAcceptableMetres)
                {
                    break;
                }
            }
            catch (Exception ex)
            {
                // On continue les tentatives suivantes plutôt que d'abandonner tout de suite
                Debug.WriteLine($"Tentative GPS {tentative + 1} échouée : {ex.Message}");
            }
        }

        if (meilleureLocation == null)
        {
            await DisplayAlert("Erreur GPS", "Impossible d'obtenir une position GPS.", "OK");
            return null;
        }

        if (meilleureLocation.Accuracy.HasValue && meilleureLocation.Accuracy > PrecisionMaxAcceptableMetres)
        {
            // La position reste imprécise malgré les tentatives : on l'utilise quand même
            // (mieux vaut une position approximative qu'aucun pointage), mais on le journalise.
            Debug.WriteLine($"Position obtenue avec une précision faible : {meilleureLocation.Accuracy} m");
        }

        return meilleureLocation;
    }

    private async Task<string> RecupererNomLieuAsync(double latitude, double longitude)
    {
        try
        {
            if (!_httpClient.DefaultRequestHeaders.UserAgent.Any())
            {
                _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("app_commercial_frontend/1.0");
            }

            string url =
                $"https://nominatim.openstreetmap.org/reverse" +
                $"?lat={latitude.ToString(System.Globalization.CultureInfo.InvariantCulture)}" +
                $"&lon={longitude.ToString(System.Globalization.CultureInfo.InvariantCulture)}" +
                $"&format=json&addressdetails=1&zoom=18";

            var response = await _httpClient.GetAsync(url);
            if (!response.IsSuccessStatusCode)
                return "Lieu inconnu";

            string json = await response.Content.ReadAsStringAsync();
            using var document = JsonDocument.Parse(json);

            if (!document.RootElement.TryGetProperty("address", out var address))
                return "Lieu inconnu";

            string[] champs = { "neighbourhood", "quarter", "suburb", "village", "town", "city" };

            foreach (string champ in champs)
            {
                if (address.TryGetProperty(champ, out var valeur))
                {
                    string? nom = valeur.GetString();
                    if (!string.IsNullOrWhiteSpace(nom))
                        return nom;
                }
            }

            return "Lieu inconnu";
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Erreur Nominatim : {ex.Message}");
            return "Lieu inconnu";
        }
    }

    private async Task EnregistrerDansTurso(string telephone, string type, string lieu, string dateHeure)
    {
        _httpClient.DefaultRequestHeaders.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", TursoAuthToken);

        var payload = new
        {
            requests = new object[]
            {
                new
                {
                    type = "execute",
                    stmt = new
                    {
                        sql = "INSERT INTO pointages (telephone, type, lieu, date_heure) VALUES (?, ?, ?, ?)",
                        args = new object[]
                        {
                            new { type = "text", value = telephone },
                            new { type = "text", value = type },
                            new { type = "text", value = lieu },
                            new { type = "text", value = dateHeure }
                        }
                    }
                },
                new { type = "close" }
            }
        };

        var response = await _httpClient.PostAsJsonAsync(TursoDbUrl, payload);

        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync();
            throw new Exception($"Erreur Turso ({response.StatusCode}) : {error}");
        }
    }

    // ---------------------------------------------------------------------
    // Gestion de la file d'attente hors ligne
    // ---------------------------------------------------------------------

    private class PointageEnAttente
    {
        public string Telephone { get; set; } = "";
        public string Type { get; set; } = "";
        public string Lieu { get; set; } = "";
        public string DateHeure { get; set; } = "";
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        // false si "Lieu" est encore un placeholder de coordonnées, à résoudre via Nominatim à la synchro
        public bool LieuResolu { get; set; }
    }

    private async Task<List<PointageEnAttente>> ChargerFileAttenteAsync()
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
            Debug.WriteLine($"Erreur lecture file d'attente : {ex.Message}");
            return new List<PointageEnAttente>();
        }
    }

    private async Task SauvegarderFileAttenteAsync(List<PointageEnAttente> file)
    {
        try
        {
            string json = JsonSerializer.Serialize(file);
            await File.WriteAllTextAsync(OfflineQueuePath, json);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Erreur sauvegarde file d'attente : {ex.Message}");
        }
    }

    private async Task AjouterAFileAttenteAsync(string telephone, string type, string lieu, string dateHeure,
        double latitude, double longitude, bool lieuResolu)
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

    // Tente d'envoyer tous les pointages en attente vers Turso, dans l'ordre chronologique.
    // Les pointages dont le lieu n'était pas résolu (enregistrés hors ligne) sont résolus via
    // Nominatim au moment de la synchronisation.
    private async Task SynchroniserFileAttenteAsync()
    {
        if (!EstConnecte)
            return;

        // Évite les synchronisations concurrentes (ex : retour réseau + appel manuel simultanés)
        if (!await _syncLock.WaitAsync(0))
            return;

        try
        {
            var file = await ChargerFileAttenteAsync();
            if (file.Count == 0)
                return;

            var restants = new List<PointageEnAttente>();

            foreach (var p in file.OrderBy(p => p.DateHeure))
            {
                try
                {
                    string lieuFinal = p.Lieu;
                    if (!p.LieuResolu)
                    {
                        lieuFinal = await RecupererNomLieuAsync(p.Latitude, p.Longitude);
                    }

                    await EnregistrerDansTurso(p.Telephone, p.Type, lieuFinal, p.DateHeure);
                }
                catch (Exception ex)
                {
                    // Échec (ex : coupure réseau en cours de synchro) : on garde le pointage pour un prochain essai
                    Debug.WriteLine($"Échec synchro pointage {p.DateHeure} : {ex.Message}");
                    restants.Add(p);
                }
            }

            await SauvegarderFileAttenteAsync(restants);

            if (restants.Count == 0)
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    StatusLabel.Text = "Pointages hors ligne synchronisés avec succès.";
                });
            }
        }
        finally
        {
            _syncLock.Release();
        }
    }
}
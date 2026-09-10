using Microsoft.Maui.Devices.Sensors;
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

    // Lieu actuellement "ouvert" (Entrée sans Sortie correspondante)
    private string? _lieuOuvert = null;

    public MainPage()
    {
        InitializeComponent();
        PhoneEntry.TextChanged += async (s, e) => await RafraichirEtatBoutons();
    }

    private async void OnEntreeClicked(object sender, EventArgs e) => await EnregistrerPointage("Entrée");

    private async void OnSortieClicked(object sender, EventArgs e) => await EnregistrerPointage("Sortie");

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

            var location = await ObtenirPositionGps();
            if (location == null)
            {
                StatusLabel.Text = "Impossible d'obtenir la position GPS.";
                return;
            }

            StatusLabel.Text = "Recherche du lieu...";
            string lieu = await RecupererNomLieuAsync(location.Latitude, location.Longitude);

            // Règle métier : une Entrée sans Sortie correspondante bloque une nouvelle Entrée pour ce lieu
            if (type == "Entrée" && _lieuOuvert == lieu)
            {
                StatusLabel.Text = $"Entrée déjà enregistrée pour {lieu}. Veuillez faire Sortie d'abord.";
                return;
            }

            if (type == "Sortie" && _lieuOuvert != lieu)
            {
                StatusLabel.Text = $"Aucune Entrée ouverte pour {lieu}.";
                return;
            }

            StatusLabel.Text = "Enregistrement en base de données...";
            await EnregistrerDansTurso(PhoneEntry.Text, type, lieu);

            // Mise à jour de l'état local
            _lieuOuvert = (type == "Entrée") ? lieu : null;

            StatusLabel.Text = $"{type} enregistrée à {DateTime.Now:HH:mm:ss} ({lieu})";
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

    // Interroge Turso pour retrouver le dernier pointage de ce téléphone et déterminer l'état réel
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
                            sql = "SELECT type, lieu FROM pointages WHERE telephone = ? ORDER BY id DESC LIMIT 1",
                            args = new object[]
                            {
                                new { type = "text", value = PhoneEntry.Text }
                            }
                        }
                    },
                    new { type = "close" }
                }
            };

            var response = await _httpClient.PostAsJsonAsync(TursoDbUrl, payload);
            if (!response.IsSuccessStatusCode)
            {
                AppliquerEtatBoutons();
                return;
            }

            string json = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(json);

            var rows = doc.RootElement
                .GetProperty("results")[0]
                .GetProperty("response")
                .GetProperty("result")
                .GetProperty("rows");

            if (rows.GetArrayLength() == 0)
            {
                _lieuOuvert = null;
            }
            else
            {
                string dernierType = rows[0][0].GetProperty("value").GetString() ?? "";
                string dernierLieu = rows[0][1].GetProperty("value").GetString() ?? "";
                _lieuOuvert = (dernierType == "Entrée") ? dernierLieu : null;
            }
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

    private async Task<Location?> ObtenirPositionGps()
    {
        var status = await Permissions.RequestAsync<Permissions.LocationWhenInUse>();
        if (status != PermissionStatus.Granted)
        {
            await DisplayAlert("Permission refusée", "L'accès à la position est nécessaire.", "OK");
            return null;
        }

        try
        {
            var request = new GeolocationRequest(GeolocationAccuracy.Medium, TimeSpan.FromSeconds(10));
            return await Geolocation.Default.GetLocationAsync(request);
        }
        catch (Exception ex)
        {
            await DisplayAlert("Erreur GPS", ex.Message, "OK");
            return null;
        }
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

    private async Task EnregistrerDansTurso(string telephone, string type, string lieu)
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
                            new { type = "text", value = DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") }
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
}
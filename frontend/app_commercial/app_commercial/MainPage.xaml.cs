using Microsoft.Maui.Devices.Sensors;
using Microsoft.Maui.Networking;
using System.Diagnostics;

namespace app_commercial;

public partial class MainPage : ContentPage
{
    private bool _pointageEnCours;

    public MainPage()
    {
        InitializeComponent();

        // Le numéro est fixe pour cette installation (champ désactivé dans le
        // XAML), donc TextChanged ne se déclenchera jamais : on enregistre la
        // valeur directement au démarrage pour que PointageService (et donc
        // le suivi automatique en arrière-plan) connaisse toujours ce numéro.
        PointageService.NumeroTelephone = PhoneEntry.Text;

        Connectivity.Current.ConnectivityChanged += async (s, e) =>
        {
            if (e.NetworkAccess == NetworkAccess.Internet)
            {
                await PointageService.SynchroniserFileAttenteAsync();
                MainThread.BeginInvokeOnMainThread(async () => await RafraichirEtatBoutons());
            }
        };

        _ = PointageService.SynchroniserFileAttenteAsync();
        _ = RafraichirEtatBoutons();

        // Numéro fixe pour ce téléphone : on active le suivi automatique
        // dès le premier démarrage, sans attendre que l'utilisateur clique.
        // Un petit délai laisse la page finir de s'afficher avant que les
        // popups de permission Android n'apparaissent.
        _ = DemarrerSuiviAutomatiqueAuLancementAsync();
    }

    private async Task DemarrerSuiviAutomatiqueAuLancementAsync()
    {
        await Task.Delay(1000);
        await ActiverSuiviAutomatiqueAsync();
    }

    // Boutons manuels conservés : l'utilisateur peut toujours pointer lui-même
    // en plus de la détection automatique par changement de lieu.
    private async void OnEntreeClicked(object sender, EventArgs e) => await EnregistrerPointageManuel("Entrée");

    private async void OnSortieClicked(object sender, EventArgs e) => await EnregistrerPointageManuel("Sortie");

    // Bouton conservé dans le XAML : permet de relancer manuellement le
    // suivi si jamais il a été arrêté (redémarrage du téléphone sans avoir
    // saisi le numéro auparavant, permission révoquée puis réaccordée, etc.)
    private async void OnActiverSuiviAutomatiqueClicked(object sender, EventArgs e)
        => await ActiverSuiviAutomatiqueAsync(afficherConfirmation: true);

    private async Task ActiverSuiviAutomatiqueAsync(bool afficherConfirmation = false)
    {
        if (string.IsNullOrWhiteSpace(PhoneEntry.Text))
        {
            if (afficherConfirmation)
                await DisplayAlert("Erreur", "Veuillez saisir un numéro de téléphone avant d'activer le suivi.", "OK");
            return;
        }

        var statutFin = await Permissions.RequestAsync<Permissions.LocationWhenInUse>();
        if (statutFin != PermissionStatus.Granted)
        {
            if (afficherConfirmation)
                await DisplayAlert("Permission refusée", "La localisation est nécessaire pour le suivi automatique.", "OK");
            return;
        }

#if ANDROID
        // Sur Android 10+, la permission "arrière-plan" doit être demandée
        // séparément, après la permission "en cours d'utilisation".
        var statutArrierePlan = await Permissions.RequestAsync<Permissions.LocationAlways>();
        if (statutArrierePlan != PermissionStatus.Granted)
        {
            if (afficherConfirmation)
                await DisplayAlert("Permission requise",
                    "Pour fonctionner même app fermée, autorisez la localisation en 'Toujours autoriser' dans les réglages du téléphone.",
                    "OK");
            return;
        }

        var intent = new Android.Content.Intent(Platform.AppContext,
            typeof(Platforms.Android.BackgroundLocationService));
        if (OperatingSystem.IsAndroidVersionAtLeast(26))
            Platform.AppContext.StartForegroundService(intent);
        else
            Platform.AppContext.StartService(intent);

        if (afficherConfirmation)
            await DisplayAlert("Activé", "Le suivi automatique fonctionne désormais même si l'app est fermée.", "OK");
#else
        if (afficherConfirmation)
            await DisplayAlert("Non disponible",
                "Le suivi permanent en arrière-plan n'est implémenté que pour Android dans cette version.", "OK");
#endif
    }

    private async Task EnregistrerPointageManuel(string type)
    {
        if (string.IsNullOrWhiteSpace(PhoneEntry.Text))
        {
            await DisplayAlert("Erreur", "Veuillez saisir un numéro de téléphone.", "OK");
            return;
        }

        if (_pointageEnCours)
            return; // évite un double clic pendant l'enregistrement (les boutons restent actifs)

        _pointageEnCours = true;

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

            // On réutilise la même logique que le suivi automatique pour
            // rester cohérent, mais ici on force le type demandé (bouton
            // Entrée ou Sortie) plutôt que de laisser la détection décider.
            bool enLigne = Connectivity.Current.NetworkAccess == NetworkAccess.Internet;
            string lieu = enLigne
                ? await PointageService.RecupererNomLieuAsync(location.Latitude, location.Longitude)
                : $"Lieu GPS ({location.Latitude:F4}, {location.Longitude:F4})";

            string dateHeure = DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss");

            if (enLigne)
            {
                try
                {
                    StatusLabel.Text = "Enregistrement en base de données...";
                    await PointageService.EnregistrerDansTurso(PhoneEntry.Text, type, lieu, dateHeure);
                    StatusLabel.Text = $"{type} enregistrée à {DateTime.Now:HH:mm:ss} ({lieu})";
                }
                catch (Exception)
                {
                    await PointageService.AjouterAFileAttenteAsync(PhoneEntry.Text, type, lieu, dateHeure,
                        location.Latitude, location.Longitude, lieuResolu: true);
                    StatusLabel.Text = $"{type} enregistrée hors ligne ({lieu}) — sera synchronisée.";
                }
            }
            else
            {
                await PointageService.AjouterAFileAttenteAsync(PhoneEntry.Text, type, lieu, dateHeure,
                    location.Latitude, location.Longitude, lieuResolu: false);
                StatusLabel.Text = $"{type} enregistrée hors ligne ({lieu}) — sera synchronisée dès la reconnexion.";
            }

            // Empêche le contrôle journalier automatique de réinsérer une
            // seconde Entrée juste après un pointage manuel.
            if (type == "Entrée")
                PointageService.MarquerEntreeAujourdHui();
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
            _pointageEnCours = false;
            await RafraichirEtatBoutons();
        }
    }

    private async Task RafraichirEtatBoutons()
    {
        // Les deux boutons restent toujours actifs.
        EntreeBtn.IsEnabled = true;
        SortieBtn.IsEnabled = true;
        await Task.CompletedTask;
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
            var request = new GeolocationRequest(GeolocationAccuracy.Best, TimeSpan.FromSeconds(10));
            return await Geolocation.Default.GetLocationAsync(request);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Erreur GPS : {ex.Message}");
            await DisplayAlert("Erreur GPS", "Impossible d'obtenir une position GPS.", "OK");
            return null;
        }
    }
}
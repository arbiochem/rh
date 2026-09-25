using Android.App;
using Android.Content;
using Android.OS;
using AndroidX.Core.App;
using Java.Util;
using Microsoft.Maui.Networking;
using System.Diagnostics;
using System.Net;
using static System.Net.Mime.MediaTypeNames;

namespace app_commercial.Platforms.Android;

/// <summary>
/// Foreground Service Android.
///
/// Responsabilités :
/// - suivi GPS en continu, même lorsque l'application est fermée ;
/// - traitement automatique des positions ;
/// - synchronisation automatique des pointages hors ligne ;
/// - synchronisation dès que Wi-Fi ou Data mobile revient.
/// </summary>
[Service(
    Name = "app_commercial.Platforms.Android.BackgroundLocationService",
    Exported = false,
    ForegroundServiceType = global::Android.Content.PM.ForegroundService.TypeLocation)]
public class BackgroundLocationService :
    Service,
    global::Android.Locations.ILocationListener
{
    private const string LogTag = "PointageApp";
    private const string ChannelId = "pointage_gps_channel";
    private const int NotificationId = 1001;

    private const long IntervalleGpsMs = 3 * 60 * 1000L;

    private const float PrecisionMaxMetres = 150f;
    private const long AgeMaxPositionMs = 2 * 60 * 1000L;

    private global::Android.Locations.LocationManager? _locationManager;

    private bool _demarre;

    // Vérification du pointage journalier
    private System.Threading.Timer? _minuteurVerificationJournaliere;

    private static readonly TimeSpan IntervalleVerification =
        TimeSpan.FromMinutes(30);

    // Synchronisation périodique de sécurité.
    // Même si Android ne nous signale pas le retour réseau,
    // on réessaie régulièrement.
    private System.Threading.Timer? _minuteurSynchronisation;

    private static readonly TimeSpan IntervalleSynchronisation =
        TimeSpan.FromMinutes(5);

    // Évite plusieurs synchronisations simultanées.
    private static readonly SemaphoreSlim _syncLock =
        new SemaphoreSlim(1, 1);

    public override IBinder? OnBind(Intent? intent) => null;

    public override StartCommandResult OnStartCommand(
        Intent? intent,
        StartCommandFlags flags,
        int startId)
    {
        Log.Info("Service démarré (OnStartCommand)");

        DemarrerEnForeground();

        // Évite de recréer les timers et les listeners.
        if (_demarre)
            return StartCommandResult.Sticky;

        _demarre = true;

        // -----------------------------
        // GPS
        // -----------------------------
        DemarrerEcouteGps();

        // -----------------------------
        // Vérification journalière
        // -----------------------------
        DemarrerVerificationJournaliere();

        // -----------------------------
        // Synchronisation réseau
        // -----------------------------
        DemarrerSynchronisationAutomatique();

        // Synchronisation immédiate au démarrage
        _ = SynchroniserSiPossibleAsync("démarrage du service");

        return StartCommandResult.Sticky;
    }

    // ============================================================
    // SYNCHRONISATION AUTOMATIQUE
    // ============================================================

    private void DemarrerSynchronisationAutomatique()
    {
        // Synchronisation périodique de sécurité.
        //
        // Même si l'événement ConnectivityChanged n'est pas reçu,
        // la file sera vérifiée toutes les 5 minutes.
        _minuteurSynchronisation = new System.Threading.Timer(
            _ => _ = SynchroniserSiPossibleAsync("minuteur"),
            null,
            IntervalleSynchronisation,
            IntervalleSynchronisation);

        // Écoute du retour Wi-Fi / Data.
        Connectivity.Current.ConnectivityChanged +=
            OnConnectivityChanged;

        Log.Info("Surveillance de la connectivité activée.");
    }

    private async void OnConnectivityChanged(
        object? sender,
        ConnectivityChangedEventArgs e)
    {
        try
        {
            Log.Info(
                $"Connectivité changée : {e.NetworkAccess}");

            if (e.NetworkAccess == NetworkAccess.Internet)
            {
                Log.Info(
                    "Internet disponible -> synchronisation immédiate.");

                // Petit délai pour laisser Android établir complètement
                // la connexion réseau.
                await Task.Delay(1000);

                await SynchroniserSiPossibleAsync(
                    "retour du réseau");
            }
        }
        catch (Exception ex)
        {
            Log.Error(
                $"Erreur ConnectivityChanged : {ex}");
        }
    }

    private async Task SynchroniserSiPossibleAsync(string origine)
    {
        // Pas d'Internet
        if (Connectivity.Current.NetworkAccess !=
            NetworkAccess.Internet)
        {
            Log.Info(
                $"Synchronisation ignorée ({origine}) : pas d'Internet.");

            return;
        }

        // Une seule synchronisation à la fois.
        if (!await _syncLock.WaitAsync(0))
        {
            Log.Info(
                $"Synchronisation déjà en cours ({origine}).");

            return;
        }

        try
        {
            Log.Info(
                $"Début synchronisation ({origine}).");

            await PointageService.SynchroniserFileAttenteAsync();

            Log.Info(
                $"Synchronisation terminée ({origine}).");
        }
        catch (Exception ex)
        {
            // Très important :
            // ne jamais supprimer la file en cas d'erreur.
            // SynchroniserFileAttenteAsync conserve les éléments
            // qui n'ont pas pu être envoyés.
            Log.Error(
                $"Erreur synchronisation ({origine}) : {ex}");
        }
        finally
        {
            _syncLock.Release();
        }
    }

    // ============================================================
    // VÉRIFICATION POINTAGE JOURNALIER
    // ============================================================

    private void DemarrerVerificationJournaliere()
    {
        _minuteurVerificationJournaliere =
            new System.Threading.Timer(
                _ => VerifierPointageJournalier(),
                null,
                TimeSpan.Zero,
                IntervalleVerification);
    }

    private void VerifierPointageJournalier()
    {
        _ = ExecuterEtJournaliserAsync(
            () => PointageService.VerifierPointageDuJourAsync(),
            nameof(VerifierPointageJournalier));
    }

    private static async Task ExecuterEtJournaliserAsync(
        Func<Task> action,
        string nomOperation)
    {
        try
        {
            await action();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine(
                $"[BackgroundLocationService] " +
                $"Erreur dans {nomOperation} : {ex}");

            Log.Error(
                $"Erreur dans {nomOperation} : {ex}");
        }
    }

    // ============================================================
    // FOREGROUND SERVICE
    // ============================================================

    private void DemarrerEnForeground()
    {
        if (Build.VERSION.SdkInt >= BuildVersionCodes.O)
        {
            var channel = new NotificationChannel(
                ChannelId,
                "Suivi de pointage GPS",
                NotificationImportance.Low)
            {
                Description =
                    "Suivi de position et synchronisation " +
                    "automatique des pointages"
            };

            var manager =
                (NotificationManager)GetSystemService(
                    NotificationService)!;

            manager.CreateNotificationChannel(channel);
        }

        var notification =
            new NotificationCompat.Builder(this, ChannelId)
                .SetContentTitle("Pointage automatique actif")
                .SetContentText(
                    "Suivi GPS et synchronisation automatique actifs.")
                .SetSmallIcon(
                    global::Android.Resource.Drawable.IcMenuMyLocation)
                .SetOngoing(true)
                .Build();

        StartForeground(
            NotificationId,
            notification);
    }

    // ============================================================
    // GPS
    // ============================================================

    private void DemarrerEcouteGps()
    {
        _locationManager =
            (global::Android.Locations.LocationManager)
            GetSystemService(LocationService)!;

        try
        {
            bool gps =
                _locationManager.IsProviderEnabled(
                    global::Android.Locations.LocationManager.GpsProvider);

            bool reseau =
                _locationManager.IsProviderEnabled(
                    global::Android.Locations.LocationManager.NetworkProvider);

            Log.Info(
                $"Fournisseurs actifs : GPS={gps}, Réseau={reseau}");

            if (gps)
            {
                _locationManager.RequestLocationUpdates(
                    global::Android.Locations.LocationManager.GpsProvider,
                    IntervalleGpsMs,
                    0f,
                    this);
            }

            if (reseau)
            {
                _locationManager.RequestLocationUpdates(
                    global::Android.Locations.LocationManager.NetworkProvider,
                    IntervalleGpsMs,
                    0f,
                    this);
            }
        }
        catch (Java.Lang.SecurityException ex)
        {
            Log.Error(
                $"Permission localisation refusée, " +
                $"arrêt du service : {ex.Message}");

            StopSelf();
        }
    }

    public void OnLocationChanged(
        global::Android.Locations.Location location)
    {
        Log.Info(
            $"Position reçue ({location.Provider}) : " +
            $"{location.Latitude:F5}, " +
            $"{location.Longitude:F5}, " +
            $"précision {(location.HasAccuracy ? location.Accuracy : -1):F0} m");

        // Précision insuffisante
        if (location.HasAccuracy &&
            location.Accuracy > PrecisionMaxMetres)
        {
            Log.Info(
                "Position ignorée : précision insuffisante.");

            return;
        }

        // Position trop ancienne
        long ageMs =
            Java.Lang.JavaSystem.CurrentTimeMillis() -
            location.Time;

        if (ageMs > AgeMaxPositionMs)
        {
            Log.Info(
                $"Position ignorée : trop ancienne " +
                $"({ageMs / 1000} s).");

            return;
        }

        _ = ExecuterEtJournaliserAsync(
            async () =>
            {
                // 1. Traitement de la nouvelle position
                await PointageService.TraiterNouvellePositionAsync(
                    location.Latitude,
                    location.Longitude);

                // 2. Synchronisation éventuelle
                //    même si la position n'a rien provoqué.
                await SynchroniserSiPossibleAsync(
                    "nouvelle position GPS");
            },
            nameof(OnLocationChanged));
    }

    // ============================================================
    // FOURNISSEURS GPS
    // ============================================================

    public void OnProviderDisabled(string provider)
    {
        Log.Info(
            $"Fournisseur désactivé : {provider}");
    }

    public void OnProviderEnabled(string provider)
    {
        Log.Info(
            $"Fournisseur activé : {provider}");

        try
        {
            if (provider ==
                    global::Android.Locations.LocationManager.GpsProvider ||
                provider ==
                    global::Android.Locations.LocationManager.NetworkProvider)
            {
                _locationManager?.RequestLocationUpdates(
                    provider,
                    IntervalleGpsMs,
                    0f,
                    this);
            }
        }
        catch (Java.Lang.SecurityException ex)
        {
            Log.Error(
                $"Permission localisation refusée " +
                $"(OnProviderEnabled) : {ex.Message}");
        }
    }

    public void OnStatusChanged(
        string? provider,
        global::Android.Locations.Availability status,
        Bundle? extras)
    {
    }

    // ============================================================
    // ARRÊT
    // ============================================================

    public override void OnDestroy()
    {
        Log.Info("Service arrêté (OnDestroy)");

        // Arrêter le GPS
        _locationManager?.RemoveUpdates(this);

        // Arrêter les timers
        _minuteurVerificationJournaliere?.Dispose();
        _minuteurVerificationJournaliere = null;

        _minuteurSynchronisation?.Dispose();
        _minuteurSynchronisation = null;

        // Désabonner la surveillance réseau
        Connectivity.Current.ConnectivityChanged -=
            OnConnectivityChanged;

        _demarre = false;

        base.OnDestroy();
    }

    // ============================================================
    // LOG
    // ============================================================

    private static class Log
    {
        public static void Info(string message) =>
            global::Android.Util.Log.Info(
                LogTag,
                message);

        public static void Error(string message) =>
            global::Android.Util.Log.Error(
                LogTag,
                message);
    }
}

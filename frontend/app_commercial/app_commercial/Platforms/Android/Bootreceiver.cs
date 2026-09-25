using Android.App;
using Android.Content;
using Android.OS;
using Microsoft.Maui;

namespace app_commercial.Platforms.Android;

/// <summary>
/// Reçoit l'évènement BOOT_COMPLETED (déclenché par Android quand le
/// téléphone termine son démarrage) et relance automatiquement le service
/// de suivi GPS, sans que l'utilisateur ait besoin d'ouvrir l'app.
/// </summary>
[BroadcastReceiver(Enabled = true, Exported = true)]
[IntentFilter(new[] { Intent.ActionBootCompleted })]
public class BootReceiver : BroadcastReceiver
{
    public override void OnReceive(Context? context, Intent? intent)
    {
        if (intent?.Action != Intent.ActionBootCompleted || context == null)
            return;

        // Ne redémarre le suivi que si un numéro a déjà été configuré une
        // fois par l'utilisateur (donc que l'app a déjà été ouverte au
        // moins une fois pour donner les permissions et saisir son numéro).
        if (string.IsNullOrWhiteSpace(PointageService.NumeroTelephone))
            return;

        var serviceIntent = new Intent(context, typeof(BackgroundLocationService));
        if (Build.VERSION.SdkInt >= BuildVersionCodes.O)
            context.StartForegroundService(serviceIntent);
        else
            context.StartService(serviceIntent);
    }
}
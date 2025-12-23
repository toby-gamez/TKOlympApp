using Android.App;
using Android.Content.PM;
using Android.OS;
using Microsoft.Maui;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using AndroidX.Activity;

namespace TkOlympApp;

[Activity(Theme = "@style/Maui.SplashTheme", MainLauncher = true, LaunchMode = LaunchMode.SingleTop,
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode |
                           ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density)]
public class MainActivity : MauiAppCompatActivity
{
    const int REQUEST_POST_NOTIFICATIONS = 1001;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        // Request runtime notification permission on Android 13+
        try
        {
            if (Android.OS.Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.Tiramisu)
            {
                if (CheckSelfPermission(Android.Manifest.Permission.PostNotifications) != Permission.Granted)
                {
                    RequestPermissions(new[] { Android.Manifest.Permission.PostNotifications }, REQUEST_POST_NOTIFICATIONS);
                }
            }
        }
        catch
        {
            // ignore permission-check errors
        }

        // If app was launched from a notification, forward data to the notification manager
        try
        {
            var extras = Intent?.Extras;
            if (extras?.GetBoolean("openNoticeboard") == true)
            {
                var title = extras.GetString("notificationTitle") ?? "Nová aktualita";
                var message = extras.GetString("notificationMessage") ?? string.Empty;
                TkOlympApp.Services.NotificationManagerService.HandleIntent(title, message);
            }
        }
        catch
        {
            // ignore intent handling errors
        }

        // Register AndroidX back dispatcher callback (non-obsolete)
        OnBackPressedDispatcher?.AddCallback(this, new BackCallback());
    }

    public override void OnRequestPermissionsResult(int requestCode, string[] permissions, Permission[] grantResults)
    {
        base.OnRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_POST_NOTIFICATIONS)
        {
            var granted = grantResults != null && grantResults.Length > 0 && grantResults[0] == Permission.Granted;
            if (!granted)
            {
                try
                {
                    // Show rationale dialog with option to open app settings
                    var builder = new Android.App.AlertDialog.Builder(this);
                    builder.SetTitle("Povolit notifikace");
                    builder.SetMessage("Aby aplikace mohla zobrazovat notifikace, povolte upozornění v nastavení aplikace.");
                    builder.SetPositiveButton("Otevřít nastavení", (s, e) =>
                    {
                        var intent = new Android.Content.Intent(Android.Provider.Settings.ActionApplicationDetailsSettings);
                        var uri = Android.Net.Uri.Parse("package:" + PackageName);
                        intent.SetData(uri);
                        StartActivity(intent);
                    });
                    builder.SetNegativeButton("Zavřít", (s, e) => { });
                    var dlg = builder.Create();
                    dlg?.Show();
                }
                catch
                {
                    // ignore UI errors
                }
            }
        }
    }

    private sealed class BackCallback : OnBackPressedCallback
    {
        public BackCallback() : base(true) { }

        public override void HandleOnBackPressed()
        {
            var shell = Shell.Current;
            var nav = shell?.Navigation;

            // Handle modal stack first
            if (nav?.ModalStack != null && nav.ModalStack.Count > 0)
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    try { await nav.PopModalAsync(); } catch { }
                });
                return;
            }

            // Then handle page navigation stack
            if (nav?.NavigationStack != null && nav.NavigationStack.Count > 1)
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    try
                    {
                        await shell!.GoToAsync("..");
                    }
                    catch
                    {
                        try { await nav.PopAsync(); } catch { }
                    }
                });
                return;
            }

            // At root: disable callback and delegate to system
            // This lets Android finish/minimize the app
            this.Enabled = false;
        }
    }
}
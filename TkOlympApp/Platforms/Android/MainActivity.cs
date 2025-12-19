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
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);

        // Register AndroidX back dispatcher callback (non-obsolete)
        OnBackPressedDispatcher.AddCallback(this, new BackCallback());
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
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Storage;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        // Apply language early so AppShell and initial pages use correct culture
        try
        {
            var stored = Preferences.Get("app_language", (string?)null);
            var lang = stored ?? LocalizationService.DetermineDefaultLanguage();
            LocalizationService.ApplyLanguage(lang);
        }
        catch { }

        // Keep constructor minimal; window + shell created in CreateWindow
    }

    // Trigger startup navigation after window is created to avoid race conditions

    protected override Window CreateWindow(IActivationState? activationState)
    {
        var win = new Window(new AppShell());

        try
        {
            Dispatcher.Dispatch(async () =>
            {
                try { await Task.Delay(150); } catch { }
                try
                {
                    if (Shell.Current != null)
                    {
                        // If this is the first run, show FirstRunPage first
                        try
                        {
                            if (!FirstRunHelper.HasSeen())
                            {
                                try { await Shell.Current.GoToAsync("FirstRunPage"); } catch { }
                                return;
                            }
                        }
                        catch { }

                        try { await Shell.Current.GoToAsync("//Kalendář"); } catch { }
                    }
                }
                catch { }
            });
        }
        catch { }

        return win;
    }
}
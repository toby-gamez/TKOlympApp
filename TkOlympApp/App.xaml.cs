using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Storage;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
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
                    var stored = Preferences.Get("app_language", (string?)null);
                    var lang = stored ?? LocalizationService.DetermineDefaultLanguage();
                    LocalizationService.ApplyLanguage(lang);
                    if (Shell.Current != null) await Shell.Current.GoToAsync("//Kalendář");
                }
                catch { }
            });
        }
        catch { }

        return win;
    }
}
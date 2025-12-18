using Microsoft.Maui.Controls;
using MauiIcons.Core;
using TkOlympApp.Pages;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class AppShell : Shell
{
    public AppShell()
    {
        InitializeComponent();
        // Workaround for URL-based XAML namespace resolution issues
        _ = new MauiIcon();

        // Register the LoginPage route and navigate conditionally on startup
        Routing.RegisterRoute(nameof(LoginPage), typeof(LoginPage));

        Dispatcher.Dispatch(async () =>
        {
            try
            {
                await AuthService.InitializeAsync();
                var hasToken = await AuthService.HasTokenAsync();
                if (!hasToken)
                {
                    // Show login without resetting Shell root to avoid route issues
                    await GoToAsync(nameof(LoginPage));
                }
            }
            catch
            {
                // Ignore navigation errors during startup
            }
        });
    }
}
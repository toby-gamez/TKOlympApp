using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Pages;

namespace TkOlympApp.Pages;

public partial class OtherPage : ContentPage
{
    public OtherPage()
    {
        InitializeComponent();
    }

    private async void OnLogoutClicked(object? sender, EventArgs e)
    {
        try
        {
            await AuthService.LogoutAsync();

            // Navigate to login route instead of setting Application.MainPage (obsolete setter)
            await Shell.Current.GoToAsync($"//{nameof(LoginPage)}");
        }
        catch
        {
            // As a fallback, try a simple navigation to the login route
            try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { /* ignore */ }
        }
    }

    private async void OnAboutMeClicked(object? sender, EventArgs e)
    {
        try
        {
            await Shell.Current.GoToAsync(nameof(AboutMePage));
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba", ex.Message, "OK");
        }
    }

    private async void OnTrainersClicked(object? sender, EventArgs e)
    {
        try
        {
            await Shell.Current.GoToAsync(nameof(TrainersAndLocationsPage));
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba", ex.Message, "OK");
        }
    }

    private async void OnCohortGroupsClicked(object? sender, EventArgs e)
    {
        try
        {
            await Shell.Current.GoToAsync(nameof(CohortGroupsPage));
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba", ex.Message, "OK");
        }
    }

    private void OnSendTestNotificationClicked(object? sender, EventArgs e)
    {
        try
        {
            TkOlympApp.Services.NotificationManagerService.EnsureInitialized();
            var mgr = TkOlympApp.Services.NotificationManagerService.Instance;
            mgr?.SendNotification("Nová aktualita", "Test aktuality");
        }
        catch
        {
            // ignore
        }
    }
}

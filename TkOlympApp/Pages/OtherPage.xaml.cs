using Microsoft.Maui.Controls;
using TkOlympApp.Services;

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

            // Reset Shell to a clean state to avoid navigation stack issues
            Application.Current.MainPage = new AppShell();
        }
        catch
        {
            // As a fallback, try a simple navigation to the login route
            try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { /* ignore */ }
        }
    }
}

using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class LoginPage : ContentPage
{
    public LoginPage()
    {
        InitializeComponent();
        Shell.SetTabBarIsVisible(this, false);
    }

    private async void OnLoginClicked(object? sender, EventArgs e)
    {
        var username = UsernameEntry.Text?.Trim() ?? string.Empty;
        var password = PasswordEntry.Text ?? string.Empty;

        if (string.IsNullOrEmpty(username) || string.IsNullOrEmpty(password))
        {
            await DisplayAlertAsync("Chyba", "Vyplňte prosím jméno i heslo.", "OK");
            return;
        }

        try
        {
            // Attempt GraphQL login; throws if credentials invalid
            await AuthService.LoginAsync(username, password);

            // Navigate to the first tab (Kalendář) defined in AppShell
            await Shell.Current.GoToAsync("//Kalendář");
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Přihlášení selhalo", ex.Message, "OK");
        }
    }

    protected override bool OnBackButtonPressed()
    {
        return true;
    }
}

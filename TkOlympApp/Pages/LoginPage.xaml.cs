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
            await DisplayAlertAsync(LocalizationService.Get("Error_Title"), LocalizationService.Get("Login_Error_Missing"), LocalizationService.Get("Button_OK"));
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
            await DisplayAlertAsync(LocalizationService.Get("Login_Failed_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
    }

    protected override bool OnBackButtonPressed()
    {
        return true;
    }
}

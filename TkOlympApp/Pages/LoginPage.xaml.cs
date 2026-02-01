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

    protected override void OnAppearing()
    {
        base.OnAppearing();
        
        // Subscribe to events
        if (LoginButton != null)
            LoginButton.Clicked += OnLoginClicked;
    }

    protected override void OnDisappearing()
    {
        // Unsubscribe from events to prevent memory leaks
        if (LoginButton != null)
            LoginButton.Clicked -= OnLoginClicked;
        
        base.OnDisappearing();
    }

    private void OnUsernameBorderTapped(object? sender, EventArgs e)
    {
        UsernameEntry?.Focus();
    }

    private void OnPasswordBorderTapped(object? sender, EventArgs e)
    {
        PasswordEntry?.Focus();
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

            // Navigate to the first tab (Přehled/MainPage) defined in AppShell
            await Shell.Current.GoToAsync("//Přehled");
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

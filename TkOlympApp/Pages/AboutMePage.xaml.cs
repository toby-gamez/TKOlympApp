using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class AboutMePage : ContentPage
{
    public AboutMePage()
    {
        InitializeComponent();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        LoadingIndicator.IsVisible = true;
        LoadingIndicator.IsRunning = true;
        ErrorLabel.IsVisible = false;
        try
        {
            var user = await UserService.GetCurrentUserAsync();
            if (user == null)
            {
                ErrorLabel.IsVisible = true;
                ErrorLabel.Text = "Nepodařilo se načíst uživatele.";
                return;
            }

            NameValue.Text = NonEmpty(user.UJmeno);
            SurnameValue.Text = NonEmpty(user.UPrijmeni);
            LoginValue.Text = NonEmpty(user.ULogin);
            EmailValue.Text = NonEmpty(user.UEmail);
            LastLoginValue.Text = NonEmpty(FormatDt(user.LastLogin));
            LastActiveValue.Text = NonEmpty(FormatDt(user.LastActiveAt));
            UCreatedAtValue.Text = NonEmpty(FormatDt(user.UCreatedAt));
            CreatedAtValue.Text = NonEmpty(FormatDt(user.CreatedAt));
            UpdatedAtValue.Text = NonEmpty(FormatDt(user.UpdatedAt));
            // lastVersion removed from API — no longer displayed
        }
        catch (Exception ex)
        {
            ErrorLabel.IsVisible = true;
            ErrorLabel.Text = ex.Message;
        }
        finally
        {
            LoadingIndicator.IsRunning = false;
            LoadingIndicator.IsVisible = false;
        }
    }

    private static string FormatDt(DateTime? dt)
        => dt.HasValue ? dt.Value.ToLocalTime().ToString("dd.MM.yyyy HH:mm") : "";

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? "—" : s;

    private async void OnLogoutClicked(object? sender, EventArgs e)
    {
        try
        {
            await AuthService.LogoutAsync();

            // Navigate to login route
            await Shell.Current.GoToAsync($"//{nameof(LoginPage)}");
        }
        catch
        {
            try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { /* ignore */ }
        }
    }
}

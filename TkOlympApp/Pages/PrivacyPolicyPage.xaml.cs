using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;

namespace TkOlympApp.Pages;

public partial class PrivacyPolicyPage : ContentPage
{
    public PrivacyPolicyPage()
    {
        InitializeComponent();
    }

    private async void OnContactTapped(object? sender, EventArgs e)
    {
        try
        {
            await Launcher.OpenAsync("https://tkolymp.cz/kontakt");
        }
        catch
        {
            // best-effort; ignore failures
        }
    }
}

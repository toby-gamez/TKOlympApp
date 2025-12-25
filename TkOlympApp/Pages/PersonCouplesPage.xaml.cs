using System.Numerics;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class PersonCouplesPage : ContentPage
{
    public PersonCouplesPage()
    {
        InitializeComponent();
        _ = LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            LoadingIndicator.IsVisible = true;
            LoadingIndicator.IsRunning = true;
            CouplesCollection.IsVisible = false;
            EmptyLabel.IsVisible = false;

            var couples = await UserService.GetActiveCouplesFromUsersAsync();
            if (couples == null || couples.Count == 0)
            {
                EmptyLabel.IsVisible = true;
                return;
            }

            CouplesCollection.ItemsSource = couples.Select(c =>
                {
                    var man = string.IsNullOrWhiteSpace(c.ManName) ? "" : c.ManName;
                    var woman = string.IsNullOrWhiteSpace(c.WomanName) ? "" : c.WomanName;
                    return (man + " - " + woman).Trim();
                }).ToList();
            CouplesCollection.IsVisible = true;
        }
        catch (Exception ex)
        {
            await DisplayAlert("Chyba", ex.Message, "OK");
        }
        finally
        {
            LoadingIndicator.IsRunning = false;
            LoadingIndicator.IsVisible = false;
        }
    }

    private async void OnRefreshClicked(object sender, EventArgs e)
    {
        await LoadAsync();
    }
}

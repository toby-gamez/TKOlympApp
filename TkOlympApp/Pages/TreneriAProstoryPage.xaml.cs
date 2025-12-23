using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class TreneriAProstoryPage : ContentPage
{
    private bool _loaded;

    public TreneriAProstoryPage()
    {
        InitializeComponent();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        if (!_loaded)
        {
            _ = LoadDataAsync();
            _loaded = true;
        }
    }

    private async Task LoadDataAsync()
    {
        try
        {
            LoadingIndicator.IsVisible = true;
            LoadingIndicator.IsRunning = true;
            var (locations, trainers) = await TenantService.GetLocationsAndTrainersAsync();
            LocationsCollection.ItemsSource = locations.Select(l => l.Name ?? string.Empty).ToList();
            TrainersCollection.ItemsSource = trainers.Select(t =>
                {
                    var p = t.Person;
                    return string.Join(' ', new[] { p?.FirstName, p?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s)));
                }).ToList();
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            LoadingIndicator.IsVisible = false;
            LoadingIndicator.IsRunning = false;
        }
    }
}

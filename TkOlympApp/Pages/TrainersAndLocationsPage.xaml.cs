using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Devices;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class TrainersAndLocationsPage : ContentPage
{
    private bool _loaded;

    public TrainersAndLocationsPage()
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
            var locList = locations
                .Select(l => l.Name?.Trim())
                .Where(name => !string.IsNullOrEmpty(name) && !string.Equals(name, "ZRUŠENO", StringComparison.OrdinalIgnoreCase))
                .ToList();

            var trainerList = trainers
                .Select(t =>
                {
                    var p = t.Person;
                    var name = string.Join(' ', new[] { p?.FirstName?.Trim(), p?.LastName?.Trim() }
                        .Where(s => !string.IsNullOrWhiteSpace(s)));
                    return name;
                })
                .Where(n => !string.IsNullOrWhiteSpace(n))
                .ToList();

            BindableLayout.SetItemsSource(LocationsStack, locList);
            BindableLayout.SetItemsSource(TrainersStack, trainerList);
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            LoadingIndicator.IsVisible = false;
            LoadingIndicator.IsRunning = false;
        }
    }

    private async void OnLocationTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is VisualElement ve && ve.BindingContext is string loc && !string.IsNullOrWhiteSpace(loc))
            {
                var query = Uri.EscapeDataString(loc.Trim());
                Uri uri;
                if (DeviceInfo.Platform == DevicePlatform.Android)
                {
                    uri = new Uri($"geo:0,0?q={query}");
                }
                else if (DeviceInfo.Platform == DevicePlatform.iOS || DeviceInfo.Platform == DevicePlatform.MacCatalyst)
                {
                    uri = new Uri($"http://maps.apple.com/?q={query}");
                }
                else
                {
                    uri = new Uri($"https://www.google.com/maps/search/?api=1&query={query}");
                }

                await Launcher.OpenAsync(uri);
            }
        }
        catch
        {
            // best-effort - ignore failures silently
        }
    }
}

using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Devices;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Models.Tenants;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Pages;

public partial class TrainersAndLocationsPage : ContentPage
{
    private readonly ITenantService _tenantService;
    private bool _loaded;

    private sealed record TrainerDisplay(string Name, string Price, string? PersonId);

    public TrainersAndLocationsPage(ITenantService tenantService)
    {
        _tenantService = tenantService ?? throw new ArgumentNullException(nameof(tenantService));
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

    private async void OnRefresh(object? sender, EventArgs e)
    {
        try
        {
            await LoadDataAsync();
        }
        finally
        {
            try { if (PageRefresh != null) PageRefresh.IsRefreshing = false; } catch { }
        }
    }

    private async Task LoadDataAsync()
    {
        try
        {
            var (locations, trainers) = await _tenantService.GetLocationsAndTrainersAsync();
            var locList = locations
                .Select(l => l.Name?.Trim())
                .Where(name => !string.IsNullOrEmpty(name) && !string.Equals(name, "ZRUŠENO", StringComparison.OrdinalIgnoreCase))
                .ToList();

            var trainerList = trainers
                .Select(t =>
                {
                    var p = t.Person;
                    var name = string.Join(' ', new[] { p?.PrefixTitle?.Trim(), p?.FirstName?.Trim(), p?.LastName?.Trim(), p?.SuffixTitle?.Trim() }
                        .Where(s => !string.IsNullOrWhiteSpace(s)));

                    static string FormatPrice(Price? price)
                    {
                        if (price == null || price.Amount == null) return string.Empty;
                        var amt = price.Amount.Value;
                        var cur = price.Currency ?? string.Empty;

                        static string FormatAmount(decimal value, bool forceNoDecimals)
                        {
                            if (forceNoDecimals) return decimal.Round(value, 0).ToString("0");
                            return value == decimal.Truncate(value) ? value.ToString("0") : value.ToString("0.##");
                        }

                        string formatted;
                        if (string.Equals(cur, "CZK", StringComparison.OrdinalIgnoreCase))
                        {
                            var a = FormatAmount(amt, true);
                            formatted = $"{a},-";
                        }
                        else if (string.Equals(cur, "EUR", StringComparison.OrdinalIgnoreCase))
                        {
                            var a = FormatAmount(amt, false);
                            formatted = $"{a} €";
                        }
                        else if (string.Equals(cur, "USD", StringComparison.OrdinalIgnoreCase) || string.Equals(cur, "USD", StringComparison.OrdinalIgnoreCase))
                        {
                            var a = FormatAmount(amt, false);
                            formatted = $"{a} $";
                        }
                        else
                        {
                            var a = FormatAmount(amt, false);
                            formatted = string.IsNullOrWhiteSpace(cur) ? a : $"{a} {cur}";
                        }

                        return formatted + (LocalizationService.Get("Price_PerCouple45") ?? " / pár, 45'");
                    }

                    var priceStr = FormatPrice(t.GuestPrice45Min);
                    if (string.IsNullOrWhiteSpace(priceStr)) priceStr = FormatPrice(t.GuestPayout45Min);

                    return new TrainerDisplay(name, priceStr, p?.Id);
                })
                .Where(td => !string.IsNullOrWhiteSpace(td.Name))
                .ToList();

            BindableLayout.SetItemsSource(LocationsStack, locList);
            BindableLayout.SetItemsSource(TrainersStack, trainerList);
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
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

    private async void OnTrainerTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is VisualElement ve && ve.BindingContext is TrainerDisplay td && !string.IsNullOrWhiteSpace(td.PersonId))
            {
                var id = td.PersonId!;
                await Shell.Current.GoToAsync($"{nameof(PersonPage)}?personId={Uri.EscapeDataString(id)}");
            }
        }
        catch { }
    }
}

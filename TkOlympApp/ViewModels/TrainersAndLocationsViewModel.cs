using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using TkOlympApp.Models.Tenants;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class TrainersAndLocationsViewModel : ViewModelBase
{
    private readonly ITenantService _tenantService;
    private readonly INavigationService _navigationService;

    private bool _loaded;

    public ObservableCollection<string> Locations { get; } = new();
    public ObservableCollection<TrainersAndLocationsTrainerItem> Trainers { get; } = new();

    [ObservableProperty]
    private bool _isRefreshing;

    public TrainersAndLocationsViewModel(ITenantService tenantService, INavigationService navigationService)
    {
        _tenantService = tenantService ?? throw new ArgumentNullException(nameof(tenantService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        if (!_loaded)
        {
            _loaded = true;
            await LoadDataAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadDataAsync();
    }

    [RelayCommand]
    private async Task OpenLocationAsync(string? location)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(location)) return;
            var query = Uri.EscapeDataString(location.Trim());
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
        catch
        {
            // best-effort
        }
    }

    [RelayCommand]
    private async Task OpenTrainerAsync(TrainersAndLocationsTrainerItem? trainer)
    {
        try
        {
            if (trainer == null || string.IsNullOrWhiteSpace(trainer.PersonId)) return;
            await _navigationService.NavigateToAsync(nameof(Pages.PersonPage), new Dictionary<string, object>
            {
                ["personId"] = trainer.PersonId
            });
        }
        catch
        {
        }
    }

    private async Task LoadDataAsync()
    {
        try
        {
            IsRefreshing = true;
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

                    var priceStr = FormatPrice(t.GuestPrice45Min);
                    if (string.IsNullOrWhiteSpace(priceStr)) priceStr = FormatPrice(t.GuestPayout45Min);

                    return new TrainersAndLocationsTrainerItem(name, priceStr, p?.Id);
                })
                .Where(td => !string.IsNullOrWhiteSpace(td.Name))
                .ToList();

            Locations.Clear();
            foreach (var l in locList) Locations.Add(l!);

            Trainers.Clear();
            foreach (var t in trainerList) Trainers.Add(t);
        }
        catch (Exception ex)
        {
            await Application.Current?.MainPage?.DisplayAlert(
                LocalizationService.Get("Error_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private static string FormatPrice(Price? price)
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
        else if (string.Equals(cur, "USD", StringComparison.OrdinalIgnoreCase) || string.Equals(cur, "US$", StringComparison.OrdinalIgnoreCase))
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

}

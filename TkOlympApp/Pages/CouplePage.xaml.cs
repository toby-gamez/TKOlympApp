using Microsoft.Maui.Controls;
using System;
using System.Threading;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(CoupleId), "id")]
public partial class CouplePage : ContentPage
{
    private string? _coupleId;
    private string? _manId;
    private string? _womanId;
    private bool _appeared;
    private bool _loadRequested;

    public string? CoupleId
    {
        get => _coupleId;
        set
        {
            _coupleId = value;
            _loadRequested = true;
            if (_appeared) _ = LoadAsync();
        }
    }

    public CouplePage()
    {
        InitializeComponent();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        _appeared = true;
        if (_loadRequested)
            await LoadAsync();
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        try
        {
            await LoadAsync();
        }
        finally
        {
            try { if (PageRefresh != null) PageRefresh.IsRefreshing = false; } catch { }
        }
    }

    private async Task LoadAsync(CancellationToken ct = default)
    {
        ErrorLabel.IsVisible = false;
            try
            {
                if (string.IsNullOrWhiteSpace(CoupleId))
                {
                    ErrorLabel.IsVisible = true;
                    ErrorLabel.Text = LocalizationService.Get("Couple_Error_MissingId");
                    return;
                }

                var couple = await CoupleService.GetCoupleAsync(CoupleId, ct);
                if (couple == null)
                {
                    ErrorLabel.IsVisible = true;
                    ErrorLabel.Text = LocalizationService.Get("Couple_Error_NotFound");
                    return;
                }

                _manId = couple.Man?.Id;
                _womanId = couple.Woman?.Id;

            IdValue.Text = NonEmpty(couple.Id);
            CreatedAtValue.Text = FormatDtString(couple.CreatedAt);

            // Set top name border: "manFirst manLast - womanFirst womanLast"
            var manFull = string.Join(" ", new[] { couple.Man?.FirstName, couple.Man?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
            var womanFull = string.Join(" ", new[] { couple.Woman?.FirstName, couple.Woman?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
            ManNameValue.Text = NonEmpty(manFull);
            WomanNameValue.Text = NonEmpty(womanFull);

            // details below
            ManFirstNameValue.Text = NonEmpty(couple.Man?.FirstName);
            ManLastNameValue.Text = NonEmpty(couple.Man?.LastName);
            ManPhoneValue.Text = NonEmpty(PhoneHelpers.Format(couple.Man?.Phone?.Trim()));

            WomanFirstNameValue.Text = NonEmpty(couple.Woman?.FirstName);
            WomanLastNameValue.Text = NonEmpty(couple.Woman?.LastName);
            WomanPhoneValue.Text = NonEmpty(PhoneHelpers.Format(couple.Woman?.Phone?.Trim()));

            // visibility toggles for named rows
            try { ManFirstNameRow.IsVisible = !string.IsNullOrWhiteSpace(couple.Man?.FirstName); } catch { }
            try { ManLastNameRow.IsVisible = !string.IsNullOrWhiteSpace(couple.Man?.LastName); } catch { }
            try { ManPhoneRow.IsVisible = !string.IsNullOrWhiteSpace(couple.Man?.Phone); } catch { }

            try { WomanFirstNameRow.IsVisible = !string.IsNullOrWhiteSpace(couple.Woman?.FirstName); } catch { }
            try { WomanLastNameRow.IsVisible = !string.IsNullOrWhiteSpace(couple.Woman?.LastName); } catch { }
            try { WomanPhoneRow.IsVisible = !string.IsNullOrWhiteSpace(couple.Woman?.Phone); } catch { }
        }
        catch (Exception ex)
        {
            ErrorLabel.IsVisible = true;
            ErrorLabel.Text = ex.Message;
        }
        finally
        {
        }
    }

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? LocalizationService.Get("Placeholder_Empty") : s;

    private static string FormatDtString(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return LocalizationService.Get("Placeholder_Empty");
        if (DateTime.TryParse(s, out var dt)) return dt.ToLocalTime().ToString("dd.MM.yyyy HH:mm");
        return s;
    }

    private async void OnManTapped(object? sender, EventArgs e)
    {
        try
        {
            var id = _manId;
            if (string.IsNullOrWhiteSpace(id)) return;
            await Shell.Current.GoToAsync($"{nameof(PersonPage)}?personId={Uri.EscapeDataString(id)}");
        }
        catch { }
    }

    private async void OnWomanTapped(object? sender, EventArgs e)
    {
        try
        {
            var id = _womanId;
            if (string.IsNullOrWhiteSpace(id)) return;
            await Shell.Current.GoToAsync($"{nameof(PersonPage)}?personId={Uri.EscapeDataString(id)}");
        }
        catch { }
    }
}

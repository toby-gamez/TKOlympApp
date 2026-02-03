using System;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class CoupleViewModel : ViewModelBase
{
    private readonly ICoupleService _coupleService;
    private readonly INavigationService _navigationService;
    private bool _appeared;
    private bool _loadRequested;
    private string? _manId;
    private string? _womanId;

    [ObservableProperty]
    private string? _coupleId;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _isErrorVisible;

    [ObservableProperty]
    private string _errorText = string.Empty;

    [ObservableProperty]
    private string _idValue = string.Empty;

    [ObservableProperty]
    private string _createdAtValue = string.Empty;

    [ObservableProperty]
    private string _manNameValue = string.Empty;

    [ObservableProperty]
    private string _womanNameValue = string.Empty;

    [ObservableProperty]
    private string _manFirstNameValue = string.Empty;

    [ObservableProperty]
    private string _manLastNameValue = string.Empty;

    [ObservableProperty]
    private string _manPhoneValue = string.Empty;

    [ObservableProperty]
    private string _womanFirstNameValue = string.Empty;

    [ObservableProperty]
    private string _womanLastNameValue = string.Empty;

    [ObservableProperty]
    private string _womanPhoneValue = string.Empty;

    [ObservableProperty]
    private bool _manFirstNameVisible;

    [ObservableProperty]
    private bool _manLastNameVisible;

    [ObservableProperty]
    private bool _manPhoneVisible;

    [ObservableProperty]
    private bool _womanFirstNameVisible;

    [ObservableProperty]
    private bool _womanLastNameVisible;

    [ObservableProperty]
    private bool _womanPhoneVisible;

    public CoupleViewModel(ICoupleService coupleService, INavigationService navigationService)
    {
        _coupleService = coupleService ?? throw new ArgumentNullException(nameof(coupleService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    partial void OnCoupleIdChanged(string? value)
    {
        _loadRequested = true;
        if (_appeared)
        {
            _ = LoadAsync();
        }
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        _appeared = true;
        if (_loadRequested)
        {
            await LoadAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadAsync();
    }

    [RelayCommand]
    private async Task OpenManAsync()
    {
        try
        {
            var id = _manId;
            if (string.IsNullOrWhiteSpace(id)) return;
            await _navigationService.NavigateToAsync(nameof(Pages.PersonPage), new System.Collections.Generic.Dictionary<string, object>
            {
                ["personId"] = id
            });
        }
        catch { }
    }

    [RelayCommand]
    private async Task OpenWomanAsync()
    {
        try
        {
            var id = _womanId;
            if (string.IsNullOrWhiteSpace(id)) return;
            await _navigationService.NavigateToAsync(nameof(Pages.PersonPage), new System.Collections.Generic.Dictionary<string, object>
            {
                ["personId"] = id
            });
        }
        catch { }
    }

    private async Task LoadAsync()
    {
        IsErrorVisible = false;
        ErrorText = string.Empty;

        try
        {
            IsRefreshing = true;
            if (string.IsNullOrWhiteSpace(CoupleId))
            {
                IsErrorVisible = true;
                ErrorText = LocalizationService.Get("Couple_Error_MissingId") ?? string.Empty;
                return;
            }

            var couple = await _coupleService.GetCoupleAsync(CoupleId);
            if (couple == null)
            {
                IsErrorVisible = true;
                ErrorText = LocalizationService.Get("Couple_Error_NotFound") ?? string.Empty;
                return;
            }

            _manId = couple.Man?.Id;
            _womanId = couple.Woman?.Id;

            IdValue = NonEmpty(couple.Id);
            CreatedAtValue = FormatDtString(couple.CreatedAt);

            var manFull = string.Join(" ", new[] { couple.Man?.FirstName, couple.Man?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
            var womanFull = string.Join(" ", new[] { couple.Woman?.FirstName, couple.Woman?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
            ManNameValue = NonEmpty(manFull);
            WomanNameValue = NonEmpty(womanFull);

            ManFirstNameValue = NonEmpty(couple.Man?.FirstName);
            ManLastNameValue = NonEmpty(couple.Man?.LastName);
            ManPhoneValue = NonEmpty(PhoneHelpers.Format(couple.Man?.Phone?.Trim()));

            WomanFirstNameValue = NonEmpty(couple.Woman?.FirstName);
            WomanLastNameValue = NonEmpty(couple.Woman?.LastName);
            WomanPhoneValue = NonEmpty(PhoneHelpers.Format(couple.Woman?.Phone?.Trim()));

            ManFirstNameVisible = !string.IsNullOrWhiteSpace(couple.Man?.FirstName);
            ManLastNameVisible = !string.IsNullOrWhiteSpace(couple.Man?.LastName);
            ManPhoneVisible = !string.IsNullOrWhiteSpace(couple.Man?.Phone);

            WomanFirstNameVisible = !string.IsNullOrWhiteSpace(couple.Woman?.FirstName);
            WomanLastNameVisible = !string.IsNullOrWhiteSpace(couple.Woman?.LastName);
            WomanPhoneVisible = !string.IsNullOrWhiteSpace(couple.Woman?.Phone);
        }
        catch (Exception ex)
        {
            IsErrorVisible = true;
            ErrorText = ex.Message;
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? (LocalizationService.Get("Placeholder_Empty") ?? string.Empty) : s;

    private static string FormatDtString(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return LocalizationService.Get("Placeholder_Empty") ?? string.Empty;
        if (DateTime.TryParse(s, out var dt)) return dt.ToLocalTime().ToString("dd.MM.yyyy HH:mm");
        return s;
    }
}

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Helpers;
using TkOlympApp.Models.People;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class AboutMeViewModel : ViewModelBase
{
    private readonly IServiceProvider _services;
    private readonly IAuthService _authService;
    private readonly IPeopleService _peopleService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    public ObservableCollection<AboutMeCohortItem> Cohorts { get; } = new();

    [ObservableProperty]
    private string _errorText = string.Empty;

    [ObservableProperty]
    private bool _isErrorVisible;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private string _nameText = "—";

    [ObservableProperty]
    private string _surnameText = "—";

    [ObservableProperty]
    private string _loginText = "—";

    [ObservableProperty]
    private string _emailText = "—";

    [ObservableProperty]
    private string _lastLoginText = "—";

    [ObservableProperty]
    private string _lastActiveText = "—";

    [ObservableProperty]
    private string _createdAtText = "—";

    [ObservableProperty]
    private string _updatedAtText = "—";

    [ObservableProperty]
    private string _proxyIdText = "—";

    [ObservableProperty]
    private string _bioText = "—";

    [ObservableProperty]
    private string _birthDateText = "—";

    [ObservableProperty]
    private string _phoneText = "—";

    [ObservableProperty]
    private string _nationalityText = "—";

    [ObservableProperty]
    private string _addressText = "—";

    [ObservableProperty]
    private string _genderText = "—";

    [ObservableProperty]
    private string _isTrainerText = "—";

    [ObservableProperty]
    private string _wdsfIdText = "—";

    [ObservableProperty]
    private string _cstsIdText = "—";

    [ObservableProperty]
    private string _nationalIdText = "—";

    [ObservableProperty]
    private bool _emailVisible;

    [ObservableProperty]
    private bool _lastLoginVisible;

    [ObservableProperty]
    private bool _lastActiveVisible;

    [ObservableProperty]
    private bool _createdAtVisible;

    [ObservableProperty]
    private bool _updatedAtVisible;

    [ObservableProperty]
    private bool _proxyIdVisible;

    [ObservableProperty]
    private bool _bioVisible;

    [ObservableProperty]
    private bool _birthDateVisible;

    [ObservableProperty]
    private bool _phoneVisible;

    [ObservableProperty]
    private bool _nationalityVisible;

    [ObservableProperty]
    private bool _addressVisible;

    [ObservableProperty]
    private bool _genderVisible;

    [ObservableProperty]
    private bool _isTrainerVisible;

    [ObservableProperty]
    private bool _wdsfVisible;

    [ObservableProperty]
    private bool _cstsVisible;

    [ObservableProperty]
    private bool _nationalIdVisible;

    [ObservableProperty]
    private bool _contactBorderVisible;

    [ObservableProperty]
    private bool _personalBorderVisible;

    [ObservableProperty]
    private bool _addressBorderVisible;

    [ObservableProperty]
    private bool _idsBorderVisible;

    [ObservableProperty]
    private bool _cohortsVisible;

    public AboutMeViewModel(
        IServiceProvider services,
        IAuthService authService,
        IPeopleService peopleService,
        IUserService userService,
        INavigationService navigationService,
        IUserNotifier notifier)
    {
        _services = services ?? throw new ArgumentNullException(nameof(services));
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _peopleService = peopleService ?? throw new ArgumentNullException(nameof(peopleService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadAsync();
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadAsync();
    }

    [RelayCommand]
    private async Task LogoutAsync()
    {
        try
        {
            await _authService.LogoutAsync();
            try
            {
                await _navigationService.NavigateToAsync(nameof(Pages.LoginPage));
            }
            catch
            {
                try { await Shell.Current.GoToAsync($"//{nameof(Pages.LoginPage)}"); }
                catch (Exception ex)
                {
                    LoggerService.SafeLogWarning<AboutMeViewModel>("Failed to navigate to login after logout: {0}", new object[] { ex.Message });
                }
            }
        }
        catch
        {
            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var msg = LocalizationService.Get("Error_OperationFailed_Message") ?? "Operace selhala.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            await _notifier.ShowAsync(title, msg, ok);
            try { await Shell.Current.GoToAsync(nameof(Pages.LoginPage)); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<AboutMeViewModel>("Failed to navigate to login: {0}", new object[] { ex.Message });
            }
        }
    }

    [RelayCommand]
    private async Task ChangePasswordAsync()
    {
        try
        {
            var page = _services.GetRequiredService<Pages.ChangePasswordPage>();
            await Shell.Current.Navigation.PushModalAsync(page);
        }
        catch (Exception ex)
        {
            try
            {
                await _navigationService.NavigateToAsync(nameof(Pages.ChangePasswordPage));
                return;
            }
            catch (Exception ex2)
            {
                var title = LocalizationService.Get("Error_Title") ?? "Chyba";
                var msg = ex2.Message ?? ex.Message ?? (LocalizationService.Get("Error_OperationFailed_Message") ?? "Operace selhala.");
                var ok = LocalizationService.Get("Button_OK") ?? "OK";
                await _notifier.ShowAsync(title, msg, ok);
            }
        }
    }

    [RelayCommand]
    private async Task EditSelfAsync()
    {
        try
        {
            var page = _services.GetRequiredService<Pages.EditSelfPage>();
            await Shell.Current.Navigation.PushModalAsync(page);
        }
        catch (Exception ex)
        {
            try
            {
                await _navigationService.NavigateToAsync(nameof(Pages.EditSelfPage));
                return;
            }
            catch (Exception ex2)
            {
                var title = LocalizationService.Get("Error_Title") ?? "Chyba";
                var msg = ex2.Message ?? ex.Message ?? (LocalizationService.Get("Error_OperationFailed_Message") ?? "Operace selhala.");
                var ok = LocalizationService.Get("Button_OK") ?? "OK";
                await _notifier.ShowAsync(title, msg, ok);
            }
        }
    }

    private async Task LoadAsync()
    {
        if (IsBusy) return;

        IsBusy = true;
        IsRefreshing = true;
        IsErrorVisible = false;
        ErrorText = string.Empty;

        try
        {
            ResetValues();

            var user = await _userService.GetCurrentUserAsync();
            if (user == null)
            {
                IsErrorVisible = true;
                ErrorText = LocalizationService.Get("Error_Loading_User") ?? "Nepodařilo se načíst uživatele.";
                return;
            }

            NameText = NonEmpty(user.UJmeno);
            SurnameText = NonEmpty(user.UPrijmeni);
            LoginText = NonEmpty(user.ULogin);
            EmailText = NonEmpty(user.UEmail);
            LastLoginText = NonEmpty(FormatDt(user.LastLogin));
            LastActiveText = NonEmpty(FormatDt(user.LastActiveAt));
            CreatedAtText = NonEmpty(FormatDt(user.CreatedAt));
            UpdatedAtText = NonEmpty(FormatDt(user.UpdatedAt));

            EmailVisible = !string.IsNullOrWhiteSpace(user.UEmail);
            LastLoginVisible = user.LastLogin.HasValue;
            LastActiveVisible = user.LastActiveAt.HasValue;
            CreatedAtVisible = true;
            UpdatedAtVisible = true;

            UpdateBorderVisibility();

            await LoadProxyAndPersonAsync();
        }
        catch (Exception ex)
        {
            IsErrorVisible = true;
            ErrorText = ex.Message;
        }
        finally
        {
            IsRefreshing = false;
            IsBusy = false;
        }
    }

    private async Task LoadProxyAndPersonAsync()
    {
        try
        {
            var proxyId = await _userService.GetCurrentUserProxyIdAsync();
            ProxyIdText = NonEmpty(proxyId);
            ProxyIdVisible = !string.IsNullOrWhiteSpace(proxyId);

            try { _userService.SetCurrentPersonId(proxyId); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<AboutMeViewModel>("Failed to set current person id: {0}", new object[] { ex.Message });
            }

            UpdateBorderVisibility();

            if (!string.IsNullOrWhiteSpace(proxyId))
            {
                await LoadPersonAsync(proxyId);
            }
        }
        catch (Exception exProxy)
        {
            ProxyIdText = "—";
            ProxyIdVisible = false;
            UpdateBorderVisibility();

            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            await _notifier.ShowAsync(title, exProxy.Message, ok);
        }
    }

    private async Task LoadPersonAsync(string proxyId)
    {
        try
        {
            var person = await _peopleService.GetPersonFullAsync(proxyId);
            if (person == null) return;

            NameText = FormatPrefixName(person.PrefixTitle, person.FirstName);
            SurnameText = FormatNameSuffix(person.LastName, person.SuffixTitle);

            BioText = NonEmpty(person.Bio?.Trim());
            BirthDateText = FormatDtString(person.BirthDate);
            PhoneText = NonEmpty(PhoneHelpers.Format(person.Phone?.Trim()));
            NationalityText = NonEmpty(NationalityHelper.GetLocalizedAdjective(person.Nationality?.Trim()));
            AddressText = ComposeAddress(person.Address);
            GenderText = MapGender(person.Gender);
            IsTrainerText = person.IsTrainer.HasValue
                ? (person.IsTrainer.Value ? LocalizationService.Get("About_Yes") : LocalizationService.Get("About_No"))
                : "—";
            WdsfIdText = NonEmpty(person.WdsfId?.Trim());
            CstsIdText = NonEmpty(person.CstsId?.Trim());
            NationalIdText = NonEmpty(person.NationalIdNumber?.Trim());

            BioVisible = !string.IsNullOrWhiteSpace(person.Bio);
            BirthDateVisible = !string.IsNullOrWhiteSpace(person.BirthDate);
            PhoneVisible = !string.IsNullOrWhiteSpace(person.Phone);
            NationalityVisible = !string.IsNullOrWhiteSpace(person.Nationality);
            AddressVisible = AddressText != "—";
            GenderVisible = !string.IsNullOrWhiteSpace(person.Gender);
            IsTrainerVisible = person.IsTrainer.HasValue;
            WdsfVisible = !string.IsNullOrWhiteSpace(person.WdsfId);
            CstsVisible = !string.IsNullOrWhiteSpace(person.CstsId);
            NationalIdVisible = !string.IsNullOrWhiteSpace(person.NationalIdNumber);

            UpdateCohorts(person.CohortMembershipsList);
            UpdateBorderVisibility();
        }
        catch (Exception exPerson)
        {
            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            await _notifier.ShowAsync(title, exPerson.Message, ok);
        }
    }

    private void UpdateCohorts(List<PersonCohortMembership>? memberships)
    {
        Cohorts.Clear();

        var cohortsList = (memberships ?? new List<PersonCohortMembership>())
            .Where(m => m?.Cohort?.IsVisible != false)
            .OrderBy(m => m?.Cohort?.Ordering ?? int.MaxValue)
            .ToList();

        foreach (var membership in cohortsList)
        {
            var c = membership?.Cohort;
            if (c == null) continue;

            var name = c.Name ?? string.Empty;
            var colorBrush = CohortColorHelper.ParseColorBrush(c.ColorRgb)
                             ?? new SolidColorBrush(Colors.LightGray);

            Cohorts.Add(new AboutMeCohortItem
            {
                Name = name,
                Color = colorBrush
            });
        }

        CohortsVisible = Cohorts.Count > 0;
    }

    private void ResetValues()
    {
        NameText = "—";
        SurnameText = "—";
        LoginText = "—";
        EmailText = "—";
        LastLoginText = "—";
        LastActiveText = "—";
        CreatedAtText = "—";
        UpdatedAtText = "—";
        ProxyIdText = "—";
        BioText = "—";
        BirthDateText = "—";
        PhoneText = "—";
        NationalityText = "—";
        AddressText = "—";
        GenderText = "—";
        IsTrainerText = "—";
        WdsfIdText = "—";
        CstsIdText = "—";
        NationalIdText = "—";

        EmailVisible = false;
        LastLoginVisible = false;
        LastActiveVisible = false;
        CreatedAtVisible = false;
        UpdatedAtVisible = false;
        ProxyIdVisible = false;
        BioVisible = false;
        BirthDateVisible = false;
        PhoneVisible = false;
        NationalityVisible = false;
        AddressVisible = false;
        GenderVisible = false;
        IsTrainerVisible = false;
        WdsfVisible = false;
        CstsVisible = false;
        NationalIdVisible = false;

        Cohorts.Clear();
        CohortsVisible = false;

        UpdateBorderVisibility();
    }

    private void UpdateBorderVisibility()
    {
        ContactBorderVisible = EmailVisible || PhoneVisible;
        PersonalBorderVisible = BioVisible || BirthDateVisible || NationalityVisible || GenderVisible || IsTrainerVisible;
        AddressBorderVisible = AddressVisible;
        IdsBorderVisible = ProxyIdVisible || WdsfVisible || CstsVisible || NationalIdVisible;
    }

    private static string FormatDt(DateTime? dt)
        => dt.HasValue ? dt.Value.ToLocalTime().ToString("dd.MM.yyyy HH:mm") : "";

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? "—" : s;

    private static string FormatDtString(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return "—";
        if (DateTime.TryParse(s, out var dt)) return dt.ToLocalTime().ToString("dd.MM.yyyy");
        return s;
    }

    private static string ComposeAddress(PersonAddress? a)
    {
        if (a == null) return "—";
        var street = a.Street?.Trim();
        var conscription = a.ConscriptionNumber?.Trim();
        var orientation = a.OrientationNumber?.Trim();

        string numberPart;
        if (!string.IsNullOrWhiteSpace(conscription) && !string.IsNullOrWhiteSpace(orientation))
            numberPart = conscription + "/" + orientation;
        else if (!string.IsNullOrWhiteSpace(conscription))
            numberPart = conscription;
        else if (!string.IsNullOrWhiteSpace(orientation))
            numberPart = orientation;
        else
            numberPart = string.Empty;

        var first = string.IsNullOrWhiteSpace(street)
            ? (string.IsNullOrWhiteSpace(numberPart) ? string.Empty : numberPart)
            : (string.IsNullOrWhiteSpace(numberPart) ? street : street + " " + numberPart);

        var postal = PostalCodeHelpers.Format(a.PostalCode?.Trim());
        var city = a.City?.Trim();
        var region = a.Region?.Trim();

        var postalCity = string.Empty;
        if (!string.IsNullOrWhiteSpace(postal) && !string.IsNullOrWhiteSpace(city)) postalCity = postal + " " + city;
        else if (!string.IsNullOrWhiteSpace(postal)) postalCity = postal;
        else if (!string.IsNullOrWhiteSpace(city)) postalCity = city;

        var parts = new[] { first, postalCity, region }.Where(p => !string.IsNullOrWhiteSpace(p)).ToArray();
        return parts.Length == 0 ? "—" : string.Join(", ", parts);
    }

    private static string MapGender(string? gender)
    {
        if (string.IsNullOrWhiteSpace(gender)) return "—";
        return gender.Trim().ToUpperInvariant() switch
        {
            "MAN" => LocalizationService.Get("About_Gender_Male"),
            "WOMAN" => LocalizationService.Get("About_Gender_Female"),
            _ => LocalizationService.Get("About_Gender_Other"),
        } ?? "—";
    }

    private static string FormatPrefixName(string? prefix, string? name)
    {
        prefix = prefix?.Trim();
        name = name?.Trim();
        if (string.IsNullOrWhiteSpace(prefix) && string.IsNullOrWhiteSpace(name)) return "—";
        if (string.IsNullOrWhiteSpace(name)) return prefix ?? "—";
        if (string.IsNullOrWhiteSpace(prefix)) return name!;
        return prefix + " " + name;
    }

    private static string FormatNameSuffix(string? name, string? suffix)
    {
        name = name?.Trim();
        suffix = suffix?.Trim();
        if (string.IsNullOrWhiteSpace(name) && string.IsNullOrWhiteSpace(suffix)) return "—";
        if (string.IsNullOrWhiteSpace(name)) return suffix ?? "—";
        if (string.IsNullOrWhiteSpace(suffix)) return name!;
        return name + " " + suffix;
    }

}

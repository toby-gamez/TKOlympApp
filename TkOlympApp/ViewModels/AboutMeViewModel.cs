using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class AboutMeViewModel : ViewModelBase
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly IServiceProvider _services;
    private readonly IAuthService _authService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    public ObservableCollection<CohortItem> Cohorts { get; } = new();

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
        IUserService userService,
        INavigationService navigationService,
        IUserNotifier notifier)
    {
        _services = services ?? throw new ArgumentNullException(nameof(services));
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
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
                try { await Shell.Current.GoToAsync($"//{nameof(Pages.LoginPage)}"); } catch { }
            }
        }
        catch
        {
            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var msg = LocalizationService.Get("Error_OperationFailed_Message") ?? "Operace selhala.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            await _notifier.ShowAsync(title, msg, ok);
            try { await Shell.Current.GoToAsync(nameof(Pages.LoginPage)); } catch { }
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
            var gqlReq = new { query = "query MyQuery { userProxiesList { person { id } } }" };
            var json = JsonSerializer.Serialize(gqlReq, Options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);

            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
            }

            var parsed = JsonSerializer.Deserialize<GraphQlResp<UserProxiesData>>(body, Options);
            if (parsed?.Errors != null && parsed.Errors.Length > 0)
            {
                var msg = parsed.Errors[0]?.Message ?? LocalizationService.Get("GraphQL_UnknownError");
                throw new InvalidOperationException(msg);
            }

            var proxyId = parsed?.Data?.UserProxiesList?.FirstOrDefault()?.Person?.Id;
            ProxyIdText = NonEmpty(proxyId);
            ProxyIdVisible = !string.IsNullOrWhiteSpace(proxyId);

            try { _userService.SetCurrentPersonId(proxyId); } catch { }

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
            var query = "query MyQuery { person(id: \"" + proxyId + "\") { address { city conscriptionNumber district orientationNumber postalCode region street } bio birthDate createdAt cstsId email firstName prefixTitle suffixTitle gender isTrainer lastName nationalIdNumber nationality phone wdsfId cohortMembershipsList { cohort { colorRgb id name ordering isVisible } } } }";

            var gqlReqP = new { query };
            var jsonP = JsonSerializer.Serialize(gqlReqP, Options);
            using var contentP = new StringContent(jsonP, Encoding.UTF8, "application/json");
            using var respP = await _authService.Http.PostAsync("", contentP);

            var bodyP = await respP.Content.ReadAsStringAsync();
            if (!respP.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"HTTP {(int)respP.StatusCode}: {bodyP}");
            }

            var parsedP = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(bodyP, Options);
            if (parsedP?.Errors != null && parsedP.Errors.Length > 0)
            {
                var msg = parsedP.Errors[0]?.Message ?? LocalizationService.Get("GraphQL_UnknownError");
                throw new InvalidOperationException(msg);
            }

            var person = parsedP?.Data?.Person;
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

    private void UpdateCohorts(List<CohortMembership>? memberships)
    {
        Cohorts.Clear();

        var cohortsList = (memberships ?? new List<CohortMembership>())
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

            Cohorts.Add(new CohortItem
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

    private static string ComposeAddress(Address? a)
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

    public sealed class CohortItem
    {
        public string Name { get; set; } = string.Empty;
        public Brush Color { get; set; } = new SolidColorBrush(Colors.LightGray);
    }

    private sealed class GraphQlResp<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public GraphQlError[]? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class UserProxiesData
    {
        [JsonPropertyName("userProxiesList")] public UserProxy[]? UserProxiesList { get; set; }
    }

    private sealed class UserProxy
    {
        [JsonPropertyName("person")] public Person? Person { get; set; }
    }

    private sealed class Person
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
    }

    private sealed class PersonRespData
    {
        [JsonPropertyName("person")] public PersonDetail? Person { get; set; }
    }

    private sealed class PersonDetail
    {
        [JsonPropertyName("address")] public Address? Address { get; set; }
        [JsonPropertyName("bio")] public string? Bio { get; set; }
        [JsonPropertyName("birthDate")] public string? BirthDate { get; set; }
        [JsonPropertyName("createdAt")] public string? CreatedAt { get; set; }
        [JsonPropertyName("cstsId")] public string? CstsId { get; set; }
        [JsonPropertyName("email")] public string? Email { get; set; }
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("prefixTitle")] public string? PrefixTitle { get; set; }
        [JsonPropertyName("suffixTitle")] public string? SuffixTitle { get; set; }
        [JsonPropertyName("gender")] public string? Gender { get; set; }
        [JsonPropertyName("isTrainer")] public bool? IsTrainer { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
        [JsonPropertyName("nationalIdNumber")] public string? NationalIdNumber { get; set; }
        [JsonPropertyName("nationality")] public string? Nationality { get; set; }
        [JsonPropertyName("phone")] public string? Phone { get; set; }
        [JsonPropertyName("wdsfId")] public string? WdsfId { get; set; }
        [JsonPropertyName("cohortMembershipsList")] public List<CohortMembership>? CohortMembershipsList { get; set; }
    }

    private sealed class CohortMembership
    {
        [JsonPropertyName("cohort")] public Cohort? Cohort { get; set; }
    }

    private sealed class Cohort
    {
        [JsonPropertyName("colorRgb")] public string? ColorRgb { get; set; }
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
        [JsonPropertyName("ordering")] public int? Ordering { get; set; }
        [JsonPropertyName("isVisible")] public bool? IsVisible { get; set; }
    }

    private sealed class Address
    {
        [JsonPropertyName("city")] public string? City { get; set; }
        [JsonPropertyName("conscriptionNumber")] public string? ConscriptionNumber { get; set; }
        [JsonPropertyName("district")] public string? District { get; set; }
        [JsonPropertyName("orientationNumber")] public string? OrientationNumber { get; set; }
        [JsonPropertyName("postalCode")] public string? PostalCode { get; set; }
        [JsonPropertyName("region")] public string? Region { get; set; }
        [JsonPropertyName("street")] public string? Street { get; set; }
    }
}

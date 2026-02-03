using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EditSelfViewModel : ViewModelBase
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly IAuthService _authService;
    private readonly IUserService _userService;
    private readonly IUserNotifier _notifier;

    private string? _personId;
    private bool _suppressBirthDateChange;

    public ObservableCollection<string> GenderOptions { get; } = new();
    public ObservableCollection<string> NationalityOptions { get; } = new();

    [ObservableProperty]
    private string _errorText = string.Empty;

    [ObservableProperty]
    private bool _isErrorVisible;

    [ObservableProperty]
    private string _firstName = string.Empty;

    [ObservableProperty]
    private string _lastName = string.Empty;

    [ObservableProperty]
    private string _prefixTitle = string.Empty;

    [ObservableProperty]
    private string _suffixTitle = string.Empty;

    [ObservableProperty]
    private int _selectedGenderIndex;

    [ObservableProperty]
    private DateTime _birthDate = DateTime.Today;

    [ObservableProperty]
    private bool _birthDateSet;

    [ObservableProperty]
    private string _email = string.Empty;

    [ObservableProperty]
    private string _phone = string.Empty;

    [ObservableProperty]
    private string _selectedNationality = string.Empty;

    [ObservableProperty]
    private string _wdsfId = string.Empty;

    [ObservableProperty]
    private string _cstsId = string.Empty;

    [ObservableProperty]
    private string _nationalId = string.Empty;

    [ObservableProperty]
    private string _street = string.Empty;

    [ObservableProperty]
    private string _city = string.Empty;

    [ObservableProperty]
    private string _postalCode = string.Empty;

    [ObservableProperty]
    private string _region = string.Empty;

    [ObservableProperty]
    private string _district = string.Empty;

    [ObservableProperty]
    private string _conscriptionNumber = string.Empty;

    [ObservableProperty]
    private string _orientationNumber = string.Empty;

    [ObservableProperty]
    private string _bio = string.Empty;

    public EditSelfViewModel(IAuthService authService, IUserService userService, IUserNotifier notifier)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();

        if (string.IsNullOrWhiteSpace(_personId))
        {
            _personId = _userService.CurrentPersonId;
        }

        await LoadAsync();
    }

    partial void OnBirthDateChanged(DateTime value)
    {
        if (_suppressBirthDateChange) return;
        BirthDateSet = true;
    }

    [RelayCommand]
    private async Task CancelAsync()
    {
        try { await Shell.Current.Navigation.PopModalAsync(); } catch { }
    }

    [RelayCommand]
    private async Task SaveAsync()
    {
        try
        {
            IsErrorVisible = false;
            ErrorText = string.Empty;

            var missing = new List<string>();
            if (string.IsNullOrWhiteSpace(Street)) missing.Add(LocalizationService.Get("Label_Street") ?? "Ulice");
            if (string.IsNullOrWhiteSpace(City)) missing.Add(LocalizationService.Get("Label_City") ?? "Město");
            if (string.IsNullOrWhiteSpace(PostalCode)) missing.Add(LocalizationService.Get("Label_PostalCode") ?? "PSČ");
            if (string.IsNullOrWhiteSpace(Region)) missing.Add(LocalizationService.Get("Label_Region") ?? "Kraj");
            if (string.IsNullOrWhiteSpace(District)) missing.Add(LocalizationService.Get("Label_District") ?? "Okres");
            if (string.IsNullOrWhiteSpace(ConscriptionNumber)) missing.Add(LocalizationService.Get("Label_ConscriptionNumber") ?? "Číslo narukování");
            if (string.IsNullOrWhiteSpace(OrientationNumber)) missing.Add(LocalizationService.Get("Label_OrientationNumber") ?? "Orient. číslo");
            if (missing.Count > 0)
            {
                ErrorText = LocalizationService.Get("Error_AddressRequired") ?? "Vyplňte prosím všechna pole adresy.";
                IsErrorVisible = true;
                return;
            }

            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorText = LocalizationService.Get("Error_MissingPersonId") ?? "ID osoby není dostupné.";
                IsErrorVisible = true;
                return;
            }

            var parts = new List<string>();
            void AddStringField(string name, string? value)
            {
                if (string.IsNullOrWhiteSpace(value)) return;
                var esc = JsonSerializer.Serialize(value);
                parts.Add($"{name}: {esc}");
            }

            AddStringField("bio", Bio);
            if (BirthDateSet)
            {
                var bdStr = $"{BirthDate:yyyy-MM-dd}";
                parts.Add($"birthDate: {JsonSerializer.Serialize(bdStr)}");
            }
            AddStringField("cstsId", CstsId);
            AddStringField("email", Email);
            AddStringField("firstName", FirstName);
            AddStringField("lastName", LastName);
            AddStringField("nationalIdNumber", NationalId);

            string? natValue = null;
            if (!string.IsNullOrWhiteSpace(SelectedNationality))
            {
                if (NationalityHelper.TryGetNumericCodeForName(SelectedNationality, out var code)) natValue = code;
                else natValue = SelectedNationality;
            }
            AddStringField("nationality", natValue);
            AddStringField("phone", Phone);
            AddStringField("wdsfId", WdsfId);
            AddStringField("prefixTitle", PrefixTitle);
            AddStringField("suffixTitle", SuffixTitle);

            var addressParts = new List<string>();
            void AddAddressField(string name, string? value)
            {
                if (string.IsNullOrWhiteSpace(value)) return;
                var esc = JsonSerializer.Serialize(value);
                addressParts.Add($"{name}: {esc}");
            }

            AddAddressField("street", Street);
            AddAddressField("city", City);
            AddAddressField("postalCode", PostalCode);
            AddAddressField("region", Region);
            AddAddressField("district", District);
            AddAddressField("conscriptionNumber", ConscriptionNumber);
            AddAddressField("orientationNumber", OrientationNumber);

            if (addressParts.Count > 0)
            {
                parts.Add("address: { " + string.Join(", ", addressParts) + " }");
            }

            var genderValue = SelectedGenderIndex switch { 1 => "MAN", 2 => "WOMAN", _ => "UNSPECIFIED" };
            if (!string.IsNullOrWhiteSpace(genderValue)) parts.Add($"gender: {genderValue}");

            var patchBody = parts.Count == 0 ? string.Empty : "{ " + string.Join(", ", parts) + " }";

            var mutation = patchBody.Length == 0
                ? $"mutation {{ updatePerson(input: {{id: \"{_personId}\"}}) {{ clientMutationId }} }}"
                : $"mutation {{ updatePerson(input: {{id: \"{_personId}\", patch: {patchBody}}}) {{ clientMutationId }} }}";

            var gqlReq = new { query = mutation };
            var json = JsonSerializer.Serialize(gqlReq, Options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);

            var body = await resp.Content.ReadAsStringAsync();
            var parsed = JsonSerializer.Deserialize<GraphQlResp<object>>(body, Options);
            if (parsed?.Errors != null && parsed.Errors.Length > 0)
            {
                ErrorText = parsed.Errors[0].Message ?? (LocalizationService.Get("Error_Saving") ?? "Chyba při ukládání.");
                IsErrorVisible = true;
                return;
            }

            if (!resp.IsSuccessStatusCode)
            {
                ErrorText = $"HTTP: {resp.StatusCode}";
                IsErrorVisible = true;
                return;
            }

            try { await Shell.Current.Navigation.PopModalAsync(); } catch { }
            var title = LocalizationService.Get("Save_Success_Title") ?? "Hotovo";
            var msg = LocalizationService.Get("Save_Success_Message") ?? "Údaje byly uloženy.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            await _notifier.ShowAsync(title, msg, ok);
        }
        catch (Exception ex)
        {
            ErrorText = ex.Message;
            IsErrorVisible = true;
        }
    }

    private async Task LoadAsync()
    {
        IsErrorVisible = false;
        ErrorText = string.Empty;

        try
        {
            GenderOptions.Clear();
            GenderOptions.Add(LocalizationService.Get("Gender_Other") ?? (LocalizationService.Get("About_Gender_Other") ?? "Other"));
            GenderOptions.Add(LocalizationService.Get("Gender_Male") ?? (LocalizationService.Get("About_Gender_Male") ?? "Male"));
            GenderOptions.Add(LocalizationService.Get("Gender_Female") ?? (LocalizationService.Get("About_Gender_Female") ?? "Female"));
        }
        catch
        {
        }

        try
        {
            NationalityOptions.Clear();
            foreach (var country in NationalityHelper.GetAllCountryNamesSorted())
            {
                NationalityOptions.Add(country);
            }
        }
        catch
        {
        }

        try
        {
            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorText = LocalizationService.Get("Error_MissingPersonId") ?? "ID osoby není dostupné.";
                IsErrorVisible = true;
                return;
            }

            var query = "query MyQuery { person(id: \"" + _personId + "\") { bio birthDate cstsId email firstName gender lastName nationalIdNumber nationality phone wdsfId prefixTitle suffixTitle address { city conscriptionNumber district orientationNumber postalCode region street } } }";
            var gqlReq = new { query };
            var json = JsonSerializer.Serialize(gqlReq, Options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);
            resp.EnsureSuccessStatusCode();

            var body = await resp.Content.ReadAsStringAsync();
            var parsed = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(body, Options);
            var person = parsed?.Data?.Person;
            if (person == null) return;

            FirstName = person.FirstName ?? string.Empty;
            LastName = person.LastName ?? string.Empty;
            Email = person.Email ?? string.Empty;
            Phone = person.Phone ?? string.Empty;
            Bio = person.Bio ?? string.Empty;
            WdsfId = person.WdsfId ?? string.Empty;
            CstsId = person.CstsId ?? string.Empty;
            NationalId = person.NationalIdNumber ?? string.Empty;
            PrefixTitle = person.PrefixTitle ?? string.Empty;
            SuffixTitle = person.SuffixTitle ?? string.Empty;

            var countryName = NationalityHelper.GetCountryName(person.Nationality);
            SelectedNationality = countryName ?? string.Empty;

            if (person.Address != null)
            {
                Street = person.Address.Street ?? string.Empty;
                City = person.Address.City ?? string.Empty;
                PostalCode = person.Address.PostalCode ?? string.Empty;
                Region = person.Address.Region ?? string.Empty;
                District = person.Address.District ?? string.Empty;
                ConscriptionNumber = person.Address.ConscriptionNumber ?? string.Empty;
                OrientationNumber = person.Address.OrientationNumber ?? string.Empty;
            }

            if (!string.IsNullOrWhiteSpace(person.BirthDate) && DateTime.TryParse(person.BirthDate, out var bd))
            {
                _suppressBirthDateChange = true;
                BirthDate = bd;
                _suppressBirthDateChange = false;
                BirthDateSet = true;
            }

            var gender = (person.Gender ?? string.Empty).ToUpperInvariant();
            if (gender == "MAN") SelectedGenderIndex = 1;
            else if (gender == "WOMAN") SelectedGenderIndex = 2;
            else SelectedGenderIndex = 0;
        }
        catch (Exception ex)
        {
            ErrorText = ex.Message;
            IsErrorVisible = true;
        }
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

    private sealed class PersonRespData
    {
        [JsonPropertyName("person")] public PersonDetail? Person { get; set; }
    }

    private sealed class PersonDetail
    {
        [JsonPropertyName("bio")] public string? Bio { get; set; }
        [JsonPropertyName("birthDate")] public string? BirthDate { get; set; }
        [JsonPropertyName("cstsId")] public string? CstsId { get; set; }
        [JsonPropertyName("email")] public string? Email { get; set; }
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("gender")] public string? Gender { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
        [JsonPropertyName("nationalIdNumber")] public string? NationalIdNumber { get; set; }
        [JsonPropertyName("nationality")] public string? Nationality { get; set; }
        [JsonPropertyName("phone")] public string? Phone { get; set; }
        [JsonPropertyName("wdsfId")] public string? WdsfId { get; set; }
        [JsonPropertyName("prefixTitle")] public string? PrefixTitle { get; set; }
        [JsonPropertyName("suffixTitle")] public string? SuffixTitle { get; set; }
        [JsonPropertyName("address")] public AddressDetail? Address { get; set; }
    }

    private sealed class AddressDetail
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

using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp.Pages;

public partial class EditSelfPage : ContentPage
{
    private string? _personId;
    private bool _birthDateSet = false;

    public EditSelfPage()
    {
        InitializeComponent();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        if (string.IsNullOrWhiteSpace(_personId))
        {
            _personId = UserService.CurrentPersonId;
        }
        await LoadAsync();
    }

    private sealed class GraphQlResp<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public GraphQlError[]? Errors { get; set; }
    }

    private sealed class GraphQlError { [JsonPropertyName("message")] public string? Message { get; set; } }

    private sealed class PersonRespData { [JsonPropertyName("person")] public PersonDetail? Person { get; set; } }
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

    private async Task LoadAsync()
    {
        ErrorLabel.IsVisible = false;
        // populate gender picker with localized items (x:String with markup extensions may not evaluate)
        try
        {
            GenderPicker.ItemsSource = new[]
            {
                LocalizationService.Get("Gender_Other") ?? (LocalizationService.Get("About_Gender_Other") ?? "Other"),
                LocalizationService.Get("Gender_Male") ?? (LocalizationService.Get("About_Gender_Male") ?? "Male"),
                LocalizationService.Get("Gender_Female") ?? (LocalizationService.Get("About_Gender_Female") ?? "Female")
            };
        }
        catch
        {
            // ignore
        }
        // populate nationality picker
        try
        {
            var countries = NationalityHelper.GetAllCountryNamesSorted();
            NationalityPicker.ItemsSource = countries;
        }
        catch
        {
            // ignore population errors
        }
        try
        {
            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorLabel.Text = LocalizationService.Get("Error_MissingPersonId") ?? "ID osoby není dostupné.";
                ErrorLabel.IsVisible = true;
                return;
            }

            var query = "query MyQuery { person(id: \"" + _personId + "\") { bio birthDate cstsId email firstName gender lastName nationalIdNumber nationality phone wdsfId prefixTitle suffixTitle address { city conscriptionNumber district orientationNumber postalCode region street } } }";
            var gqlReq = new { query };
            var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
            var json = JsonSerializer.Serialize(gqlReq, options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await AuthService.Http.PostAsync("", content);
            resp.EnsureSuccessStatusCode();

            var body = await resp.Content.ReadAsStringAsync();
            var parsed = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(body, options);
            var person = parsed?.Data?.Person;
                if (person != null)
            {
                FirstNameEntry.Text = person.FirstName;
                LastNameEntry.Text = person.LastName;
                EmailEntry.Text = person.Email;
                PhoneEntry.Text = person.Phone;
                // display localized/normalized country name
                var countryName = NationalityHelper.GetCountryName(person.Nationality);
                if (NationalityPicker.ItemsSource is System.Collections.IList list)
                {
                    var idx = -1;
                    for (int i = 0; i < list.Count; i++)
                    {
                        if (string.Equals(list[i]?.ToString(), countryName, StringComparison.OrdinalIgnoreCase)) { idx = i; break; }
                    }
                    NationalityPicker.SelectedIndex = idx;
                }
                BioEditor.Text = person.Bio;
                if (person.Address != null)
                {
                    StreetEntry.Text = person.Address.Street;
                    CityEntry.Text = person.Address.City;
                    PostalCodeEntry.Text = person.Address.PostalCode;
                    RegionEntry.Text = person.Address.Region;
                    DistrictEntry.Text = person.Address.District;
                    ConscriptionNumberEntry.Text = person.Address.ConscriptionNumber;
                    OrientationNumberEntry.Text = person.Address.OrientationNumber;
                }
                if (!string.IsNullOrWhiteSpace(person.BirthDate) && DateTime.TryParse(person.BirthDate, out var bd))
                {
                    BirthDatePicker.Date = bd;
                    _birthDateSet = true;
                }
                WdsfEntry.Text = person.WdsfId;
                CstsEntry.Text = person.CstsId;
                NationalIdEntry.Text = person.NationalIdNumber;
                PrefixTitleEntry.Text = person.PrefixTitle;
                SuffixTitleEntry.Text = person.SuffixTitle;
                // set gender picker
                var gender = (person.Gender ?? string.Empty).ToUpperInvariant();
                if (gender == "MAN") GenderPicker.SelectedIndex = 1;
                else if (gender == "WOMAN") GenderPicker.SelectedIndex = 2;
                else GenderPicker.SelectedIndex = 0;
            }
        }
        catch (Exception ex)
        {
            ErrorLabel.Text = ex.Message;
            ErrorLabel.IsVisible = true;
        }
    }

    private async void OnCancelClicked(object? sender, EventArgs e)
    {
        try { await Shell.Current.Navigation.PopModalAsync(); } catch { }
    }

    private async void OnSaveClicked(object? sender, EventArgs e)
    {
        try
        {
            ErrorLabel.IsVisible = false;

            // Validate that all address fields are filled (required)
            var missing = new System.Collections.Generic.List<string>();
            if (string.IsNullOrWhiteSpace(StreetEntry.Text)) missing.Add(LocalizationService.Get("Label_Street") ?? "Ulice");
            if (string.IsNullOrWhiteSpace(CityEntry.Text)) missing.Add(LocalizationService.Get("Label_City") ?? "Město");
            if (string.IsNullOrWhiteSpace(PostalCodeEntry.Text)) missing.Add(LocalizationService.Get("Label_PostalCode") ?? "PSČ");
            if (string.IsNullOrWhiteSpace(RegionEntry.Text)) missing.Add(LocalizationService.Get("Label_Region") ?? "Kraj");
            if (string.IsNullOrWhiteSpace(DistrictEntry.Text)) missing.Add(LocalizationService.Get("Label_District") ?? "Okres");
            if (string.IsNullOrWhiteSpace(ConscriptionNumberEntry.Text)) missing.Add(LocalizationService.Get("Label_ConscriptionNumber") ?? "Číslo narukování");
            if (string.IsNullOrWhiteSpace(OrientationNumberEntry.Text)) missing.Add(LocalizationService.Get("Label_OrientationNumber") ?? "Orient. číslo");
            if (missing.Count > 0)
            {
                ErrorLabel.Text = LocalizationService.Get("Error_AddressRequired") ?? "Vyplňte prosím všechna pole adresy.";
                ErrorLabel.IsVisible = true;
                return;
            }

            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorLabel.Text = "ID osoby není dostupné.";
                ErrorLabel.IsVisible = true;
                return;
            }

            // Build inline patch object for GraphQL (server doesn't accept PersonPatchInput variable type)
            var parts = new List<string>();
            void AddStringField(string name, string? value)
            {
                if (string.IsNullOrWhiteSpace(value)) return;
                var esc = JsonSerializer.Serialize(value);
                parts.Add($"{name}: {esc}");
            }

            AddStringField("bio", BioEditor.Text);
            if (_birthDateSet)
            {
                var bdStr = $"{BirthDatePicker.Date:yyyy-MM-dd}";
                parts.Add($"birthDate: {JsonSerializer.Serialize(bdStr)}");
            }
            AddStringField("cstsId", CstsEntry.Text);
            AddStringField("email", EmailEntry.Text);
            AddStringField("firstName", FirstNameEntry.Text);
            AddStringField("lastName", LastNameEntry.Text);
            AddStringField("nationalIdNumber", NationalIdEntry.Text);
            // nationality: send numeric code when available, otherwise the selected name
            string? natValue = null;
            if (NationalityPicker.SelectedIndex >= 0)
            {
                var selected = NationalityPicker.SelectedItem?.ToString();
                if (!string.IsNullOrWhiteSpace(selected) && NationalityHelper.TryGetNumericCodeForName(selected, out var code)) natValue = code;
                else natValue = selected;
            }
            AddStringField("nationality", natValue);
            AddStringField("phone", PhoneEntry.Text);
            AddStringField("wdsfId", WdsfEntry.Text);
            AddStringField("prefixTitle", PrefixTitleEntry.Text);
            AddStringField("suffixTitle", SuffixTitleEntry.Text);

            // address fields
            var addressParts = new List<string>();
            void AddAddressField(string name, string? value)
            {
                if (string.IsNullOrWhiteSpace(value)) return;
                var esc = JsonSerializer.Serialize(value);
                addressParts.Add($"{name}: {esc}");
            }

            AddAddressField("street", StreetEntry.Text);
            AddAddressField("city", CityEntry.Text);
            AddAddressField("postalCode", PostalCodeEntry.Text);
            AddAddressField("region", RegionEntry.Text);
            AddAddressField("district", DistrictEntry.Text);
            AddAddressField("conscriptionNumber", ConscriptionNumberEntry.Text);
            AddAddressField("orientationNumber", OrientationNumberEntry.Text);

            if (addressParts.Count > 0)
            {
                parts.Add("address: { " + string.Join(", ", addressParts) + " }");
            }

            var genderValue = GenderPicker.SelectedIndex switch { 1 => "MAN", 2 => "WOMAN", _ => "UNSPECIFIED" };
            if (!string.IsNullOrWhiteSpace(genderValue)) parts.Add($"gender: {genderValue}");

            var patchBody = parts.Count == 0 ? string.Empty : "{ " + string.Join(", ", parts) + " }";

            // Ensure we have person id from service if not already set
            if (string.IsNullOrWhiteSpace(_personId)) _personId = UserService.CurrentPersonId;

            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorLabel.Text = "ID osoby není dostupné.";
                ErrorLabel.IsVisible = true;
                return;
            }

            var mutation = patchBody.Length == 0
                ? $"mutation {{ updatePerson(input: {{id: \"{_personId}\"}}) {{ clientMutationId }} }}"
                : $"mutation {{ updatePerson(input: {{id: \"{_personId}\", patch: {patchBody}}}) {{ clientMutationId }} }}";

            var gqlReq = new { query = mutation };
            var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
            var json = JsonSerializer.Serialize(gqlReq, options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await AuthService.Http.PostAsync("", content);

            var body = await resp.Content.ReadAsStringAsync();
            var parsed = JsonSerializer.Deserialize<GraphQlResp<object>>(body, options);
                if (parsed?.Errors != null && parsed.Errors.Length > 0)
            {
                ErrorLabel.Text = parsed.Errors[0].Message ?? (LocalizationService.Get("Error_Saving") ?? "Chyba při ukládání.");
                ErrorLabel.IsVisible = true;
                return;
            }

            if (!resp.IsSuccessStatusCode)
            {
                ErrorLabel.Text = $"HTTP: {resp.StatusCode}";
                ErrorLabel.IsVisible = true;
                return;
            }

            try { await Shell.Current.Navigation.PopModalAsync(); } catch { }
            try { await Shell.Current.DisplayAlertAsync(LocalizationService.Get("Save_Success_Title") ?? "Hotovo", LocalizationService.Get("Save_Success_Message") ?? "Údaje byly uloženy.", LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
        catch (Exception ex)
        {
            ErrorLabel.Text = ex.Message;
            ErrorLabel.IsVisible = true;
        }
    }

    private void OnBirthDateSelected(object sender, DateChangedEventArgs e)
    {
        _birthDateSet = true;
    }
}

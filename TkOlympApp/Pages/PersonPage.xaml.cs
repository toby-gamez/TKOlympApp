using System;
using System.Linq;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp.Pages
{
    [QueryProperty(nameof(PersonId), "personId")]
    public partial class PersonPage : ContentPage
    {
        private string? _personId;
        private bool _appeared;
        private bool _loadRequested;
        private readonly ObservableCollection<ActiveCoupleDisplay> _activeCouples = new();

        public string? PersonId
        {
            get => _personId;
            set
            {
                _personId = value;
                _loadRequested = true;
                if (_appeared) _ = LoadAsync();
            }
        }

        public PersonPage()
        {
            InitializeComponent();
            ActiveCouplesCollection.ItemsSource = _activeCouples;
        }

        protected override async void OnAppearing()
        {
            base.OnAppearing();
            _appeared = true;
            if (_loadRequested)
                await LoadAsync();
        }

        private async Task LoadAsync()
        {
            LoadingIndicator.IsVisible = true;
            LoadingIndicator.IsRunning = true;
            ErrorLabel.IsVisible = false;

            try
            {
                if (string.IsNullOrWhiteSpace(_personId))
                {
                    ErrorLabel.IsVisible = true;
                    ErrorLabel.Text = "Missing personId";
                    return;
                }

                var query = "query MyQuery { person(id: \"" + _personId + "\") { bio birthDate createdAt cstsId email firstName gender isTrainer lastName phone wdsfId activeCouplesList { id man { firstName lastName } woman { firstName lastName } } } }";

                var gqlReq = new { query };
                var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
                var json = JsonSerializer.Serialize(gqlReq, options);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await AuthService.Http.PostAsync("", content);
                resp.EnsureSuccessStatusCode();

                var body = await resp.Content.ReadAsStringAsync();
                var parsed = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(body, options);
                var person = parsed?.Data?.Person;
                if (person == null)
                {
                    ErrorLabel.IsVisible = true;
                    ErrorLabel.Text = LocalizationService.Get("NotFound_Person") ?? "Person not found";
                    return;
                }

                // Populate fields
                NameValue.Text = NonEmpty(person.FirstName?.Trim());
                SurnameValue.Text = NonEmpty(person.LastName?.Trim());
                BioValue.Text = NonEmpty(person.Bio?.Trim());
                BirthDateValue.Text = FormatDtString(person.BirthDate);
                PhoneValue.Text = NonEmpty(PhoneHelpers.Format(person.Phone?.Trim()));
                GenderValue.Text = MapGender(person.Gender);
                IsTrainerValue.Text = person.IsTrainer.HasValue ? (person.IsTrainer.Value ? LocalizationService.Get("About_Yes") : LocalizationService.Get("About_No")) : "—";
                WdsfIdValue.Text = NonEmpty(person.WdsfId?.Trim());
                CstsIdValue.Text = NonEmpty(person.CstsId?.Trim());

                // Active couples
                _activeCouples.Clear();
                foreach (var c in person.ActiveCouplesList ?? new List<ActiveCouple>())
                {
                    try
                    {
                        var manFirst = c.Man?.FirstName?.Trim() ?? string.Empty;
                        var manLast = c.Man?.LastName?.Trim() ?? string.Empty;
                        var womanFirst = c.Woman?.FirstName?.Trim() ?? string.Empty;
                        var womanLast = c.Woman?.LastName?.Trim() ?? string.Empty;

                        string man = string.IsNullOrWhiteSpace(manFirst) ? manLast : (string.IsNullOrWhiteSpace(manLast) ? manFirst : (manFirst + " " + manLast).Trim());
                        string woman = string.IsNullOrWhiteSpace(womanFirst) ? womanLast : (string.IsNullOrWhiteSpace(womanLast) ? womanFirst : (womanFirst + " " + womanLast).Trim());

                        string entry;
                        if (!string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman))
                            entry = man + " – " + woman;
                        else if (!string.IsNullOrWhiteSpace(man))
                            entry = man;
                        else if (!string.IsNullOrWhiteSpace(woman))
                            entry = woman;
                        else
                            entry = "(–)";

                        _activeCouples.Add(new ActiveCoupleDisplay { Id = c.Id, Text = entry });
                    }
                    catch { }
                }
                ActiveCouplesFrame.IsVisible = _activeCouples.Count > 0;

                // Toggle visibility per-row
                try { BioRow.IsVisible = !string.IsNullOrWhiteSpace(person.Bio); } catch { }
                try { BirthDateRow.IsVisible = !string.IsNullOrWhiteSpace(person.BirthDate); } catch { }
                try { PhoneRow.IsVisible = !string.IsNullOrWhiteSpace(person.Phone); } catch { }
                try { /* nationality removed */ } catch { }
                try { GenderRow.IsVisible = !string.IsNullOrWhiteSpace(person.Gender); } catch { }
                try { IsTrainerRow.IsVisible = person.IsTrainer.HasValue; } catch { }
                try { WdsfRow.IsVisible = !string.IsNullOrWhiteSpace(person.WdsfId); } catch { }
                try { CstsRow.IsVisible = !string.IsNullOrWhiteSpace(person.CstsId); } catch { }
                try { /* national id removed */ } catch { }

                try { ContactBorder.IsVisible = EmailRow.IsVisible || PhoneRow.IsVisible; } catch { }
                try { PersonalBorder.IsVisible = BioRow.IsVisible || BirthDateRow.IsVisible || GenderRow.IsVisible || IsTrainerRow.IsVisible; } catch { }
                try { IdsBorder.IsVisible = WdsfRow.IsVisible || CstsRow.IsVisible; } catch { }
            }
            catch (Exception ex)
            {
                ErrorLabel.IsVisible = true;
                ErrorLabel.Text = ex.Message;
            }
            finally
            {
                LoadingIndicator.IsRunning = false;
                LoadingIndicator.IsVisible = false;
                _loadRequested = false;
            }
        }

        private async void OnActiveCoupleSelected(object? sender, SelectionChangedEventArgs e)
        {
            try
            {
                if (e.CurrentSelection == null || e.CurrentSelection.Count == 0) return;
                var sel = e.CurrentSelection.FirstOrDefault() as ActiveCoupleDisplay;
                if (sel == null) return;
                try { ActiveCouplesCollection.SelectedItem = null; } catch { }
                var id = sel.Id;
                if (!string.IsNullOrWhiteSpace(id))
                {
                    await Shell.Current.GoToAsync($"{nameof(CouplePage)}?id={Uri.EscapeDataString(id)}");
                }
            }
            catch { }
        }

        private static string FormatDtString(string? s)
        {
            if (string.IsNullOrWhiteSpace(s)) return "—";
            if (DateTime.TryParse(s, out var dt)) return dt.ToLocalTime().ToString("dd.MM.yyyy");
            return s;
        }

        private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? "—" : s;

        

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

        private sealed class GraphQlResp<T>
        {
            [JsonPropertyName("data")] public T? Data { get; set; }
        }

        private sealed class PersonRespData
        {
            [JsonPropertyName("person")] public PersonDetail? Person { get; set; }
        }

        private sealed class PersonDetail
        {
            [JsonPropertyName("bio")] public string? Bio { get; set; }
            [JsonPropertyName("birthDate")] public string? BirthDate { get; set; }
            [JsonPropertyName("createdAt")] public string? CreatedAt { get; set; }
            [JsonPropertyName("cstsId")] public string? CstsId { get; set; }
            [JsonPropertyName("email")] public string? Email { get; set; }
            [JsonPropertyName("firstName")] public string? FirstName { get; set; }
            [JsonPropertyName("gender")] public string? Gender { get; set; }
            [JsonPropertyName("isTrainer")] public bool? IsTrainer { get; set; }
            [JsonPropertyName("lastName")] public string? LastName { get; set; }
            [JsonPropertyName("activeCouplesList")] public List<ActiveCouple>? ActiveCouplesList { get; set; }
            
            [JsonPropertyName("phone")] public string? Phone { get; set; }
            [JsonPropertyName("wdsfId")] public string? WdsfId { get; set; }

        }

        private sealed class ActiveCouple
        {
            [JsonPropertyName("id")] public string? Id { get; set; }
            [JsonPropertyName("man")] public ActivePerson? Man { get; set; }
            [JsonPropertyName("woman")] public ActivePerson? Woman { get; set; }
        }

        private sealed class ActivePerson
        {
            [JsonPropertyName("firstName")] public string? FirstName { get; set; }
            [JsonPropertyName("lastName")] public string? LastName { get; set; }
        }

        private sealed class ActiveCoupleDisplay
        {
            public string? Id { get; set; }
            public string Text { get; set; } = string.Empty;
        }

        
    }
}

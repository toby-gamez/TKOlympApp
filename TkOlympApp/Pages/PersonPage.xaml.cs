using System;
using System.Linq;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Concurrent;
using System.Threading;
using System.Threading.Tasks;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;
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
        private CancellationTokenSource? _cts;
        private readonly ObservableCollection<ActiveCoupleDisplay> _activeCouples = new();

        private static readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
        private static readonly ConcurrentDictionary<string, (PersonDetail Person, DateTime FetchedAt)> _personCache = new();
        private static readonly TimeSpan CacheTtl = TimeSpan.FromMinutes(5);

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
            
            // Subscribe to events
            if (PageRefresh != null)
                PageRefresh.Refreshing += OnRefresh;
            if (ActiveCouplesCollection != null)
                ActiveCouplesCollection.SelectionChanged += OnActiveCoupleSelected;
            
            _appeared = true;
            if (_loadRequested)
                await LoadAsync();
        }

        protected override void OnDisappearing()
        {
            // Unsubscribe from events to prevent memory leaks
            if (PageRefresh != null)
                PageRefresh.Refreshing -= OnRefresh;
            if (ActiveCouplesCollection != null)
                ActiveCouplesCollection.SelectionChanged -= OnActiveCoupleSelected;
            
            // Cancel any ongoing loads
            try { _cts?.Cancel(); } catch { }
            
            base.OnDisappearing();
        }

        private async void OnRefresh(object? sender, EventArgs e)
        {
            try
            {
                if (!string.IsNullOrWhiteSpace(_personId)) _personCache.TryRemove(_personId, out _);
                await LoadAsync();
            }
            finally
            {
                try { if (PageRefresh != null) PageRefresh.IsRefreshing = false; } catch { }
            }
        }

        private async Task LoadAsync()
        {
            ErrorLabel.IsVisible = false;

            try
            {
                // cancel any previous load
                try { _cts?.Cancel(); } catch { }
                _cts = new CancellationTokenSource();
                var ct = _cts.Token;

                if (string.IsNullOrWhiteSpace(_personId))
                {
                    ErrorLabel.IsVisible = true;
                    ErrorLabel.Text = "Missing personId";
                    return;
                }

                // return cached basic data if fresh
                if (_personCache.TryGetValue(_personId!, out var cached) && DateTime.UtcNow - cached.FetchedAt < CacheTtl)
                {
                    ApplyBasicPerson(cached.Person);
                    _ = LoadExtrasAsync(_personId!, ct);
                    return;
                }

                // Primary (fast) query without nested lists
                var primaryQuery = "query Primary { person(id: \"" + _personId + "\") { bio birthDate cstsId email firstName prefixTitle suffixTitle gender isTrainer lastName phone wdsfId } }";
                var gqlReq = new { query = primaryQuery };
                var json = JsonSerializer.Serialize(gqlReq, _jsonOptions);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await AuthService.Http.PostAsync("", content, ct);
                resp.EnsureSuccessStatusCode();

                var body = await resp.Content.ReadAsStringAsync(ct);
                var parsed = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(body, _jsonOptions);
                var person = parsed?.Data?.Person;
                if (person == null)
                {
                    ErrorLabel.IsVisible = true;
                    ErrorLabel.Text = LocalizationService.Get("NotFound_Person") ?? "Person not found";
                    return;
                }

                _personCache[_personId!] = (person, DateTime.UtcNow);
                ApplyBasicPerson(person);

                // load extras in background
                _ = LoadExtrasAsync(_personId!, ct);
            }
            catch (OperationCanceledException) { }
            catch (Exception ex)
            {
                ErrorLabel.IsVisible = true;
                ErrorLabel.Text = ex.Message;
            }
            finally
            {
                _loadRequested = false;
            }
        }

        private void ApplyBasicPerson(PersonDetail person)
        {
            static string FormatPrefixName(string? prefix, string? name)
            {
                prefix = prefix?.Trim();
                name = name?.Trim();
                if (string.IsNullOrWhiteSpace(prefix) && string.IsNullOrWhiteSpace(name)) return "—";
                if (string.IsNullOrWhiteSpace(name)) return prefix ?? "—";
                if (string.IsNullOrWhiteSpace(prefix)) return name!;
                return prefix + " " + name;
            }

            static string FormatNameSuffix(string? name, string? suffix)
            {
                name = name?.Trim();
                suffix = suffix?.Trim();
                if (string.IsNullOrWhiteSpace(name) && string.IsNullOrWhiteSpace(suffix)) return "—";
                if (string.IsNullOrWhiteSpace(name)) return suffix ?? "—";
                if (string.IsNullOrWhiteSpace(suffix)) return name!;
                return name + " " + suffix;
            }

            MainThread.BeginInvokeOnMainThread(() =>
            {
                NameValue.Text = FormatPrefixName(person.PrefixTitle, person.FirstName);
                SurnameValue.Text = FormatNameSuffix(person.LastName, person.SuffixTitle);
                BioValue.Text = NonEmpty(person.Bio?.Trim());
                BirthDateValue.Text = FormatDtString(person.BirthDate);
                PhoneValue.Text = NonEmpty(PhoneHelpers.Format(person.Phone?.Trim()));
                GenderValue.Text = MapGender(person.Gender);
                IsTrainerValue.Text = person.IsTrainer.HasValue ? (person.IsTrainer.Value ? LocalizationService.Get("About_Yes") : LocalizationService.Get("About_No")) : "—";
                WdsfIdValue.Text = NonEmpty(person.WdsfId?.Trim());
                CstsIdValue.Text = NonEmpty(person.CstsId?.Trim());

                try { BioRow.IsVisible = !string.IsNullOrWhiteSpace(person.Bio); } catch { }
                try { BirthDateRow.IsVisible = !string.IsNullOrWhiteSpace(person.BirthDate); } catch { }
                try { PhoneRow.IsVisible = !string.IsNullOrWhiteSpace(person.Phone); } catch { }
                try { GenderRow.IsVisible = !string.IsNullOrWhiteSpace(person.Gender); } catch { }
                try { IsTrainerRow.IsVisible = person.IsTrainer.HasValue; } catch { }
                try { WdsfRow.IsVisible = !string.IsNullOrWhiteSpace(person.WdsfId); } catch { }
                try { CstsRow.IsVisible = !string.IsNullOrWhiteSpace(person.CstsId); } catch { }

                try { ContactBorder.IsVisible = EmailRow.IsVisible || PhoneRow.IsVisible; } catch { }
                try { PersonalBorder.IsVisible = BioRow.IsVisible || BirthDateRow.IsVisible || GenderRow.IsVisible || IsTrainerRow.IsVisible; } catch { }
                try { IdsBorder.IsVisible = WdsfRow.IsVisible || CstsRow.IsVisible; } catch { }
            });
        }

        private async Task LoadExtrasAsync(string personId, CancellationToken ct)
        {
            try
            {
                var extrasQuery = "query Extras { person(id: \"" + personId + "\") { activeCouplesList { id man { firstName lastName } woman { firstName lastName } } cohortMembershipsList { cohort { colorRgb id name ordering isVisible } } } }";
                var gqlReq = new { query = extrasQuery };
                var json = JsonSerializer.Serialize(gqlReq, _jsonOptions);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await AuthService.Http.PostAsync("", content, ct);
                resp.EnsureSuccessStatusCode();

                var body = await resp.Content.ReadAsStringAsync(ct);
                var parsed = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(body, _jsonOptions);
                var person = parsed?.Data?.Person;
                if (person == null) return;

                if (_personCache.TryGetValue(personId, out var existing))
                {
                    var merged = existing.Person;
                    try { merged.ActiveCouplesList = person.ActiveCouplesList; } catch { }
                    try { merged.CohortMembershipsList = person.CohortMembershipsList; } catch { }
                    _personCache[personId] = (merged, DateTime.UtcNow);
                }

                MainThread.BeginInvokeOnMainThread(() =>
                {
                    try
                    {
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
                    }
                    catch { }
                });

                MainThread.BeginInvokeOnMainThread(() =>
                {
                    try
                    {
                        CohortDots.Children.Clear();
                        var cohortsList = (person.CohortMembershipsList ?? new List<CohortMembership>())
                            .Where(m => m?.Cohort?.IsVisible != false)
                            .OrderBy(m => m?.Cohort?.Ordering ?? int.MaxValue)
                            .ToList();
                        foreach (var membership in cohortsList)
                        {
                            try
                            {
                                var c = membership?.Cohort;
                                if (c == null) continue;
                                var name = c.Name ?? string.Empty;
                                var colorBrush = CohortColorHelper.ParseColorBrush(c.ColorRgb) ?? new Microsoft.Maui.Controls.SolidColorBrush(Microsoft.Maui.Graphics.Colors.LightGray);

                                var row = new Microsoft.Maui.Controls.Grid { VerticalOptions = Microsoft.Maui.Controls.LayoutOptions.Center, HorizontalOptions = Microsoft.Maui.Controls.LayoutOptions.Fill };
                                row.ColumnDefinitions.Add(new Microsoft.Maui.Controls.ColumnDefinition { Width = Microsoft.Maui.GridLength.Star });
                                row.ColumnDefinitions.Add(new Microsoft.Maui.Controls.ColumnDefinition { Width = Microsoft.Maui.GridLength.Auto });

                                var nameLabel = new Microsoft.Maui.Controls.Label { Text = name, VerticalOptions = Microsoft.Maui.Controls.LayoutOptions.Center, HorizontalOptions = Microsoft.Maui.Controls.LayoutOptions.Start };
                                row.Add(nameLabel);

                                var dot = new Microsoft.Maui.Controls.Border
                                {
                                    WidthRequest = 20,
                                    HeightRequest = 20,
                                    Padding = 0,
                                    Margin = new Microsoft.Maui.Thickness(0),
                                    HorizontalOptions = Microsoft.Maui.Controls.LayoutOptions.End,
                                    VerticalOptions = Microsoft.Maui.Controls.LayoutOptions.Center,
                                    Background = colorBrush,
                                    Stroke = null,
                                    StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 }
                                };
                                row.Add(dot, 1, 0);

                                CohortDots.Children.Add(row);
                            }
                            catch { }
                        }

                        CohortDots.IsVisible = CohortDots.Children.Count > 0;
                        try { CohortsFrame.IsVisible = CohortDots.IsVisible; } catch { CohortsFrame.IsVisible = false; }
                    }
                    catch { try { CohortDots.IsVisible = false; CohortsFrame.IsVisible = false; } catch { } }
                });
            }
            catch (OperationCanceledException) { }
            catch { }
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
            [JsonPropertyName("prefixTitle")] public string? PrefixTitle { get; set; }
            [JsonPropertyName("suffixTitle")] public string? SuffixTitle { get; set; }
            [JsonPropertyName("gender")] public string? Gender { get; set; }
            [JsonPropertyName("isTrainer")] public bool? IsTrainer { get; set; }
            [JsonPropertyName("lastName")] public string? LastName { get; set; }
            [JsonPropertyName("activeCouplesList")] public List<ActiveCouple>? ActiveCouplesList { get; set; }
            [JsonPropertyName("cohortMembershipsList")] public List<CohortMembership>? CohortMembershipsList { get; set; }
            
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

        private sealed class CohortMembership
        {
            [JsonPropertyName("cohort")] public Cohort? Cohort { get; set; }
        }

        private sealed class Cohort
        {
            [JsonPropertyName("id")] public string? Id { get; set; }
            [JsonPropertyName("name")] public string? Name { get; set; }
            [JsonPropertyName("colorRgb")] public string? ColorRgb { get; set; }
            [JsonPropertyName("ordering")] public int? Ordering { get; set; }
            [JsonPropertyName("isVisible")] public bool? IsVisible { get; set; }
        }

        private sealed class ActiveCoupleDisplay
        {
            public string? Id { get; set; }
            public string Text { get; set; } = string.Empty;
        }

        
    }
}

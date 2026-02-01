using Microsoft.Maui.Controls;
using System;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Helpers;
using System.Collections.ObjectModel;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Net.Http;
using System.ComponentModel;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class RegistrationPage : ContentPage
{
    private long _eventId;
    private RegistrationOption? _selectedOption;
    private bool _trainerReservationNotAllowed = false;
    private EventService.EventDetails? _currentEvent;
    

    public long EventId
    {
        get => _eventId;
        set
        {
            _eventId = value;
            _ = LoadAsync();
        }
    }

    public RegistrationPage()
    {
        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"RegistrationPage XAML init error: {ex}");
            try
            {
                Dispatcher.Dispatch(async () =>
                {
                    try { await DisplayAlertAsync("XAML Error", ex.Message, "OK"); } catch { }
                });
            }
            catch { }
        }
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        
        // Subscribe to events
        if (SelectionCollection != null)
            SelectionCollection.SelectionChanged += OnSelectionChanged;
        if (ConfirmButton != null)
            ConfirmButton.Clicked += OnConfirmClicked;
    }

    protected override void OnDisappearing()
    {
        // Unsubscribe from events to prevent memory leaks
        if (SelectionCollection != null)
            SelectionCollection.SelectionChanged -= OnSelectionChanged;
        if (ConfirmButton != null)
            ConfirmButton.Clicked -= OnConfirmClicked;
        
        base.OnDisappearing();
    }

    private sealed record RegistrationOption(string DisplayText, string Kind, string? Id);
    private sealed class TrainerOption : INotifyPropertyChanged
    {
        private int _count;
        public string DisplayText { get; set; }
        public string Name { get; set; }
        public string? Id { get; set; }
        public int Count
        {
            get => _count;
            set
            {
                if (_count != value)
                {
                    _count = value;
                    OnPropertyChanged(nameof(Count));
                }
            }
        }

        public TrainerOption(string displayText, string name, int count, string? id)
        {
            DisplayText = displayText;
            Name = name;
            _count = count;
            Id = id;
        }

        public event PropertyChangedEventHandler? PropertyChanged;
        private void OnPropertyChanged(string propertyName) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }


    private async Task LoadAsync()
    {
        try
        {
            if (EventId == 0) return;
            var ev = await EventService.GetEventAsync(EventId);
            _currentEvent = ev;
            if (ev == null)
            {
                TitleLabel.Text = LocalizationService.Get("NotFound_Event");
                EventInfoLabel.Text = string.Empty;
                return;
            }

            TitleLabel.Text = string.IsNullOrWhiteSpace(ev.Name) ? LocalizationService.Get("EventPage_Title") : ev.Name;
            var loc = string.IsNullOrWhiteSpace(ev.LocationText) ? string.Empty : (LocalizationService.Get("Event_Location_Prefix") ?? "Místo konání: ") + ev.LocationText;
            var dates = DateHelpers.ToFriendlyDateTimeString(ev.Since);
            if (!string.IsNullOrWhiteSpace(DateHelpers.ToFriendlyDateTimeString(ev.Until)))
                dates = dates + " – " + DateHelpers.ToFriendlyDateTimeString(ev.Until);
            EventInfoLabel.Text = string.Join("\n", new[] { loc, dates }.Where(s => !string.IsNullOrWhiteSpace(s)));

            // Show EventId for debugging / user info
            try
            {
                EventIdLabel.Text = EventId != 0 ? EventId.ToString() : string.Empty;
            }
            catch { }

                    // Logic: trainer reservations are not allowed for events of type "lesson" or "group"
                    _trainerReservationNotAllowed = string.Equals(ev.Type, "lesson", StringComparison.OrdinalIgnoreCase) ||
                                                     string.Equals(ev.Type, "group", StringComparison.OrdinalIgnoreCase);

            // Load and show current user info and selection list
            await LoadMyCouplesAsync();
            // hide trainer selection initially
            try
            {
                TrainerSelectionCollection.IsVisible = false;
                TrainerSelectionHeader.IsVisible = false;
            }
            catch { }
        }
        catch (Exception ex)
        {
            try
            {
                var title = LocalizationService.Get("Registration_Load_Error_Title") ?? "Chyba při načítání";
                var ok = LocalizationService.Get("Button_OK") ?? "OK";
                await DisplayAlertAsync(title, ex.Message, ok);
            }
            catch { }
        }
    }

    private async Task LoadMyCouplesAsync()
    {
        try
        {
            try { await UserService.InitializeAsync(); } catch { }
            var me = await UserService.GetCurrentUserAsync();
            if (me == null)
            {
                CurrentUserLabel.Text = string.Empty;
                CurrentUserLabel.IsVisible = false;
                ConfirmButton.IsEnabled = false;
                return;
            }
            var name = string.IsNullOrWhiteSpace(me.UJmeno) ? me.ULogin : me.UJmeno;
            var surname = string.IsNullOrWhiteSpace(me.UPrijmeni) ? string.Empty : me.UPrijmeni;
            var displayName = string.IsNullOrWhiteSpace(surname) ? name : $"{name} {surname}";
            string? pidDisplay = null;
            try { pidDisplay = UserService.CurrentPersonId; } catch { pidDisplay = null; }
            if (string.IsNullOrWhiteSpace(pidDisplay)) pidDisplay = "není";
            displayName = $"{displayName} ({pidDisplay})";
            CurrentUserLabel.Text = displayName;
            CurrentUserLabel.IsVisible = true;
            // Show current user's personId — use only proxy/person id (never fallback to me.Id)
            try
            {
                string? selectedPersonId = null;
                try { selectedPersonId = UserService.CurrentPersonId; } catch { selectedPersonId = null; }
                // Do NOT fallback to me.Id — show 'není' when proxy id not present
                if (string.IsNullOrWhiteSpace(selectedPersonId))
                {
                    PersonIdLabel.Text = "není";
                    ConfirmButton.IsEnabled = false;
                }
                else
                {
                    PersonIdLabel.Text = selectedPersonId;
                }
            }
            catch { PersonIdLabel.IsVisible = false; }
            CoupleIdLabel.Text = string.Empty;
            CoupleIdLabel.IsVisible = false;
            ConfirmButton.IsEnabled = false;
            // Load couples (global list for now) and show under user info
            try
            {
                var couples = await UserService.GetActiveCouplesFromUsersAsync();
                // Build selection: self + optional couples
                var options = new List<RegistrationOption>();
                // Determine already-registered persons/couples by querying event instances (same approach as EditRegistrationsPage)
                var registeredPersonNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                var registeredCoupleIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                try
                {
                    var startRange = DateTime.Now.Date.AddYears(-1).ToString("o");
                    var endRange = DateTime.Now.Date.AddYears(1).ToString("o");
                    var queryObj = new
                    {
                        query = "query($startRange: Datetime!, $endRange: Datetime!) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange) { id event { id name eventRegistrationsList { id person { firstName lastName } couple { id man { firstName lastName } woman { firstName lastName } } } } } }",
                        variables = new { startRange = startRange, endRange = endRange }
                    };

                    var json = JsonSerializer.Serialize(queryObj);
                    using var content = new StringContent(json, Encoding.UTF8, "application/json");
                    using var resp = await AuthService.Http.PostAsync("", content);
                    if (resp.IsSuccessStatusCode)
                    {
                        var body = await resp.Content.ReadAsStringAsync();
                        try
                        {
                            using var doc = JsonDocument.Parse(body);
                            if (!doc.RootElement.TryGetProperty("data", out var data)) { }
                            else if (data.TryGetProperty("eventInstancesForRangeList", out var instances) && instances.ValueKind == JsonValueKind.Array)
                            {
                                foreach (var inst in instances.EnumerateArray())
                                {
                                    try
                                    {
                                        if (!inst.TryGetProperty("event", out var ev) || ev.ValueKind == JsonValueKind.Null) continue;
                                        // filter to current EventId
                                        try
                                        {
                                            if (ev.TryGetProperty("id", out var evIdEl))
                                            {
                                                long parsedEvId = 0;
                                                if (evIdEl.ValueKind == JsonValueKind.Number && evIdEl.TryGetInt64(out var n)) parsedEvId = n;
                                                else parsedEvId = long.TryParse(evIdEl.GetRawText().Trim('"'), out var t) ? t : 0;
                                                if (parsedEvId != 0 && parsedEvId != EventId) continue;
                                            }
                                        }
                                        catch { }

                                        if (!ev.TryGetProperty("eventRegistrationsList", out var regs) || regs.ValueKind != JsonValueKind.Array) continue;
                                        foreach (var reg in regs.EnumerateArray())
                                        {
                                            try
                                            {
                                                // collect couple id if present
                                                if (reg.TryGetProperty("couple", out var coupleEl) && coupleEl.ValueKind != JsonValueKind.Null)
                                                {
                                                    if (coupleEl.TryGetProperty("id", out var cidEl))
                                                    {
                                                        var cid = cidEl.GetRawText().Trim('"');
                                                        if (!string.IsNullOrWhiteSpace(cid)) registeredCoupleIds.Add(cid);
                                                    }
                                                }

                                                // collect person full name if present
                                                if (reg.TryGetProperty("person", out var personEl) && personEl.ValueKind != JsonValueKind.Null)
                                                {
                                                    var pf = personEl.TryGetProperty("firstName", out var pff) ? pff.GetString() ?? string.Empty : string.Empty;
                                                    var pl = personEl.TryGetProperty("lastName", out var pll) ? pll.GetString() ?? string.Empty : string.Empty;
                                                    var pFull = string.IsNullOrWhiteSpace(pf) ? pl : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                                                    if (!string.IsNullOrWhiteSpace(pFull)) registeredPersonNames.Add(pFull);
                                                }
                                            }
                                            catch { }
                                        }
                                    }
                                    catch { }
                                }
                            }
                        }
                        catch { }
                    }
                }
                catch { }
                // Use proxy/person id only — do not add 'self' option when CurrentPersonId is missing
                string? myPersonIdOption = null;
                try { myPersonIdOption = UserService.CurrentPersonId; } catch { myPersonIdOption = null; }
                if (!string.IsNullOrWhiteSpace(myPersonIdOption))
                {
                    var mePrefix = LocalizationService.Get("Registration_My_Prefix") ?? "Já: ";
                    options.Add(new RegistrationOption($"{mePrefix}{CurrentUserLabel.Text}", "self", myPersonIdOption));
                }
                if (couples != null && couples.Count > 0)
                {
                    foreach (var c in couples)
                    {
                        var man = string.IsNullOrWhiteSpace(c.ManName) ? "" : c.ManName;
                        var woman = string.IsNullOrWhiteSpace(c.WomanName) ? "" : c.WomanName;
                        var text = (man + " - " + woman).Trim();
                        if (!string.IsNullOrEmpty(text)) options.Add(new RegistrationOption(text, "couple", c.Id));
                    }
                }

                // Filter out options that are already registered for this event
                var filtered = new List<RegistrationOption>();
                var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
                var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
                var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : string.IsNullOrWhiteSpace(myLast) ? myFirst : (myFirst + " " + myLast).Trim();
                foreach (var opt in options)
                {
                    try
                    {
                        if (opt.Kind == "self")
                        {
                            if (string.IsNullOrWhiteSpace(myFull) || registeredPersonNames.Contains(myFull))
                            {
                                continue; // already registered
                            }
                        }
                        else if (opt.Kind == "couple")
                        {
                            // match by couple id when possible
                            if (!string.IsNullOrWhiteSpace(opt.Id) && registeredCoupleIds.Contains(opt.Id)) continue;
                            // fallback: match by display text (man - woman)
                            var key = opt.DisplayText?.Trim() ?? string.Empty;
                            if (!string.IsNullOrWhiteSpace(key) && registeredPersonNames.Contains(key)) continue;
                        }
                        filtered.Add(opt);
                    }
                    catch { filtered.Add(opt); }
                }

                SelectionCollection.IsVisible = true;
                SelectionCollection.ItemsSource = filtered;
            }
            catch { }
        }
        catch (Exception ex)
        {
            try
            {
                var title = LocalizationService.Get("Registration_Load_Error_Title") ?? "Chyba při načítání";
                var ok = LocalizationService.Get("Button_OK") ?? "OK";
                await DisplayAlertAsync(title, ex.Message, ok);
            }
            catch { }
        }
    }

    private async void OnConfirmClicked(object? sender, EventArgs e)
    {
        try
        {
            if (EventId == 0) return;
            if (_selectedOption == null)
            {
                await DisplayAlertAsync(LocalizationService.Get("Registration_Error_Title") ?? "Chyba", LocalizationService.Get("Registration_NoSelection") ?? "Vyberte, koho chcete registrovat.", LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            string? personId = null;
            string? coupleId = null;
            if (_selectedOption.Kind == "self") personId = _selectedOption.Id;
            else if (_selectedOption.Kind == "couple") coupleId = _selectedOption.Id;

            var success = false;
            try
            {
                success = await CreateRegistrationAsync(personId, coupleId);
            }
            catch (Exception ex)
            {
                await DisplayAlertAsync(LocalizationService.Get("Registration_Error_Title") ?? "Chyba", ex.Message, LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            if (success)
            {
                var msg = LocalizationService.Get("Registration_Confirm_Message") ?? "Registrace odeslána";
                await ShowSuccessAndGoBackAsync(msg);
            }
        }
        catch { }
    }

    private void OnSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var sel = e.CurrentSelection?.FirstOrDefault();
        if (sel is RegistrationOption ro)
        {
            _selectedOption = ro;
            ConfirmButton.IsEnabled = true;
            // Show chosen ids
            try
            {
                if (ro.Kind == "self")
                {
                    PersonIdLabel.Text = ro.Id ?? string.Empty;
                    CoupleIdLabel.Text = string.Empty;
                }
                else if (ro.Kind == "couple")
                {
                    CoupleIdLabel.Text = ro.Id ?? string.Empty;
                    PersonIdLabel.Text = string.Empty;
                }
            }
            catch { }
            // When a registration target is selected, show trainer selection if allowed
            try
            {
                if (!_trainerReservationNotAllowed && _currentEvent?.EventTrainersList != null && _currentEvent.EventTrainersList.Count > 0)
                {
                    _ = LoadTrainerSelectionAsync();
                }
                else
                {
                    TrainerSelectionCollection.IsVisible = false;
                    TrainerSelectionHeader.IsVisible = false;
                }
            }
            catch { }
        }
        else
        {
            _selectedOption = null;
            ConfirmButton.IsEnabled = false;
            PersonIdLabel.Text = string.Empty;
            CoupleIdLabel.Text = string.Empty;
            try
            {
                TrainerSelectionCollection.IsVisible = false;
                TrainerSelectionHeader.IsVisible = false;
            }
            catch { }
        }
    }

    private async Task LoadTrainerSelectionAsync()
    {
        try
        {
            var ev = _currentEvent;
            if (ev == null) return;

            // Build trainer options from event's trainer list (prefer ids when available)
            var options = new List<TrainerOption>();
            if (ev.EventTrainersList != null)
            {
                foreach (var t in ev.EventTrainersList.OrderBy(x => (x?.Name ?? string.Empty).Trim()))
                {
                    var name = (t?.Name ?? string.Empty).Trim();
                    options.Add(new TrainerOption(name, name, 0, t?.Id));
                }
            }

            TrainerSelectionCollection.ItemsSource = options;
            TrainerSelectionHeader.IsVisible = true;
            TrainerSelectionCollection.IsVisible = true;
        }
        catch { }
    }

    private void OnTrainerSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        // Not used anymore (selection disabled)
    }

    private void OnTrainerCountChanged(object sender, ValueChangedEventArgs e)
    {
        try
        {
            if (sender is Stepper s && s.BindingContext is TrainerOption to)
            {
                to.Count = (int)e.NewValue;
            }
        }
        catch { }
    }

    private void OnTrainerPlusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is TrainerOption to)
            {
                if (to.Count < 10) to.Count = to.Count + 1;
            }
        }
        catch { }
    }

    private void OnTrainerMinusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is TrainerOption to)
            {
                if (to.Count > 0) to.Count = to.Count - 1;
            }
        }
        catch { }
    }

    private async Task<bool> CreateRegistrationAsync(string? personId, string? coupleId)
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };

        // Ensure we have either a personId or coupleId to send
        if (string.IsNullOrWhiteSpace(personId) && string.IsNullOrWhiteSpace(coupleId))
        {
            throw new InvalidOperationException(LocalizationService.Get("Registration_NoSelection") ?? "Vyberte, koho chcete registrovat.");
        }

        // Build variables payload with IDs as strings to avoid escaping issues
        var reg = new Dictionary<string, object>
        {
            ["eventId"] = EventId.ToString()
        };

        if (!string.IsNullOrWhiteSpace(personId))
        {
            // Send personId as-is (may be non-numeric proxy/person id)
            reg["personId"] = personId;
        }
        if (!string.IsNullOrWhiteSpace(coupleId))
        {
            if (long.TryParse(coupleId, out var cid)) reg["coupleId"] = cid.ToString();
            else throw new InvalidOperationException(LocalizationService.Get("Registration_IdNumeric") ?? "CoupleId must be numeric.");
        }

        // If trainer reservations are not allowed, include an empty lessons array inside each registration
        if (_trainerReservationNotAllowed)
        {
            reg["lessons"] = new List<Dictionary<string, object>>();
        }
        else
        {
            try
            {
                // collect chosen lessons per trainer (trainerId -> lessonCount)
                var lessonsList = new List<Dictionary<string, object>>();
                if (TrainerSelectionCollection.ItemsSource is IEnumerable<TrainerOption> tos)
                {
                    foreach (var t in tos)
                    {
                        if (t != null && t.Count > 0)
                        {
                            if (string.IsNullOrWhiteSpace(t.Id))
                            {
                                throw new InvalidOperationException($"Missing trainerId for trainer: {t.Name}");
                            }
                            lessonsList.Add(new Dictionary<string, object>
                            {
                                ["trainerId"] = t.Id!,
                                ["lessonCount"] = t.Count
                            });
                        }
                    }
                }
                if (lessonsList.Count > 0) reg["lessons"] = lessonsList;
            }
            catch { }
        }

        var clientMutationId = Guid.NewGuid().ToString();
        var variables = new Dictionary<string, object>
        {
            ["input"] = new Dictionary<string, object>
            {
                ["registrations"] = new List<Dictionary<string, object>> { reg },
                ["clientMutationId"] = clientMutationId
            }
        };

        // lessons are now placed inside each registration (`reg`) above

        var gql = new Dictionary<string, object>
        {
            ["query"] = "mutation RegisterToEvent($input: RegisterToEventManyInput!) { registerToEventMany(input: $input) { eventRegistrations { id } } }",
            ["variables"] = variables
        };

        var json = JsonSerializer.Serialize(gql, options);
        // Do not show raw GraphQL/mutation payload in UI (MutationPreview hidden)

        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content);
        var body = await resp.Content.ReadAsStringAsync();

        try
        {
            var data = JsonSerializer.Deserialize<GraphQlResponse<RegisterToEventManyData>>(body, options);
            if (data?.Errors != null && data.Errors.Count > 0)
            {
                var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
                throw new InvalidOperationException(msg);
            }
            // success if we have at least one returned registration id
            return data?.Data?.RegisterToEventMany?.EventRegistrations != null && data.Data.RegisterToEventMany.EventRegistrations.Count > 0;
        }
        catch (JsonException)
        {
            if (!resp.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
            }
            return resp.IsSuccessStatusCode;
        }
    }

    private async Task ShowSuccessAndGoBackAsync(string message)
    {
        try
        {
            // set localized message if available
            SuccessText.Text = message;
        }
        catch { }

        try
        {
            SuccessOverlay.IsVisible = true;
            // start from a small scale for a pop animation
            SuccessIcon.Scale = 0.6;
            await SuccessIcon.ScaleToAsync(1.1, 300, Easing.CubicOut);
            await SuccessIcon.ScaleToAsync(1.0, 150, Easing.CubicIn);
            // keep overlay briefly visible then navigate back
            await Task.Delay(900);
            try { await Shell.Current.GoToAsync(".."); } catch { }
        }
        catch { }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class RegisterToEventManyData
    {
        [JsonPropertyName("registerToEventMany")] public RegisterToEventManyPayload? RegisterToEventMany { get; set; }
    }

    private sealed class RegisterToEventManyPayload
    {
        [JsonPropertyName("eventRegistrations")] public List<EventRegistrationResult>? EventRegistrations { get; set; }
    }

    private sealed class EventRegistrationResult
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
    }
}


using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class RegistrationViewModel : ViewModelBase
{
    private readonly IAuthService _authService;
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private EventDetails? _currentEvent;

    [ObservableProperty]
    private long _eventId;

    [ObservableProperty]
    private string _titleText = string.Empty;

    [ObservableProperty]
    private string _eventInfoText = string.Empty;

    [ObservableProperty]
    private string _currentUserText = string.Empty;

    [ObservableProperty]
    private bool _currentUserVisible = false;

    [ObservableProperty]
    private string _eventIdText = string.Empty;

    [ObservableProperty]
    private string _personIdText = string.Empty;

    [ObservableProperty]
    private string _coupleIdText = string.Empty;

    [ObservableProperty]
    private bool _confirmEnabled = false;

    [ObservableProperty]
    private bool _trainerSelectionVisible = false;

    [ObservableProperty]
    private bool _trainerSelectionHeaderVisible = false;

    [ObservableProperty]
    private bool _successOverlayVisible = false;

    [ObservableProperty]
    private string _successText = string.Empty;

    [ObservableProperty]
    private RegistrationOption? _selectedOption;

    private bool _trainerReservationNotAllowed = false;

    public ObservableCollection<RegistrationOption> SelectionOptions { get; } = new();
    public ObservableCollection<TrainerOption> TrainerOptions { get; } = new();

    public RegistrationViewModel(
        IAuthService authService,
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    partial void OnEventIdChanged(long value)
    {
        if (value != 0)
        {
            _ = LoadEventAsync();
        }
    }

    partial void OnSelectedOptionChanged(RegistrationOption? value)
    {
        if (value != null)
        {
            ConfirmEnabled = true;
            // Show chosen ids
            if (value.Kind == "self")
            {
                PersonIdText = value.Id ?? string.Empty;
                CoupleIdText = string.Empty;
            }
            else if (value.Kind == "couple")
            {
                CoupleIdText = value.Id ?? string.Empty;
                PersonIdText = string.Empty;
            }

            // Show trainer selection if allowed
            if (!_trainerReservationNotAllowed && _currentEvent?.EventTrainersList != null && _currentEvent.EventTrainersList.Count > 0)
            {
                _ = LoadTrainerSelectionAsync();
            }
            else
            {
                TrainerSelectionVisible = false;
                TrainerSelectionHeaderVisible = false;
            }
        }
        else
        {
            ConfirmEnabled = false;
            PersonIdText = string.Empty;
            CoupleIdText = string.Empty;
            TrainerSelectionVisible = false;
            TrainerSelectionHeaderVisible = false;
        }
    }

    public override async Task InitializeAsync()
    {
        await base.InitializeAsync();
        if (EventId != 0)
        {
            await LoadEventAsync();
        }
    }

    private async Task LoadEventAsync()
    {
        try
        {
            if (EventId == 0) return;

            var ev = await _eventService.GetEventAsync(EventId);
            _currentEvent = ev;

            if (ev == null)
            {
                TitleText = LocalizationService.Get("NotFound_Event") ?? "Event not found";
                EventInfoText = string.Empty;
                return;
            }

            TitleText = string.IsNullOrWhiteSpace(ev.Name) 
                ? LocalizationService.Get("EventPage_Title") ?? "Event" 
                : ev.Name;

            var loc = string.IsNullOrWhiteSpace(ev.LocationText) 
                ? string.Empty 
                : (LocalizationService.Get("Event_Location_Prefix") ?? "Místo konání: ") + ev.LocationText;
            var dates = DateHelpers.ToFriendlyDateTimeString(ev.Since);
            if (!string.IsNullOrWhiteSpace(DateHelpers.ToFriendlyDateTimeString(ev.Until)))
                dates = dates + " – " + DateHelpers.ToFriendlyDateTimeString(ev.Until);
            EventInfoText = string.Join("\n", new[] { loc, dates }.Where(s => !string.IsNullOrWhiteSpace(s)));

            EventIdText = EventId != 0 ? EventId.ToString() : string.Empty;

            // Logic: trainer reservations are not allowed for events of type "lesson" or "group"
            _trainerReservationNotAllowed = string.Equals(ev.Type, "lesson", StringComparison.OrdinalIgnoreCase) ||
                                             string.Equals(ev.Type, "group", StringComparison.OrdinalIgnoreCase);

            await LoadMyCouplesAsync();
        }
        catch (Exception ex)
        {
            // Error handling - notify user
            var title = LocalizationService.Get("Registration_Load_Error_Title") ?? "Chyba při načítání";
            // In ViewModel we can't show alerts directly, we need a way to communicate this back to the view
            // For now, we'll use a simple approach: set Title to error
            TitleText = $"{title}: {ex.Message}";
        }
    }

    private async Task LoadMyCouplesAsync()
    {
        try
        {
            await _userService.InitializeAsync();
            var me = await _userService.GetCurrentUserAsync();
            if (me == null)
            {
                CurrentUserText = string.Empty;
                CurrentUserVisible = false;
                ConfirmEnabled = false;
                return;
            }

            var name = string.IsNullOrWhiteSpace(me.UJmeno) ? me.ULogin : me.UJmeno;
            var surname = string.IsNullOrWhiteSpace(me.UPrijmeni) ? string.Empty : me.UPrijmeni;
            var displayName = string.IsNullOrWhiteSpace(surname) ? name : $"{name} {surname}";
            string? pidDisplay = null;
            try { pidDisplay = _userService.CurrentPersonId; } catch { pidDisplay = null; }
            if (string.IsNullOrWhiteSpace(pidDisplay)) pidDisplay = "není";
            displayName = $"{displayName} ({pidDisplay})";
            CurrentUserText = displayName;
            CurrentUserVisible = true;

            // Show current user's personId
            string? selectedPersonId = null;
            try { selectedPersonId = _userService.CurrentPersonId; } catch { selectedPersonId = null; }
            if (string.IsNullOrWhiteSpace(selectedPersonId))
            {
                PersonIdText = "není";
                ConfirmEnabled = false;
            }
            else
            {
                PersonIdText = selectedPersonId;
            }

            CoupleIdText = string.Empty;
            ConfirmEnabled = false;

            // Load couples and build selection options
            var couples = await _userService.GetActiveCouplesFromUsersAsync();
            var options = new List<RegistrationOption>();

            // Determine already-registered persons/couples
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
                using var resp = await _authService.Http.PostAsync("", content);
                if (resp.IsSuccessStatusCode)
                {
                    var body = await resp.Content.ReadAsStringAsync();
                    using var doc = JsonDocument.Parse(body);
                    if (doc.RootElement.TryGetProperty("data", out var data) && 
                        data.TryGetProperty("eventInstancesForRangeList", out var instances) && 
                        instances.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var inst in instances.EnumerateArray())
                        {
                            if (!inst.TryGetProperty("event", out var ev) || ev.ValueKind == JsonValueKind.Null) continue;
                            
                            // Filter to current EventId
                            if (ev.TryGetProperty("id", out var evIdEl))
                            {
                                long parsedEvId = 0;
                                if (evIdEl.ValueKind == JsonValueKind.Number && evIdEl.TryGetInt64(out var n)) 
                                    parsedEvId = n;
                                else 
                                    parsedEvId = long.TryParse(evIdEl.GetRawText().Trim('"'), out var t) ? t : 0;
                                if (parsedEvId != 0 && parsedEvId != EventId) continue;
                            }

                            if (!ev.TryGetProperty("eventRegistrationsList", out var regs) || regs.ValueKind != JsonValueKind.Array) 
                                continue;
                            
                            foreach (var reg in regs.EnumerateArray())
                            {
                                // Collect couple id if present
                                if (reg.TryGetProperty("couple", out var coupleEl) && coupleEl.ValueKind != JsonValueKind.Null)
                                {
                                    if (coupleEl.TryGetProperty("id", out var cidEl))
                                    {
                                        var cid = cidEl.GetRawText().Trim('"');
                                        if (!string.IsNullOrWhiteSpace(cid)) registeredCoupleIds.Add(cid);
                                    }
                                }

                                // Collect person full name if present
                                if (reg.TryGetProperty("person", out var personEl) && personEl.ValueKind != JsonValueKind.Null)
                                {
                                    var pf = personEl.TryGetProperty("firstName", out var pff) ? pff.GetString() ?? string.Empty : string.Empty;
                                    var pl = personEl.TryGetProperty("lastName", out var pll) ? pll.GetString() ?? string.Empty : string.Empty;
                                    var pFull = string.IsNullOrWhiteSpace(pf) ? pl : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                                    if (!string.IsNullOrWhiteSpace(pFull)) registeredPersonNames.Add(pFull);
                                }
                            }
                        }
                    }
                }
            }
            catch { }

            // Use proxy/person id only
            string? myPersonIdOption = null;
            try { myPersonIdOption = _userService.CurrentPersonId; } catch { myPersonIdOption = null; }
            if (!string.IsNullOrWhiteSpace(myPersonIdOption))
            {
                var mePrefix = LocalizationService.Get("Registration_My_Prefix") ?? "Já: ";
                options.Add(new RegistrationOption($"{mePrefix}{CurrentUserText}", "self", myPersonIdOption));
            }

            if (couples != null && couples.Count > 0)
            {
                foreach (var c in couples)
                {
                    var man = string.IsNullOrWhiteSpace(c.ManName) ? "" : c.ManName;
                    var woman = string.IsNullOrWhiteSpace(c.WomanName) ? "" : c.WomanName;
                    var text = (man + " - " + woman).Trim();
                    if (!string.IsNullOrEmpty(text)) 
                        options.Add(new RegistrationOption(text, "couple", c.Id));
                }
            }

            // Filter out already registered options
            var filtered = new List<RegistrationOption>();
            var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
            var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
            var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : string.IsNullOrWhiteSpace(myLast) ? myFirst : (myFirst + " " + myLast).Trim();
            
            foreach (var opt in options)
            {
                if (opt.Kind == "self")
                {
                    if (string.IsNullOrWhiteSpace(myFull) || registeredPersonNames.Contains(myFull))
                        continue;
                }
                else if (opt.Kind == "couple")
                {
                    if (!string.IsNullOrWhiteSpace(opt.Id) && registeredCoupleIds.Contains(opt.Id)) 
                        continue;
                    var key = opt.DisplayText?.Trim() ?? string.Empty;
                    if (!string.IsNullOrWhiteSpace(key) && registeredPersonNames.Contains(key)) 
                        continue;
                }
                filtered.Add(opt);
            }

            SelectionOptions.Clear();
            foreach (var opt in filtered)
            {
                SelectionOptions.Add(opt);
            }
        }
        catch (Exception ex)
        {
            // Error handling
            CurrentUserText = $"Error: {ex.Message}";
        }
    }

    private async Task LoadTrainerSelectionAsync()
    {
        try
        {
            var ev = _currentEvent;
            if (ev == null) return;

            TrainerOptions.Clear();
            if (ev.EventTrainersList != null)
            {
                foreach (var t in ev.EventTrainersList.OrderBy(x => (x?.Name ?? string.Empty).Trim()))
                {
                    var name = (t?.Name ?? string.Empty).Trim();
                    TrainerOptions.Add(new TrainerOption(name, name, 0, t?.Id));
                }
            }

            TrainerSelectionHeaderVisible = true;
            TrainerSelectionVisible = true;
        }
        catch { }
    }

    [RelayCommand]
    private async Task ConfirmAsync()
    {
        try
        {
            if (EventId == 0) return;
            if (SelectedOption == null)
            {
                // Need to communicate error to view - for now we'll throw
                throw new InvalidOperationException(LocalizationService.Get("Registration_NoSelection") ?? "Vyberte, koho chcete registrovat.");
            }

            string? personId = null;
            string? coupleId = null;
            if (SelectedOption.Kind == "self") 
                personId = SelectedOption.Id;
            else if (SelectedOption.Kind == "couple") 
                coupleId = SelectedOption.Id;

            await CreateRegistrationAsync(personId, coupleId);

            // Show success animation
            SuccessText = LocalizationService.Get("Registration_Confirm_Message") ?? "Registrace odeslána";
            SuccessOverlayVisible = true;

            await Task.Delay(1200); // Animation + display time
            await _navigationService.GoBackAsync();
        }
        catch (Exception ex)
        {
            // Error - in real implementation we'd want to communicate this to the view
            throw;
        }
    }

    private async Task<bool> CreateRegistrationAsync(string? personId, string? coupleId)
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };

        if (string.IsNullOrWhiteSpace(personId) && string.IsNullOrWhiteSpace(coupleId))
        {
            throw new InvalidOperationException(LocalizationService.Get("Registration_NoSelection") ?? "Vyberte, koho chcete registrovat.");
        }

        var reg = new Dictionary<string, object>
        {
            ["eventId"] = EventId.ToString()
        };

        if (!string.IsNullOrWhiteSpace(personId))
        {
            reg["personId"] = personId;
        }
        if (!string.IsNullOrWhiteSpace(coupleId))
        {
            if (long.TryParse(coupleId, out var cid)) 
                reg["coupleId"] = cid.ToString();
            else 
                throw new InvalidOperationException(LocalizationService.Get("Registration_IdNumeric") ?? "CoupleId must be numeric.");
        }

        if (_trainerReservationNotAllowed)
        {
            reg["lessons"] = new List<Dictionary<string, object>>();
        }
        else
        {
            var lessonsList = new List<Dictionary<string, object>>();
            foreach (var t in TrainerOptions)
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
            if (lessonsList.Count > 0) 
                reg["lessons"] = lessonsList;
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

        var gql = new Dictionary<string, object>
        {
            ["query"] = "mutation RegisterToEvent($input: RegisterToEventManyInput!) { registerToEventMany(input: $input) { eventRegistrations { id } } }",
            ["variables"] = variables
        };

        var json = JsonSerializer.Serialize(gql, options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await _authService.Http.PostAsync("", content);
        var body = await resp.Content.ReadAsStringAsync();

        try
        {
            var data = JsonSerializer.Deserialize<GraphQlResponse<RegisterToEventManyData>>(body, options);
            if (data?.Errors != null && data.Errors.Count > 0)
            {
                var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
                throw new InvalidOperationException(msg);
            }
            return data?.Data?.RegisterToEventMany?.EventRegistrations != null && 
                   data.Data.RegisterToEventMany.EventRegistrations.Count > 0;
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

    // Nested classes
    public sealed record RegistrationOption(string DisplayText, string Kind, string? Id);

    public sealed class TrainerOption : INotifyPropertyChanged
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
        private void OnPropertyChanged(string propertyName) => 
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }

    // GraphQL response types
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

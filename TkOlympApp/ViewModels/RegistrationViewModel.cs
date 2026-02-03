using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System.Collections.ObjectModel;
using System.ComponentModel;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class RegistrationViewModel : ViewModelBase
{
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
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService)
    {
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
            try { pidDisplay = _userService.CurrentPersonId; } catch (Exception ex) { LoggerService.SafeLogWarning<RegistrationViewModel>("Reading CurrentPersonId failed: {0}", new object[] { ex.Message }); pidDisplay = null; }
            if (string.IsNullOrWhiteSpace(pidDisplay)) pidDisplay = "není";
            displayName = $"{displayName} ({pidDisplay})";
            CurrentUserText = displayName;
            CurrentUserVisible = true;

            // Prepare containers
            var registeredCoupleIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var registeredPersonNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var options = new List<RegistrationOption>();

            // Load user's couples from user service
            List<CoupleInfo>? couples = null;
            try
            {
                couples = await _userService.GetActiveCouplesFromUsersAsync();
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<RegistrationViewModel>("GetActiveCouplesFromUsersAsync failed: {0}", new object[] { ex.Message });
            }

            // If we don't have a proxy person id, scan recent registrations to detect already-registered people/couples
            string? selectedPersonId = null;
            try { selectedPersonId = _userService.CurrentPersonId; } catch { selectedPersonId = null; }
            if (string.IsNullOrWhiteSpace(selectedPersonId))
            {
                PersonIdText = "není";
                try
                {
                    var startRange = DateTime.Now.Date.AddYears(-1);
                    var endRange = DateTime.Now.Date.AddYears(1);
                    var scan = await _eventService.GetEventRegistrationScanAsync(startRange, endRange, EventId);

                    registeredCoupleIds = new HashSet<string>(scan.RegisteredCoupleIds ?? Array.Empty<string>(), StringComparer.OrdinalIgnoreCase);
                    registeredPersonNames = new HashSet<string>(scan.RegisteredPersonNames ?? Array.Empty<string>(), StringComparer.OrdinalIgnoreCase);
                }
                catch (Exception ex)
                {
                    LoggerService.SafeLogWarning<RegistrationViewModel>("LoadMyCouples: scanning registrations failed: {0}", new object[] { ex.Message });
                }
            }

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
            LoggerService.SafeLogWarning<RegistrationViewModel>("LoadMyCouplesAsync failed: {0}", new object[] { ex.Message });
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
        catch (Exception ex) { LoggerService.SafeLogWarning<RegistrationViewModel>("LoadTrainerSelectionAsync failed: {0}", new object[] { ex.Message }); }
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
            // Log and surface minimal feedback through TitleText
            LoggerService.SafeLogWarning<RegistrationViewModel>("ConfirmAsync failed: {0}", new object[] { ex.Message });
            TitleText = LocalizationService.Get("Registration_Confirm_Error") ?? "Chyba při registraci";
        }
    }

    private async Task<bool> CreateRegistrationAsync(string? personId, string? coupleId)
    {
        var lessons = new List<EventRegistrationLessonRequest>();
        if (!_trainerReservationNotAllowed)
        {
            foreach (var t in TrainerOptions)
            {
                if (t != null && t.Count > 0)
                {
                    if (string.IsNullOrWhiteSpace(t.Id))
                    {
                        throw new InvalidOperationException($"Missing trainerId for trainer: {t.Name}");
                    }
                    lessons.Add(new EventRegistrationLessonRequest(t.Id!, t.Count));
                }
            }
        }

        var request = new EventRegistrationRequest(personId, coupleId, lessons);
        return await _eventService.RegisterToEventManyAsync(request);
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

}

using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Globalization;
using System.Linq;
using Microsoft.Extensions.Logging;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace TkOlympApp.ViewModels;

public sealed partial class MainPageViewModel : ViewModelBase
{
    private readonly IEventService _eventService;
    private readonly INoticeboardService _noticeboardService;
    private readonly IEventNotificationService _eventNotificationService;
    private readonly IUserNotifier _notifier;
    private readonly ILogger _logger;

    private CancellationTokenSource? _cts;

    [ObservableProperty]
    private DayGroup? _currentDay;

    [ObservableProperty]
    private bool _isLoading;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _hasUpcomingEvents;

    [ObservableProperty]
    private bool _hasRecentAnnouncements;

    [ObservableProperty]
    private bool _hasUpcomingCamps;

    public ObservableCollection<Announcement> RecentAnnouncements { get; } = new();
    public ObservableCollection<CampItem> UpcomingCamps { get; } = new();

    public MainPageViewModel(
        IEventService eventService,
        INoticeboardService noticeboardService,
        IEventNotificationService eventNotificationService,
        IUserNotifier notifier)
    {
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _noticeboardService = noticeboardService ?? throw new ArgumentNullException(nameof(noticeboardService));
        _eventNotificationService = eventNotificationService ?? throw new ArgumentNullException(nameof(eventNotificationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
        _logger = LoggerService.CreateLogger<MainPageViewModel>();

        RecentAnnouncements.CollectionChanged += (_, __) =>
        {
            HasRecentAnnouncements = RecentAnnouncements.Count > 0;
        };
        UpcomingCamps.CollectionChanged += (_, __) =>
        {
            HasUpcomingCamps = UpcomingCamps.Count > 0;
        };
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();

        _cts?.Cancel();
        _cts?.Dispose();
        _cts = new CancellationTokenSource();

        try
        {
            await InitializeAsync(_cts.Token);
        }
        catch (OperationCanceledException)
        {
            _logger.LogInformation("MainPageViewModel initialization cancelled");
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("MainPageViewModel initialization failed: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    LocalizationService.Get("Initialization_Error") ?? "Initialization failed",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<MainPageViewModel>("Failed to show initialization error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    public override Task OnDisappearingAsync()
    {
        try
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _cts = null;
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("Failed to cancel MainPageViewModel CTS: {0}", new object[] { ex.Message });
        }

        return base.OnDisappearingAsync();
    }

    private async Task InitializeAsync(CancellationToken ct)
    {
        ct.ThrowIfCancellationRequested();

        try
        {
            var notifStart = DateTime.Now.Date;
            var notifEnd = DateTime.Now.Date.AddDays(2).AddHours(23).AddMinutes(59);
            var notifEvents = await _eventService.GetMyEventInstancesForRangeAsync(notifStart, notifEnd, ct: ct);

            await _eventNotificationService.CheckAndNotifyChangesAsync(notifEvents, ct);
            await _eventNotificationService.ScheduleNotificationsForEventsAsync(notifEvents, ct);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("Failed to schedule event notifications: {0}", new object[] { ex.Message });
        }

        ct.ThrowIfCancellationRequested();

        await LoadUpcomingEventsAsync(ct);
        ct.ThrowIfCancellationRequested();

        await LoadRecentAnnouncementsAsync(ct);
        ct.ThrowIfCancellationRequested();

        await LoadUpcomingCampsAsync(ct);
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        if (IsRefreshing) return;

        IsRefreshing = true;
        try
        {
            await LoadUpcomingEventsAsync(_cts?.Token ?? default);
            await LoadRecentAnnouncementsAsync(_cts?.Token ?? default);
            await LoadUpcomingCampsAsync(_cts?.Token ?? default);
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("Refresh failed: {0}", new object[] { ex.Message });
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private async Task LoadUpcomingEventsAsync(CancellationToken ct = default)
    {
        if (IsLoading) return;
        IsLoading = true;

        try
        {
            var now = DateTime.Now;
            var start = now.Date;
            var end = now.Date.AddDays(14);

            _logger.LogInformation("Loading upcoming events from {Start} to {End}", start, end);
            var events = await _eventService.GetMyEventInstancesForRangeAsync(start, end, ct: ct);

            var groupsByDate = events
                .Where(e => e.Since.HasValue)
                .GroupBy(e => e.Since!.Value.Date)
                .OrderBy(g => g.Key)
                .ToDictionary(g => g.Key, g => g.OrderBy(ev => ev.Since).ToList());

            var firstDate = groupsByDate.Keys.FirstOrDefault();

            DayGroup? day = null;
            if (firstDate != default && groupsByDate.TryGetValue(firstDate, out var dayEvents) && dayEvents.Count > 0)
            {
                var culture = CultureInfo.CurrentUICulture ?? CultureInfo.CurrentCulture;
                var todayDate = DateTime.Now.Date;
                string dayLabel;

                if (firstDate == todayDate)
                    dayLabel = LocalizationService.Get("Today") ?? "Dnes";
                else if (firstDate == todayDate.AddDays(1))
                    dayLabel = LocalizationService.Get("Tomorrow") ?? "Zítra";
                else
                {
                    var name = culture.DateTimeFormat.GetDayName(firstDate.DayOfWeek);
                    dayLabel = char.ToUpper(name[0], culture) + name.Substring(1) + $" {firstDate:dd.MM.}";
                }

                day = new DayGroup(dayLabel, firstDate, dayEvents);
                _logger.LogDebug("Loaded {EventCount} events for {Date}", dayEvents.Count, firstDate);
            }
            else
            {
                _logger.LogInformation("No upcoming events found");
            }

            CurrentDay = day;
            HasUpcomingEvents = CurrentDay != null;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("Failed to load upcoming events: {0}", new object[] { ex.Message });
            CurrentDay = null;
            HasUpcomingEvents = false;
        }
        finally
        {
            IsLoading = false;
        }
    }

    private async Task LoadRecentAnnouncementsAsync(CancellationToken ct = default)
    {
        try
        {
            _logger.LogDebug("Loading recent announcements");
            var announcements = await _noticeboardService.GetMyAnnouncementsAsync(null, ct);
            RecentAnnouncements.Clear();

            var recent = announcements
                .OrderByDescending(a => a.CreatedAt)
                .Take(2)
                .ToList();

            foreach (var announcement in recent)
            {
                RecentAnnouncements.Add(announcement);
            }

            _logger.LogInformation("Loaded {Count} recent announcements", recent.Count);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("Failed to load recent announcements: {0}", new object[] { ex.Message });
            RecentAnnouncements.Clear();
        }
    }

    private async Task LoadUpcomingCampsAsync(CancellationToken ct = default)
    {
        try
        {
            _logger.LogDebug("Loading upcoming camps");
            var now = DateTime.Now;
            var pastStart = now.Date.AddYears(-1);
            var futureEnd = now.Date.AddYears(1);

            var events = await _eventService.GetMyEventInstancesForRangeAsync(pastStart, futureEnd, ct: ct);

            var camps = events
                .Where(e => string.Equals(e.Event?.Type, "CAMP", StringComparison.OrdinalIgnoreCase))
                .Where(e => e.Since.HasValue)
                .Select(e => new { Event = e, Distance = Math.Abs((e.Since!.Value.Date - now.Date).TotalDays) })
                .OrderBy(x => x.Distance)
                .Take(2)
                .Select(x => x.Event)
                .ToList();

            UpcomingCamps.Clear();
            foreach (var camp in camps)
            {
                UpcomingCamps.Add(new CampItem
                {
                    EventId = camp.Event?.Id ?? 0,
                    EventName = camp.Event?.Name ?? string.Empty,
                    LocationName = camp.Event?.LocationText ?? string.Empty,
                    Since = camp.Since,
                    Until = camp.Until
                });
            }

            _logger.LogInformation("Loaded {Count} upcoming camps", camps.Count);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPageViewModel>("Failed to load upcoming camps: {0}", new object[] { ex.Message });
            UpcomingCamps.Clear();
        }
    }

    public bool IsEventsEmpty => !HasUpcomingEvents && !IsLoading;

    partial void OnHasUpcomingEventsChanged(bool value)
    {
        OnPropertyChanged(nameof(IsEventsEmpty));
    }

    partial void OnIsLoadingChanged(bool value)
    {
        OnPropertyChanged(nameof(IsEventsEmpty));
    }

    public sealed class DayGroup
    {
        public string DayLabel { get; }
        public DateTime Date { get; }
        public ObservableCollection<GroupedTrainer> GroupedTrainers { get; }
        public ObservableCollection<EventInstance> SingleEvents { get; }

        public DayGroup(string dayLabel, DateTime date, IEnumerable<EventInstance> events)
        {
            DayLabel = dayLabel;
            Date = date;
            GroupedTrainers = new ObservableCollection<GroupedTrainer>();
            SingleEvents = new ObservableCollection<EventInstance>();

            var groupableEvents = new List<EventInstance>();
            var singleEventsList = new List<EventInstance>();

            foreach (var evt in events)
            {
                var isLesson = string.Equals(evt.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase);
                var trainerCount = evt.Event?.EventTrainersList?.Count ?? 0;

                if (isLesson && trainerCount == 1)
                {
                    groupableEvents.Add(evt);
                }
                else
                {
                    singleEventsList.Add(evt);
                }
            }

            var groups = groupableEvents
                .GroupBy(e => EventTrainerDisplayHelper.GetTrainerDisplayName(e.Event?.EventTrainersList?.FirstOrDefault())?.Trim() ?? string.Empty)
                .OrderBy(g => g.Min(x => x.Since ?? x.UpdatedAt));

            foreach (var g in groups)
            {
                var ordered = g.OrderBy(i => i.Since ?? i.UpdatedAt).ToList();
                var trainerName = g.Key;
                var representative = ordered.FirstOrDefault()?.Event?.EventTrainersList?.FirstOrDefault();
                var trainerTitle = string.IsNullOrWhiteSpace(trainerName)
                    ? LocalizationService.Get("Lessons") ?? "Lekce"
                    : EventTrainerDisplayHelper.GetTrainerDisplayWithPrefix(representative).Trim();
                var gt = new GroupedTrainer(trainerTitle);

                for (var i = 0; i < ordered.Count; i++)
                {
                    var inst = ordered[i];
                    var firstRegistrant = i == 0 ? GroupedEventRow.ComputeFirstRegistrantPublic(inst) : string.Empty;
                    gt.Rows.Add(new GroupedEventRow(inst, firstRegistrant));
                }
                GroupedTrainers.Add(gt);
            }

            foreach (var evt in singleEventsList.OrderBy(e => e.Since ?? e.UpdatedAt))
            {
                SingleEvents.Add(evt);
            }
        }

        public int TotalRows => (SingleEvents?.Count ?? 0) + GroupedTrainers.Sum(gt => gt.Rows.Count);
    }

    public sealed class GroupedTrainer
    {
        public string TrainerTitle { get; }
        public ObservableCollection<GroupedEventRow> Rows { get; }
        public GroupedTrainer(string trainerTitle)
        {
            TrainerTitle = trainerTitle;
            Rows = new ObservableCollection<GroupedEventRow>();
        }
    }

    public sealed class GroupedEventRow : INotifyPropertyChanged
    {
        public EventInstance Instance { get; }
        public string TimeRange { get; }
        public bool IsCancelled => Instance?.IsCancelled ?? false;
        private string _firstRegistrant;
        private bool _isLoaded;
        public bool IsLoaded
        {
            get => _isLoaded;
            set
            {
                if (_isLoaded != value)
                {
                    _isLoaded = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsLoaded)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsFree)));
                }
            }
        }
        public string FirstRegistrant
        {
            get => _firstRegistrant;
            set
            {
                if (_firstRegistrant != value)
                {
                    _firstRegistrant = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FirstRegistrant)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsFree)));
                }
            }
        }

        public bool IsFree => IsLoaded && string.IsNullOrEmpty(FirstRegistrant);
        private bool _isHighlighted;
        public bool IsHighlighted
        {
            get => _isHighlighted;
            set
            {
                if (_isHighlighted != value)
                {
                    _isHighlighted = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsHighlighted)));
                }
            }
        }
        public string DurationText { get; }

        public GroupedEventRow(EventInstance instance, string firstRegistrantOverride = "")
        {
            Instance = instance;
            var since = instance.Since;
            var until = instance.Until;
            TimeRange = (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " - " + (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
            _firstRegistrant = string.IsNullOrEmpty(firstRegistrantOverride) ? ComputeFirstRegistrant(instance) : firstRegistrantOverride;
            IsLoaded = !string.IsNullOrEmpty(_firstRegistrant);
            DurationText = ComputeDuration(instance);
        }

        public event PropertyChangedEventHandler? PropertyChanged;

        public static string ComputeFirstRegistrantPublic(EventInstance inst) => ComputeFirstRegistrant(inst);

        private static string ComputeFirstRegistrant(EventInstance inst)
        {
            try
            {
                var evt = inst.Event;
                if (evt?.EventRegistrationsList != null && evt.EventRegistrationsList.Count > 0)
                {
                    var regs = evt.EventRegistrationsList;
                    var count = regs.Count;

                    var surnames = new List<string>();
                    static string ExtractSurname(string full)
                    {
                        if (string.IsNullOrWhiteSpace(full)) return string.Empty;
                        var parts = full.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                        return parts.Length > 1 ? parts[^1] : full;
                    }
                    foreach (var node in regs)
                    {
                        try
                        {
                            if (node?.Person != null)
                            {
                                var full = node.Person.Name ?? string.Empty;
                                if (!string.IsNullOrWhiteSpace(full))
                                    surnames.Add(ExtractSurname(full));
                            }
                            else if (node?.Couple != null)
                            {
                                var manLn = node.Couple.Man?.LastName;
                                var womanLn = node.Couple.Woman?.LastName;
                                if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn))
                                    surnames.Add(manLn + " - " + womanLn);
                                else if (!string.IsNullOrWhiteSpace(manLn)) surnames.Add(manLn);
                                else if (!string.IsNullOrWhiteSpace(womanLn)) surnames.Add(womanLn);
                            }
                        }
                        catch (Exception ex)
                        {
                            LoggerService.SafeLogWarning<MainPageViewModel>("ComputeFirstRegistrant: failed parsing registration item: {0}", new object[] { ex.Message });
                        }
                    }

                    if (count == 1)
                    {
                        var node = regs[0];
                        if (node?.Person != null)
                        {
                            var full = node.Person.Name ?? string.Empty;
                            if (!string.IsNullOrWhiteSpace(full)) return full;
                        }
                        if (node?.Couple != null)
                        {
                            var manLn = node.Couple.Man?.LastName;
                            var womanLn = node.Couple.Woman?.LastName;
                            if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn)) return manLn + " - " + womanLn;
                            if (!string.IsNullOrWhiteSpace(manLn)) return manLn;
                            if (!string.IsNullOrWhiteSpace(womanLn)) return womanLn;
                        }
                    }
                    else if (count == 2)
                    {
                        if (surnames.Count >= 2)
                            return surnames[0] + " - " + surnames[1];
                        if (surnames.Count == 1) return surnames[0];
                    }
                    else
                    {
                        if (count == 3)
                        {
                            if (surnames.Count > 0) return string.Join(", ", surnames);
                        }
                        else
                        {
                            var take = surnames.Take(2).ToList();
                            if (take.Count > 0) return string.Join(", ", take) + "...";
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<MainPageViewModel>("ComputeFirstRegistrant failed: {0}", new object[] { ex.Message });
            }
            return string.Empty;
        }

        private static string ComputeDuration(EventInstance inst)
        {
            try
            {
                if (inst.Since.HasValue && inst.Until.HasValue)
                {
                    var mins = (int)(inst.Until.Value - inst.Since.Value).TotalMinutes;
                    return mins > 0 ? mins + "'" : string.Empty;
                }
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<MainPageViewModel>("ComputeDuration failed: {0}", new object[] { ex.Message });
            }
            return string.Empty;
        }
    }

    public sealed class CampItem
    {
        public long EventId { get; set; }
        public string EventName { get; set; } = string.Empty;
        public string LocationName { get; set; } = string.Empty;
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
    }
}

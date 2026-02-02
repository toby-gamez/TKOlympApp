using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Pages;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

/// <summary>
/// ViewModel for CalendarPage - handles all calendar logic, grouping, filtering, and data loading.
/// Replaces the 1446-line code-behind with MVVM pattern.
/// </summary>
public partial class CalendarViewModel : ViewModelBase
{
    private readonly ILogger<CalendarViewModel> _logger;
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private readonly List<TrainerDetailRow> _trainerDetailRows = new(); // for async updates
    private bool _suppressReloadOnNextAppearing = false;

    [ObservableProperty]
    private bool _isRefreshing = false;

    [ObservableProperty]
    private bool _onlyMine = true;

    [ObservableProperty]
    private DateTime _weekStart;

    [ObservableProperty]
    private DateTime _weekEnd;

    [ObservableProperty]
    private string _emptyText = string.Empty;

    [ObservableProperty]
    private bool _showEmpty = false;

    [ObservableProperty]
    private bool _myTabActive = true;

    [ObservableProperty]
    private bool _allTabActive = false;

    /// <summary>
    /// Flat list of all calendar rows (week headers, day headers, events, trainer groups).
    /// Bound to CollectionView for high-performance rendering.
    /// </summary>
    public ObservableCollection<EventRow> EventRows { get; } = new();

    public CalendarViewModel(
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService)
    {
        _logger = LoggerService.CreateLogger<CalendarViewModel>();
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));

        // Initialize week range to rolling 7-day window (today + 7 days)
        _weekStart = DateTime.Now.Date;
        _weekEnd = _weekStart.AddDays(6);

        UpdateEmptyText();
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();

        if (_suppressReloadOnNextAppearing)
        {
            _suppressReloadOnNextAppearing = false;
            return;
        }

        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        _logger.LogTrace("Refresh triggered");
        IsRefreshing = true;
        try
        {
            await LoadEventsAsync();
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    [RelayCommand]
    private async Task SwitchToMyEventsAsync()
    {
        _logger.LogTrace("Tab 'Mine' clicked");
        OnlyMine = true;
        MyTabActive = true;
        AllTabActive = false;
        UpdateEmptyText();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task SwitchToAllEventsAsync()
    {
        _logger.LogTrace("Tab 'All' clicked");
        OnlyMine = false;
        MyTabActive = false;
        AllTabActive = true;
        UpdateEmptyText();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task PreviousWeekAsync()
    {
        WeekStart = WeekStart.AddDays(-7);
        WeekEnd = WeekStart.AddDays(6);
        _logger.LogDebug("Week navigation: Previous week - {WeekStart} to {WeekEnd}",
            WeekStart.ToShortDateString(), WeekEnd.ToShortDateString());
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task NextWeekAsync()
    {
        WeekStart = WeekStart.AddDays(7);
        WeekEnd = WeekStart.AddDays(6);
        _logger.LogDebug("Week navigation: Next week - {WeekStart} to {WeekEnd}",
            WeekStart.ToShortDateString(), WeekEnd.ToShortDateString());
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task TodayAsync()
    {
        var today = DateTime.Now.Date;
        
        // If already on today's week, don't reload
        if (WeekStart == today)
        {
            _logger.LogDebug("Today button clicked but already on today's week - skipping reload");
            return;
        }

        WeekStart = today;
        WeekEnd = WeekStart.AddDays(6);
        _logger.LogDebug("Week navigation: Today - resetting to current week {WeekStart} to {WeekEnd}",
            WeekStart.ToShortDateString(), WeekEnd.ToShortDateString());
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task OpenCalendarViewAsync()
    {
        _suppressReloadOnNextAppearing = true;
        await _navigationService.NavigateToAsync(nameof(CalendarViewPage));
    }

    [RelayCommand]
    private async Task NavigateToEventAsync(EventInstance? instance)
    {
        if (instance == null) return;
        if (instance.IsCancelled) return;
        
        var eventId = instance.Event?.Id;
        if (eventId is long id && id != 0)
        {
            _suppressReloadOnNextAppearing = true;
            await _navigationService.NavigateToAsync($"{nameof(EventPage)}?id={id}");
        }
    }

    [RelayCommand]
    private async Task NavigateToEventFromTrainerRowAsync(TrainerDetailRow? row)
    {
        if (row?.Instance == null) return;
        if (row.Instance.IsCancelled) return;

        var eventId = row.Instance.Event?.Id;
        if (eventId is long id && id != 0)
        {
            _suppressReloadOnNextAppearing = true;
            await _navigationService.NavigateToAsync($"{nameof(EventPage)}?id={id}");
        }
    }

    /// <summary>
    /// Public method to request a refresh from external callers (e.g. AppShell after auth).
    /// </summary>
    public async Task RefreshEventsAsync()
    {
        IsRefreshing = true;
        await LoadEventsAsync();
    }

    private async Task LoadEventsAsync()
    {
        _logger.LogDebug("LoadEventsAsync started - Mode: {Mode}, WeekStart: {WeekStart}, WeekEnd: {WeekEnd}",
            OnlyMine ? "Mine" : "All", WeekStart, WeekEnd);

        if (IsBusy)
        {
            _logger.LogDebug("LoadEventsAsync skipped - load already in progress");
            return;
        }

        IsBusy = true;
        UpdateEmptyText();

        try
        {
            var start = WeekStart.Date;
            var end = WeekEnd.Date.AddDays(1); // extend by one day for timezone inclusivity
            
            _logger.LogInformation("Fetching events: Start={Start:o}, End={End:o}, OnlyMine={OnlyMine}",
                start, end, OnlyMine);

            using var perfLog = LoggerService.LogPerformance<CalendarViewModel>(
                "LoadEvents" + (OnlyMine ? "Mine" : "All"));

            List<EventInstance> events;
            if (OnlyMine)
            {
                events = await _eventService.GetMyEventInstancesForRangeAsync(start, end);
            }
            else
            {
                events = await _eventService.GetEventInstancesForRangeListAsync(start, end);
            }

            _logger.LogInformation("Fetched {Count} events for week {WeekStart} - {WeekEnd}",
                events.Count, WeekStart.ToShortDateString(), WeekEnd.ToShortDateString());

            // Clear and rebuild
            EventRows.Clear();
            _trainerDetailRows.Clear();

            // Render all events into flat row list
            RenderEvents(events);

            // Highlight rows in "All" view when registrant matches current user or couples
            List<string> normalizedHighlights = new();
            if (!OnlyMine)
            {
                normalizedHighlights = await BuildHighlightListAsync();
                ApplyHighlighting(normalizedHighlights);
            }

            // Background fetch for missing first-registrant names
            _ = Task.Run(() => BackgroundFetchRegistrantNamesAsync(normalizedHighlights));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "LoadEventsAsync failed");
            // Note: Error display should be handled by page if needed
        }
        finally
        {
            IsBusy = false;
            IsRefreshing = false;
            UpdateEmptyText();
            _logger.LogDebug("LoadEventsAsync completed");
        }
    }

    private void RenderEvents(List<EventInstance> events)
    {
        // Group events by day (date only)
        var groupsByDate = events
            .GroupBy(e => e.Since.HasValue ? e.Since.Value.Date : e.UpdatedAt.Date)
            .ToDictionary(g => g.Key, g => g.OrderBy(ev => ev.Since).ToList());

        // Single week header for current rolling window
        var weekLabel = CalendarHelpers.FormatWeekLabel(WeekStart, WeekEnd);
        EventRows.Add(new WeekHeaderRow(weekLabel));

        foreach (var date in groupsByDate.Keys.OrderBy(d => d))
        {
            if (!groupsByDate.TryGetValue(date, out var dayEvents) || dayEvents.Count == 0) 
                continue;

            // Day header
            var dayLabel = CalendarHelpers.FormatDayLabel(date);
            EventRows.Add(new DayHeaderRow(dayLabel, date));

            // Separate events into groupable (lesson with exactly one trainer) and single events
            var groupableEvents = new List<EventInstance>();
            var singleEventsList = new List<EventInstance>();

            foreach (var evt in dayEvents)
            {
                var isLesson = string.Equals(evt.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase);
                var trainerCount = evt.Event?.EventTrainersList?.Count ?? 0;

                if (isLesson && trainerCount == 1)
                    groupableEvents.Add(evt);
                else
                    singleEventsList.Add(evt);
            }

            // Add single events
            foreach (var evt in singleEventsList.OrderBy(e => e.Since ?? e.UpdatedAt))
            {
                var timeRange = CalendarHelpers.ComputeTimeRange(evt);
                var locationOrTrainers = CalendarHelpers.ComputeLocationOrTrainers(evt);
                var eventName = CalendarHelpers.ComputeEventName(evt);
                var eventTypeLabel = CalendarHelpers.ComputeEventTypeLabel(evt.Event?.Type);

                EventRows.Add(new SingleEventRow(evt, timeRange, locationOrTrainers, eventName, 
                    eventTypeLabel, evt.IsCancelled));
            }

            // Add grouped trainers
            var groups = groupableEvents
                .GroupBy(e => EventTrainerDisplayHelper.GetTrainerDisplayName(
                    e.Event?.EventTrainersList?.FirstOrDefault())?.Trim() ?? string.Empty)
                .OrderBy(g => g.Min(x => x.Since ?? x.UpdatedAt));

            foreach (var g in groups)
            {
                var ordered = g.OrderBy(i => i.Since ?? i.UpdatedAt).ToList();
                var trainerName = g.Key;
                var representative = ordered.FirstOrDefault()?.Event?.EventTrainersList?.FirstOrDefault();
                var trainerTitle = string.IsNullOrWhiteSpace(trainerName)
                    ? LocalizationService.Get("Lessons") ?? "Lekce"
                    : EventTrainerDisplayHelper.GetTrainerDisplayWithPrefix(representative).Trim();
                var cohorts = ordered.FirstOrDefault()?.Event?.EventTargetCohortsList;

                EventRows.Add(new TrainerGroupHeaderRow(trainerTitle, cohorts));

                for (int i = 0; i < ordered.Count; i++)
                {
                    var inst = ordered[i];
                    var firstRegistrant = i == 0 ? CalendarHelpers.ComputeFirstRegistrant(inst) : string.Empty;
                    var durationText = CalendarHelpers.ComputeDuration(inst);
                    var detailRow = new TrainerDetailRow(inst, firstRegistrant, durationText);
                    _trainerDetailRows.Add(detailRow);

                    EventRows.Add(detailRow);
                }
            }
        }
    }

    private async Task<List<string>> BuildHighlightListAsync()
    {
        try
        {
            var highlightNames = new List<string>();

            try
            {
                var currentUser = await _userService.GetCurrentUserAsync();
                if (currentUser != null)
                {
                    var full = ((currentUser.UJmeno ?? string.Empty) + " " + 
                        (currentUser.UPrijmeni ?? string.Empty)).Trim();
                    CalendarHelpers.AddNameVariants(highlightNames, full);
                    CalendarHelpers.AddNameVariants(highlightNames, currentUser.ULogin);
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to fetch current user for highlighting");
            }

            try
            {
                var couples = await _userService.GetActiveCouplesFromUsersAsync();
                foreach (var c in couples)
                {
                    CalendarHelpers.AddNameVariants(highlightNames, c.ManName);
                    CalendarHelpers.AddNameVariants(highlightNames, c.WomanName);
                    CalendarHelpers.AddNameVariants(highlightNames,
                        string.IsNullOrWhiteSpace(c.ManName) || string.IsNullOrWhiteSpace(c.WomanName)
                            ? null
                            : c.ManName + " - " + c.WomanName);
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to fetch active couples for highlighting");
            }

            var distinct = highlightNames.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            var normalized = distinct
                .Select(CalendarHelpers.NormalizeName)
                .Where(x => !string.IsNullOrWhiteSpace(x))
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();

            _logger.LogDebug("Highlight names for calendar: {Names}", string.Join(", ", distinct));
            return normalized;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to process highlighting logic");
            return new List<string>();
        }
    }

    private void ApplyHighlighting(List<string> normalizedHighlights)
    {
        foreach (var row in _trainerDetailRows)
        {
            var fr = row.FirstRegistrant ?? string.Empty;
            if (string.IsNullOrWhiteSpace(fr))
            {
                row.IsHighlighted = false;
                continue;
            }

            var frNorm = CalendarHelpers.NormalizeName(fr);
            bool matched = !string.IsNullOrWhiteSpace(frNorm) && 
                normalizedHighlights.Any(h => frNorm.Contains(h) || h.Contains(frNorm));
            row.IsHighlighted = matched;
        }
    }

    private async Task BackgroundFetchRegistrantNamesAsync(List<string> normalizedHighlights)
    {
        try
        {
            _logger.LogTrace("Starting background fetch for {Count} trainer detail rows", 
                _trainerDetailRows.Count);

            foreach (var row in _trainerDetailRows)
            {
                var inst = row.Instance;
                var evt = inst?.Event;
                if (!row.IsLoaded && evt != null && evt.Id != 0)
                {
                    try
                    {
                        var id = evt.Id;
                        var details = await _eventService.GetEventAsync(id);
                        string name = string.Empty;

                        try
                        {
                            if (details?.EventRegistrations?.Nodes != null && 
                                details.EventRegistrations.Nodes.Count > 0)
                            {
                                var node = details.EventRegistrations.Nodes[0];
                                if (node?.Person != null)
                                    name = (node.Person.FirstName + " " + 
                                        (node.Person.LastName ?? string.Empty)).Trim();
                                
                                if (string.IsNullOrWhiteSpace(name) && node?.Couple != null)
                                {
                                    var manLn = node.Couple.Man?.LastName;
                                    var womanLn = node.Couple.Woman?.LastName;
                                    if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn))
                                        name = manLn + " - " + womanLn;
                                    else if (!string.IsNullOrWhiteSpace(manLn)) name = manLn;
                                    else if (!string.IsNullOrWhiteSpace(womanLn)) name = womanLn;
                                }
                            }
                        }
                        catch (Exception ex)
                        {
                            _logger.LogWarning(ex, "Failed to extract registrant name from event {EventId}", id);
                        }

                        // Update row on main thread
                        Microsoft.Maui.ApplicationModel.MainThread.BeginInvokeOnMainThread(() =>
                        {
                            row.FirstRegistrant = name;
                            row.IsLoaded = true;

                            try
                            {
                                var frNorm = CalendarHelpers.NormalizeName(row.FirstRegistrant);
                                var matched = !string.IsNullOrWhiteSpace(frNorm) &&
                                    normalizedHighlights.Any(h => frNorm.Contains(h) || h.Contains(frNorm));
                                row.IsHighlighted = matched;
                                _logger.LogTrace("Row updated: FirstRegistrant={FirstRegistrant}, Normalized={Normalized}, Matched={Matched}",
                                    row.FirstRegistrant, frNorm, matched);
                            }
                            catch (Exception ex)
                            {
                                _logger.LogWarning(ex, "Failed to update highlight status for row");
                            }
                        });
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning(ex, "Failed to fetch event details for event {EventId}", evt.Id);
                        // Mark as loaded so UI doesn't stay in unknown state
                        Microsoft.Maui.ApplicationModel.MainThread.BeginInvokeOnMainThread(() =>
                        {
                            row.IsLoaded = true;
                        });
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Background fetch failed unexpectedly");
        }
    }

    private void UpdateEmptyText()
    {
        EmptyText = OnlyMine
            ? (LocalizationService.Get("Empty_MyEvents") ?? "Nemáte žádné události.")
            : (LocalizationService.Get("Empty_NoEvents") ?? "Nejsou žádné události.");
        
        ShowEmpty = !IsBusy && EventRows.Count == 0;
    }
}

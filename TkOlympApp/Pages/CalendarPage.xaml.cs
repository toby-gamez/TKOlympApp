using Microsoft.Maui.Controls;
using Microsoft.Maui.Controls.Shapes;
using System.Diagnostics;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Threading.Tasks;
using System.Globalization;
using System.Linq;
using TkOlympApp.Services;
using Microsoft.Maui.Graphics;
using Microsoft.Extensions.Logging;

namespace TkOlympApp.Pages;

public partial class CalendarPage : ContentPage
{
    private readonly ILogger<CalendarPage> _logger;
    private bool _isLoading;
    private bool _onlyMine = true;
    private DateTime _weekStart;
    private DateTime _weekEnd;
    private readonly List<TrainerDetailRow> _trainerDetailRows = new(); // for async updates
    private bool _suppressReloadOnNextAppearing = false;

    public CalendarPage()
    {
        _logger = LoggerService.CreateLogger<CalendarPage>();
        
        try
        {
            _logger.LogTrace("CalendarPage constructor started");
            InitializeComponent();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "CalendarPage InitializeComponent failed");
            Content = new Microsoft.Maui.Controls.StackLayout
            {
                Children =
                {
                    new Microsoft.Maui.Controls.Label { Text = LocalizationService.Get("Error_Loading_Prefix") + ex.Message }
                }
            };
            return;
        }

        // No binding needed - we'll generate views directly
        try
        {
            SetTopTabVisuals(_onlyMine);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "SetTopTabVisuals failed during initialization - buttons may not be available on this platform");
        }

        // initialize week range to rolling 7-day window (today + 7 days)
        _weekStart = DateTime.Now.Date;
        _weekEnd = _weekStart.AddDays(6);
        try 
        { 
            UpdateWeekLabel(); 
        } 
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "UpdateWeekLabel failed during initialization");
        }

        UpdateEmptyView();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _logger.LogTrace("CalendarPage OnAppearing");
        
        // Subscribe to events
        if (TabMyButton != null)
            TabMyButton.Clicked += OnTabMineClicked;
        if (TabAllButton != null)
            TabAllButton.Clicked += OnTabAllClicked;
        if (PrevWeekButton != null)
            PrevWeekButton.Clicked += OnPrevWeekClicked;
        if (NextWeekButton != null)
            NextWeekButton.Clicked += OnNextWeekClicked;
        if (TodayWeekButton != null)
            TodayWeekButton.Clicked += OnTodayWeekClicked;
        if (EventsRefresh != null)
            EventsRefresh.Refreshing += OnEventsRefresh;
        
        if (_suppressReloadOnNextAppearing)
        {
            _suppressReloadOnNextAppearing = false;
            return;
        }
        try
        {
            Dispatcher.Dispatch(async () =>
            {
                try 
                { 
                    await LoadEventsAsync(); 
                } 
                catch (Exception ex)
                {
                    _logger.LogError(ex, "LoadEventsAsync failed in OnAppearing");
                    await DisplayAlertAsync(
                        LocalizationService.Get("Error_Loading_Title") ?? "Error",
                        ex.Message,
                        LocalizationService.Get("Button_OK") ?? "OK");
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Dispatcher.Dispatch failed, using fallback");
            _ = LoadEventsAsync();
        }
    }

    protected override void OnDisappearing()
    {
        // Unsubscribe from events to prevent memory leaks
        if (TabMyButton != null)
            TabMyButton.Clicked -= OnTabMineClicked;
        if (TabAllButton != null)
            TabAllButton.Clicked -= OnTabAllClicked;
        if (PrevWeekButton != null)
            PrevWeekButton.Clicked -= OnPrevWeekClicked;
        if (NextWeekButton != null)
            NextWeekButton.Clicked -= OnNextWeekClicked;
        if (TodayWeekButton != null)
            TodayWeekButton.Clicked -= OnTodayWeekClicked;
        if (EventsRefresh != null)
            EventsRefresh.Refreshing -= OnEventsRefresh;
        
        base.OnDisappearing();
    }

    private static string NormalizeName(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return string.Empty;
        var normalized = s.Normalize(System.Text.NormalizationForm.FormD);
        var chars = normalized.Where(c => System.Globalization.CharUnicodeInfo.GetUnicodeCategory(c) != System.Globalization.UnicodeCategory.NonSpacingMark).ToArray();
        return new string(chars).ToLowerInvariant().Trim();
    }

    private async Task LoadEventsAsync()
    {
        _logger.LogDebug("LoadEventsAsync started - Mode: {Mode}, WeekStart: {WeekStart}, WeekEnd: {WeekEnd}", 
            _onlyMine ? "Mine" : "All", _weekStart, _weekEnd);

        // Ensure the RefreshView shows a refresh indicator when we start programmatically
        try 
        { 
            if (EventsRefresh != null && !EventsRefresh.IsRefreshing) 
                EventsRefresh.IsRefreshing = true; 
        } 
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to set RefreshView.IsRefreshing");
        }

        if (_isLoading)
        {
            _logger.LogDebug("LoadEventsAsync skipped - load already in progress");
            // If a load is already in progress, ensure RefreshView isn't left spinning.
            try 
            { 
                EventsRefresh?.IsRefreshing = false; 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to reset RefreshView.IsRefreshing");
            }
            return;
        }
        UpdateEmptyView();
        _isLoading = true;
        try 
        { 
            EventsScroll.IsVisible = false; 
        } 
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to hide EventsScroll");
        }
        try
        {
            // use selected week range (Monday..Sunday)
            var start = _weekStart.Date;
            // extend end by one day to be inclusive across timezones
            var end = _weekEnd.Date.AddDays(1);
            _logger.LogInformation("Fetching events: Start={Start:o}, End={End:o}, OnlyMine={OnlyMine}", 
                start, end, _onlyMine);
            
            using var perfLog = LoggerService.LogPerformance<CalendarPage>("LoadEvents" + (_onlyMine ? "Mine" : "All"));
            
            List<EventService.EventInstance> events;
            if (_onlyMine)
            {
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end);
            }
            else
            {
                // Load all events at once (fastest approach - single request)
                events = await EventService.GetEventInstancesForRangeListAsync(start, end);
            }
            
            _logger.LogInformation("Fetched {Count} events for week {WeekStart} - {WeekEnd}", 
                events.Count, _weekStart.ToShortDateString(), _weekEnd.ToShortDateString());
            
            // Clear previous views and tracking list
            EventsStack.Children.Clear();
            _trainerDetailRows.Clear();
            
            // Render all events
            RenderEvents(events);

            // Highlight rows in the "All" view when the registrant matches current user or user's active couples
            List<string> normalizedHighlights = new();
            if (!_onlyMine)
            {
                try
                {
                    var highlightNames = new List<string>();
                    static void AddNameVariants(List<string> list, string? name)
                    {
                        if (string.IsNullOrWhiteSpace(name)) return;
                        var v = name.Trim();
                        if (!list.Contains(v, StringComparer.OrdinalIgnoreCase)) list.Add(v);
                        // add surname (last token)
                        var parts = v.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                        if (parts.Length > 0)
                        {
                            var last = parts[^1];
                            if (!list.Contains(last, StringComparer.OrdinalIgnoreCase)) list.Add(last);
                        }
                    }
                    try
                    {
                        var currentUser = await UserService.GetCurrentUserAsync();
                        if (currentUser != null)
                        {
                            var full = ((currentUser.UJmeno ?? string.Empty) + " " + (currentUser.UPrijmeni ?? string.Empty)).Trim();
                            AddNameVariants(highlightNames, full);
                            AddNameVariants(highlightNames, currentUser.ULogin);
                        }
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning(ex, "Failed to fetch current user for highlighting");
                    }

                    try
                    {
                        var couples = await UserService.GetActiveCouplesFromUsersAsync();
                        foreach (var c in couples)
                        {
                            AddNameVariants(highlightNames, c.ManName);
                            AddNameVariants(highlightNames, c.WomanName);
                            AddNameVariants(highlightNames, string.IsNullOrWhiteSpace(c.ManName) || string.IsNullOrWhiteSpace(c.WomanName) ? null : c.ManName + " - " + c.WomanName);
                        }
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning(ex, "Failed to fetch active couples for highlighting");
                    }

                    var distinct = highlightNames.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
                    normalizedHighlights = distinct.Select(NormalizeName).Where(x => !string.IsNullOrWhiteSpace(x)).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
                    _logger.LogDebug("Highlight names for calendar: {Names}", string.Join(", ", distinct));
                    
                    foreach (var row in _trainerDetailRows)
                    {
                        var fr = row.FirstRegistrant ?? string.Empty;
                        if (string.IsNullOrWhiteSpace(fr))
                        {
                            row.IsHighlighted = false;
                            continue;
                        }
                        var frNorm = NormalizeName(fr);
                        bool matched = !string.IsNullOrWhiteSpace(frNorm) && normalizedHighlights.Any(h => frNorm.Contains(h) || h.Contains(frNorm));
                        row.IsHighlighted = matched;
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to process highlighting logic");
                }
            }

            // background fetch for missing first-registrant names using GetEventAsync
            _ = Task.Run(async () =>
            {
                try
                {
                    _logger.LogTrace("Starting background fetch for {Count} trainer detail rows", _trainerDetailRows.Count);
                    foreach (var row in _trainerDetailRows)
                    {
                        var inst = row.Instance;
                        var evt = inst?.Event;
                        if (!row.IsLoaded && evt != null && evt.Id != 0)
                        {
                            try
                            {
                                var id = evt.Id;
                                var details = await EventService.GetEventAsync(id);
                                string name = string.Empty;
                                try
                                {
                                    if (details?.EventRegistrations?.Nodes != null && details.EventRegistrations.Nodes.Count > 0)
                                    {
                                        var node = details.EventRegistrations.Nodes[0];
                                        if (node?.Person != null)
                                            name = (node.Person.FirstName + " " + (node.Person.LastName ?? string.Empty)).Trim();
                                        if (string.IsNullOrWhiteSpace(name) && node?.Couple != null)
                                        {
                                            var manLn = node.Couple.Man?.LastName;
                                            var womanLn = node.Couple.Woman?.LastName;
                                            if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn)) name = manLn + " - " + womanLn;
                                            else if (!string.IsNullOrWhiteSpace(manLn)) name = manLn;
                                            else if (!string.IsNullOrWhiteSpace(womanLn)) name = womanLn;
                                        }
                                    }
                                }
                                catch (Exception ex)
                                {
                                    _logger.LogWarning(ex, "Failed to extract registrant name from event {EventId}", id);
                                }
                                // Ensure we set the FirstRegistrant (may be empty) and mark row as loaded so triggers apply after fetch
                                Dispatcher?.Dispatch(() => {
                                    row.FirstRegistrant = name;
                                    row.IsLoaded = true;
                                    try
                                    {
                                        var frNorm = NormalizeName(row.FirstRegistrant);
                                        var matched = !string.IsNullOrWhiteSpace(frNorm) && normalizedHighlights.Any(h => frNorm.Contains(h) || h.Contains(frNorm));
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
                                // On any error, still mark row as loaded so UI doesn't stay in unknown state
                                Dispatcher?.Dispatch(() => { row.IsLoaded = true; });
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Background fetch failed unexpectedly");
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "LoadEventsAsync failed");
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            try 
            { 
                EventsScroll.IsVisible = true; 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to show EventsScroll");
            }
            try
            {
                if (EventsRefresh != null)
                    EventsRefresh.IsRefreshing = false;
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to stop RefreshView spinner");
            }
            _isLoading = false;
            UpdateEmptyView();
            _logger.LogDebug("LoadEventsAsync completed");
        }
    }

    private void RenderEvents(List<EventService.EventInstance> events)
    {
        // Clear existing UI
        EventsStack.Children.Clear();
        _trainerDetailRows.Clear();

        // Group events by day (date only) - no ISO week grouping, just single rolling 7-day window
        var groupsByDate = events
            .GroupBy(e => e.Since.HasValue ? e.Since.Value.Date : e.UpdatedAt.Date)
            .ToDictionary(g => g.Key, g => g.OrderBy(ev => ev.Since).ToList());

        // Single week map for current rolling window
        var weekMap = new SortedDictionary<DateTime, List<DateTime>>();
        weekMap[_weekStart] = groupsByDate.Keys.OrderBy(d => d).ToList();

        var culture = CultureInfo.CurrentUICulture ?? CultureInfo.CurrentCulture;

        foreach (var kv in weekMap)
        {
            var weekStart = kv.Key;
            var weekEnd = weekStart.AddDays(6);
            
            // Week header
            EventsStack.Children.Add(CreateWeekHeader($"{weekStart:dd.MM.yyyy} – {weekEnd:dd.MM.yyyy}"));

            foreach (var date in kv.Value.OrderBy(d => d))
            {
                if (!groupsByDate.TryGetValue(date, out var dayEvents) || dayEvents.Count == 0) continue;

                string dayLabel;
                var todayDate = DateTime.Now.Date;
                if (date == todayDate) dayLabel = LocalizationService.Get("Today") ?? "Dnes";
                else if (date == todayDate.AddDays(1)) dayLabel = LocalizationService.Get("Tomorrow") ?? "Zítra";
                else
                {
                    var name = culture.DateTimeFormat.GetDayName(date.DayOfWeek);
                    dayLabel = char.ToUpper(name[0], culture) + name.Substring(1) + $" {date:dd.MM.}";
                }

                // Day header
                EventsStack.Children.Add(CreateDayHeader(dayLabel, date));

                // Separate events into groupable (lesson with exactly one trainer) and single events
                var groupableEvents = new List<EventService.EventInstance>();
                var singleEventsList = new List<EventService.EventInstance>();

                foreach (var evt in dayEvents)
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

                // Add single events
                foreach (var evt in singleEventsList.OrderBy(e => e.Since ?? e.UpdatedAt))
                {
                    var since = evt.Since;
                    var until = evt.Until;
                    var timeRange = (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " – " + (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
                    var locationOrTrainers = ComputeLocationOrTrainers(evt);
                    var eventName = ComputeEventName(evt);
                    var eventTypeLabel = ComputeEventTypeLabel(evt.Event?.Type);

                    EventsStack.Children.Add(CreateSingleEventCard(evt, timeRange, locationOrTrainers, eventName, eventTypeLabel));
                }

                // Add grouped trainers
                var groups = groupableEvents.GroupBy(e => EventService.GetTrainerDisplayName(e.Event?.EventTrainersList?.FirstOrDefault())?.Trim() ?? string.Empty)
                    .OrderBy(g => g.Min(x => x.Since ?? x.UpdatedAt));

                foreach (var g in groups)
                {
                    var ordered = g.OrderBy(i => i.Since ?? i.UpdatedAt).ToList();
                    var trainerName = g.Key;
                    var representative = ordered.FirstOrDefault()?.Event?.EventTrainersList?.FirstOrDefault();
                    var trainerTitle = string.IsNullOrWhiteSpace(trainerName) ? LocalizationService.Get("Lessons") ?? "Lekce" : EventService.GetTrainerDisplayWithPrefix(representative).Trim();
                    var cohorts = ordered.FirstOrDefault()?.Event?.EventTargetCohortsList;

                    EventsStack.Children.Add(CreateTrainerGroupHeader(trainerTitle, cohorts));

                    for (int i = 0; i < ordered.Count; i++)
                    {
                        var inst = ordered[i];
                        var firstRegistrant = i == 0 ? ComputeFirstRegistrant(inst) : string.Empty;
                        var durationText = ComputeDuration(inst);
                        var detailRow = new TrainerDetailRow(inst, firstRegistrant, durationText);
                        _trainerDetailRows.Add(detailRow);
                        
                        EventsStack.Children.Add(CreateTrainerDetailRow(detailRow));
                    }
                }
            }
        }
    }

    private void AppendEvents(List<EventService.EventInstance> newBatch, List<EventService.EventInstance> allEvents)
    {
        // Efficiently append only the new events to existing UI structure
        // instead of rebuilding everything
        
        var culture = CultureInfo.CurrentUICulture ?? CultureInfo.CurrentCulture;
        
        // Group new events by date
        var newEventsByDate = newBatch
            .GroupBy(e => e.Since.HasValue ? e.Since.Value.Date : e.UpdatedAt.Date)
            .ToDictionary(g => g.Key, g => g.OrderBy(ev => ev.Since).ToList());
        
        foreach (var dateGroup in newEventsByDate.OrderBy(kv => kv.Key))
        {
            var date = dateGroup.Key;
            var newDayEvents = dateGroup.Value;
            
            // Check if this date already exists in UI
            var existingDayHeader = EventsStack.Children
                .OfType<Label>()
                .FirstOrDefault(l => l.BindingContext is DayHeaderRow dhr && dhr.Date == date);
            
            int insertIndex;
            if (existingDayHeader == null)
            {
                // New day - need to add day header and events in correct position
                string dayLabel;
                var todayDate = DateTime.Now.Date;
                if (date == todayDate) dayLabel = LocalizationService.Get("Today") ?? "Dnes";
                else if (date == todayDate.AddDays(1)) dayLabel = LocalizationService.Get("Tomorrow") ?? "Zítra";
                else
                {
                    var name = culture.DateTimeFormat.GetDayName(date.DayOfWeek);
                    dayLabel = char.ToUpper(name[0], culture) + name.Substring(1) + $" {date:dd.MM.}";
                }
                
                // Find correct position for this date
                insertIndex = FindInsertPositionForDate(date);
                EventsStack.Children.Insert(insertIndex, CreateDayHeader(dayLabel, date));
                insertIndex++; // Move past the header
            }
            else
            {
                // Day already exists - append to end of that day's events
                insertIndex = EventsStack.Children.IndexOf(existingDayHeader) + 1;
                // Skip existing events for this day
                while (insertIndex < EventsStack.Children.Count)
                {
                    var next = EventsStack.Children[insertIndex];
                    // Stop when we hit next day header or week header
                    if (next is Label label)
                    {
                        if (label.BindingContext is DayHeaderRow dhr && dhr.Date != date)
                            break;
                    }
                    if (next is VisualElement ve2 && ve2.BindingContext is WeekHeaderRow) // Week header
                        break;
                    insertIndex++;
                }
            }
            
            // Add new events for this day
            var groupableEvents = new List<EventService.EventInstance>();
            var singleEventsList = new List<EventService.EventInstance>();
            
            foreach (var evt in newDayEvents)
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
                var since = evt.Since;
                var until = evt.Until;
                var timeRange = (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " – " + (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
                var locationOrTrainers = ComputeLocationOrTrainers(evt);
                var eventName = ComputeEventName(evt);
                var eventTypeLabel = ComputeEventTypeLabel(evt.Event?.Type);
                
                EventsStack.Children.Insert(insertIndex++, CreateSingleEventCard(evt, timeRange, locationOrTrainers, eventName, eventTypeLabel));
            }
            
            // Add grouped trainers
                var groups = groupableEvents.GroupBy(e => EventService.GetTrainerDisplayName(e.Event?.EventTrainersList?.FirstOrDefault())?.Trim() ?? string.Empty)
                .OrderBy(g => g.Min(x => x.Since ?? x.UpdatedAt));
            
            foreach (var g in groups)
            {
                var ordered = g.OrderBy(i => i.Since ?? i.UpdatedAt).ToList();
                var trainerName = g.Key;
                var representative = ordered.FirstOrDefault()?.Event?.EventTrainersList?.FirstOrDefault();
                var trainerTitle = string.IsNullOrWhiteSpace(trainerName) ? LocalizationService.Get("Lessons") ?? "Lekce" : EventService.GetTrainerDisplayWithPrefix(representative).Trim();
                var cohorts = ordered.FirstOrDefault()?.Event?.EventTargetCohortsList;

                EventsStack.Children.Insert(insertIndex++, CreateTrainerGroupHeader(trainerTitle, cohorts));
                
                for (int i = 0; i < ordered.Count; i++)
                {
                    var inst = ordered[i];
                    var firstRegistrant = i == 0 ? ComputeFirstRegistrant(inst) : string.Empty;
                    var durationText = ComputeDuration(inst);
                    var detailRow = new TrainerDetailRow(inst, firstRegistrant, durationText);
                    _trainerDetailRows.Add(detailRow);
                    
                    EventsStack.Children.Insert(insertIndex++, CreateTrainerDetailRow(detailRow));
                }
            }
        }
    }
    
    private int FindInsertPositionForDate(DateTime targetDate)
    {
        // Find where to insert a new day header based on date order
        for (int i = 0; i < EventsStack.Children.Count; i++)
        {
            var child = EventsStack.Children[i];
            if (child is Label label && label.BindingContext is DayHeaderRow dhr)
            {
                if (dhr.Date > targetDate)
                    return i; // Insert before this day
            }
        }
        return EventsStack.Children.Count; // Append at end
    }

    // Public helper to request a refresh from external callers (e.g. AppShell after auth)
    public async Task RefreshEventsAsync()
    {
        try { EventsRefresh?.IsRefreshing = true; } catch { }
        await LoadEventsAsync();
    }

    private void UpdateEmptyView()
    {
        try
        {
            var onlyMine = _onlyMine;
            var emptyText = onlyMine ? (LocalizationService.Get("Empty_MyEvents") ?? "Nemáte žádné události.") : (LocalizationService.Get("Empty_NoEvents") ?? "Nejsou žádné události.");
            if (EmptyLabel != null)
            {
                EmptyLabel.Text = emptyText;
                var totalRows = EventsStack?.Children?.Count ?? 0;
                var showEmpty = !_isLoading && totalRows == 0;
                EmptyLabel.IsVisible = showEmpty;
                if (EventsScroll != null)
                    EventsScroll.IsVisible = !showEmpty;
            }
        }
        catch
        {
            // ignore UI update failures
        }
    }

    private async void OnEventCardTapped(object? sender, TappedEventArgs e)
    {
        if (sender is VisualElement ve && ve.BindingContext is EventService.EventInstance instance)
        {
            if (instance.IsCancelled) return;
            if (instance.Event?.Id is long eventId)
            {
                var page = new EventPage();
                if (eventId != 0) page.EventId = eventId;
                _suppressReloadOnNextAppearing = true;
                await Navigation.PushAsync(page);
            }
        }
    }

    private async void OnGroupedRowTapped(object? sender, TappedEventArgs e)
    {
        try
        {
            TrainerDetailRow? row = null;
            if (sender is VisualElement ve)
                row = ve.BindingContext as TrainerDetailRow;

            if (row?.Instance?.Event?.Id is long eventId)
            {
                if (row.Instance.IsCancelled) return;
                var page = new EventPage();
                if (eventId != 0) page.EventId = eventId;
                _suppressReloadOnNextAppearing = true;
                await Navigation.PushAsync(page);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OnEventCardTapped failed");
            await DisplayAlertAsync(
                LocalizationService.Get("Error_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }

    private void SetTopTabVisuals(bool myActive)
    {
        try
        {
            var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
            if (theme == AppTheme.Light)
            {
                if (myActive)
                {
                    TabMyButton.BackgroundColor = Colors.Black;
                    TabMyButton.TextColor = Colors.White;
                    TabAllButton.BackgroundColor = Colors.Transparent;
                    TabAllButton.TextColor = Colors.Black;
                }
                else
                {
                    TabAllButton.BackgroundColor = Colors.Black;
                    TabAllButton.TextColor = Colors.White;
                    TabMyButton.BackgroundColor = Colors.Transparent;
                    TabMyButton.TextColor = Colors.Black;
                }
            }
            else
            {
                if (myActive)
                {
                    TabMyButton.BackgroundColor = Colors.LightGray;
                    TabMyButton.TextColor = Colors.Black;
                    TabAllButton.BackgroundColor = Colors.Transparent;
                    TabAllButton.TextColor = Colors.White;
                }
                else
                {
                    TabAllButton.BackgroundColor = Colors.LightGray;
                    TabAllButton.TextColor = Colors.Black;
                    TabMyButton.BackgroundColor = Colors.Transparent;
                    TabMyButton.TextColor = Colors.White;
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "SetTopTabVisuals failed - tab buttons may not be styled correctly");
        }
    }

    private async void OnTabMineClicked(object? sender, EventArgs e)
    {
        try
        {
            _logger.LogTrace("Tab 'Mine' clicked");
            _onlyMine = true;
            try 
            { 
                SetTopTabVisuals(true); 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "SetTopTabVisuals failed in OnTabMineClicked");
            }
            UpdateEmptyView();
            await LoadEventsAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OnTabMineClicked failed");
            try 
            { 
                await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); 
            } 
            catch (Exception displayEx)
            {
                _logger.LogError(displayEx, "Failed to display error alert");
            }
        }
    }

    private async void OnTabAllClicked(object? sender, EventArgs e)
    {
        try
        {
            _logger.LogTrace("Tab 'All' clicked");
            _onlyMine = false;
            try 
            { 
                SetTopTabVisuals(false); 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "SetTopTabVisuals failed in OnTabAllClicked");
            }
            UpdateEmptyView();
            await LoadEventsAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OnTabAllClicked failed");
            try 
            { 
                await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); 
            } 
            catch (Exception displayEx)
            {
                _logger.LogError(displayEx, "Failed to display error alert");
            }
        }
    }

    private async void OnEventsRefresh(object? sender, EventArgs e)
    {
        _logger.LogTrace("Events refresh triggered");
        await LoadEventsAsync();
        try
        {
            if (EventsRefresh != null)
                EventsRefresh.IsRefreshing = false;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to stop RefreshView after manual refresh");
        }
    }

    // Week helpers and handlers
    private static DateTime GetWeekStart(DateTime dt)
    {
        // Rolling 7-day window starts from today
        return dt.Date;
    }

    private void UpdateWeekLabel()
    {
        // Intentionally empty — UI should not display the week range here.
        return;
    }

    private async void OnPrevWeekClicked(object? sender, EventArgs e)
    {
        try
        {
            _weekStart = _weekStart.AddDays(-7);
            _weekEnd = _weekStart.AddDays(6);
            _logger.LogDebug("Week navigation: Previous week selected - {WeekStart} to {WeekEnd}", 
                _weekStart.ToShortDateString(), _weekEnd.ToShortDateString());
            try 
            { 
                UpdateWeekLabel(); 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "UpdateWeekLabel failed in OnPrevWeekClicked");
            }
            await LoadEventsAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OnPrevWeekClicked failed");
            try 
            { 
                await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); 
            } 
            catch (Exception displayEx)
            {
                _logger.LogError(displayEx, "Failed to display error alert");
            }
        }
    }

    private async void OnNextWeekClicked(object? sender, EventArgs e)
    {
        try
        {
            _weekStart = _weekStart.AddDays(7);
            _weekEnd = _weekStart.AddDays(6);
            _logger.LogDebug("Week navigation: Next week selected - {WeekStart} to {WeekEnd}", 
                _weekStart.ToShortDateString(), _weekEnd.ToShortDateString());
            try 
            { 
                UpdateWeekLabel(); 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "UpdateWeekLabel failed in OnNextWeekClicked");
            }
            await LoadEventsAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OnNextWeekClicked failed");
            try 
            { 
                await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); 
            } 
            catch (Exception displayEx)
            {
                _logger.LogError(displayEx, "Failed to display error alert");
            }
        }
    }

    private async void OnTodayWeekClicked(object? sender, EventArgs e)
    {
        try
        {
            var today = DateTime.Now.Date;
            // If already on today's week, don't reload
            if (_weekStart == today)
            {
                _logger.LogDebug("Today button clicked but already on today's week - skipping reload");
                return;
            }
            
            _weekStart = today;
            _weekEnd = _weekStart.AddDays(6);
            _logger.LogDebug("Week navigation: Today clicked - resetting to current week {WeekStart} to {WeekEnd}", 
                _weekStart.ToShortDateString(), _weekEnd.ToShortDateString());
            try 
            { 
                UpdateWeekLabel(); 
            } 
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "UpdateWeekLabel failed in OnTodayWeekClicked");
            }
            await LoadEventsAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OnTodayWeekClicked failed");
            try 
            { 
                await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); 
            } 
            catch (Exception displayEx)
            {
                _logger.LogError(displayEx, "Failed to display error alert");
            }
        }
    }

    private async void OnOpenCalendarViewClicked(object? sender, EventArgs e)
    {
        try
        {
            var page = new CalendarViewPage();
            _suppressReloadOnNextAppearing = true;
            await Navigation.PushAsync(page);
        }
        catch { }
    }

    private static new async Task DisplayAlertAsync(string? title, string? message, string? cancel)
    {
        try
        {
            var window = Application.Current?.Windows?.FirstOrDefault();
            if (window?.Page != null)
                await window.Page.DisplayAlertAsync(title ?? string.Empty, message ?? string.Empty, cancel ?? "OK");
        }
        catch { }
    }

    // Helper methods for computing display strings (replacing converter logic)
    private static string ComputeEventName(EventService.EventInstance inst)
    {
        try
        {
            var evt = inst.Event;
            if (evt == null) return string.Empty;
            return evt.Name ?? string.Empty;
        }
        catch { return string.Empty; }
    }

    private static string ComputeEventTypeLabel(string? type)
    {
        try
        {
            if (string.IsNullOrEmpty(type)) return string.Empty;
            return type.ToUpperInvariant() switch
            {
                "CAMP" => LocalizationService.Get("EventType_Camp") ?? "soustředění",
                "LESSON" => LocalizationService.Get("EventType_Lesson") ?? "lekce",
                "HOLIDAY" => LocalizationService.Get("EventType_Holiday") ?? "prázdniny",
                "RESERVATION" => LocalizationService.Get("EventType_Reservation") ?? "rezervace",
                "GROUP" => LocalizationService.Get("EventType_Group") ?? "vedená",
                _ => type
            };
        }
        catch { return string.Empty; }
    }

    private static string ComputeLocationOrTrainers(EventService.EventInstance inst)
    {
        try
        {
            var evt = inst.Event;
            if (evt == null) return string.Empty;

            // Try locationText first (preferred)
            if (!string.IsNullOrWhiteSpace(evt.LocationText))
                return evt.LocationText;

            // Fallback to trainers
            var trainers = evt.EventTrainersList?.Select(t => EventService.GetTrainerDisplayName(t)).Where(n => !string.IsNullOrWhiteSpace(n)).ToList() ?? new List<string>();
            if (trainers.Count > 0)
                return string.Join(", ", trainers);

            return string.Empty;
        }
        catch { return string.Empty; }
    }

    private static string ComputeFirstRegistrant(EventService.EventInstance inst)
    {
        try
        {
            // prefer eventRegistrationsList if available
            var evt = inst.Event;
            if (evt?.EventRegistrationsList != null && evt.EventRegistrationsList.Count > 0)
            {
                var regs = evt.EventRegistrationsList;
                int count = regs.Count;

                // collect best-possible surname/identifier for each registration
                var surnames = new System.Collections.Generic.List<string>();
                static string ExtractSurname(string full)
                {
                    if (string.IsNullOrWhiteSpace(full)) return string.Empty;
                    var parts = full.Split(' ', System.StringSplitOptions.RemoveEmptyEntries);
                    return parts.Length > 1 ? parts[parts.Length - 1] : full;
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
                    catch { }
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
                else // count >= 3
                {
                    if (count == 3)
                    {
                        if (surnames.Count > 0) return string.Join(", ", surnames);
                    }
                    else // > 3
                    {
                        var take = surnames.Take(2).ToList();
                        if (take.Count > 0) return string.Join(", ", take) + "...";
                    }
                }
            }

            // fall back to tenant.couplesList (existing logic)
            var tenant = inst.Tenant;
            if (tenant?.CouplesList != null && tenant.CouplesList.Count > 0)
            {
                var c = tenant.CouplesList[0];
                EventService.Person? p = c.Man ?? c.Woman;
                if (p != null)
                {
                    if (!string.IsNullOrWhiteSpace(p.FirstName))
                        return (p.FirstName + " " + (p.Name ?? p.LastName ?? string.Empty)).Trim();
                    return ((p.Name ?? string.Empty) + (string.IsNullOrWhiteSpace(p.LastName) ? string.Empty : " " + p.LastName)).Trim();
                }
            }
        }
        catch { }
        return string.Empty;
    }

    private static string ComputeDuration(EventService.EventInstance inst)
    {
        try
        {
            if (inst.Since.HasValue && inst.Until.HasValue)
            {
                var mins = (int)(inst.Until.Value - inst.Since.Value).TotalMinutes;
                return mins > 0 ? mins + "'" : string.Empty;
            }
        }
        catch { }
        return string.Empty;
    }

    // View factory methods for static UI generation
    private View CreateWeekHeader(string weekLabel)
    {
        var grid = new Grid
        {
            Margin = new Thickness(6, 12, 6, 0),
            BindingContext = new WeekHeaderRow(weekLabel)
        };

        var box = new BoxView();
        try { box.Style = (Style)Application.Current!.Resources["GroupHeaderBackground"]; } catch { box.Color = Colors.Transparent; }
        var label = new Label
        {
            Text = weekLabel,
            FontAttributes = FontAttributes.Bold,
            HorizontalTextAlignment = TextAlignment.Center,
            VerticalTextAlignment = TextAlignment.Center,
            Padding = new Thickness(8)
        };

        grid.Add(box);
        grid.Add(label);
        return grid;
    }

    private Label CreateDayHeader(string dayLabel, DateTime date)
    {
        var label = new Label
        {
            Text = dayLabel,
            FontAttributes = FontAttributes.Bold,
            Margin = new Thickness(6, 8, 6, 4),
            BindingContext = new DayHeaderRow(dayLabel, date) // For scroll-to functionality
        };
        return label;
    }

    private View CreateSingleEventCard(EventService.EventInstance instance, string timeRange, string locationOrTrainers, string eventName, string eventTypeLabel)
    {
        var grid = new Grid
        {
            Margin = new Thickness(6),
            BindingContext = instance
        };

        var tapGesture = new TapGestureRecognizer();
        tapGesture.Tapped += OnEventCardTapped;
        grid.GestureRecognizers.Add(tapGesture);

        var background = new BoxView();
        try { background.Style = (Style)Application.Current!.Resources["SelectableItemBackground"]; } catch { background.Color = Colors.Transparent; }
        var stack = new VerticalStackLayout { Spacing = 6, Padding = new Thickness(12) };

        // Title row
        var titleGrid = new Grid
        {
            ColumnDefinitions = new ColumnDefinitionCollection
            {
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto }
            }
        };

        var titleLabel = new Label
        {
            Text = eventName,
            FontAttributes = FontAttributes.Bold
        };
        if (instance.IsCancelled)
            titleLabel.TextDecorations = TextDecorations.Strikethrough;
        titleGrid.Add(titleLabel, 0);

        var typeWithColorStack = new HorizontalStackLayout
        {
            Spacing = 6,
            HorizontalOptions = LayoutOptions.End,
            VerticalOptions = LayoutOptions.Center
        };

        var typeLabel = new Label
        {
            Text = eventTypeLabel,
            FontSize = 12,
            Style = (Style)Application.Current!.Resources["MutedLabelStyle"],
            VerticalOptions = LayoutOptions.Center
        };
        typeWithColorStack.Add(typeLabel);

        // Add cohort color circle if available (to the right of the label)
        var cohorts = instance.Event?.EventTargetCohortsList;
        if (cohorts != null && cohorts.Count > 0)
        {
            var firstCohort = cohorts[0];
            var colorRgb = firstCohort.Cohort?.ColorRgb;
            if (!string.IsNullOrWhiteSpace(colorRgb))
            {
                var colorBrush = TryParseColorBrush(colorRgb);
                    if (colorBrush != null)
                    {
                        var circle = new BoxView();
                        try { circle.Style = (Style)Application.Current!.Resources["CohortCircle"]; } catch { circle.WidthRequest = 12; circle.HeightRequest = 12; circle.CornerRadius = 6; }
                        if (colorBrush is SolidColorBrush scb)
                            circle.Color = scb.Color;
                        else
                            circle.Background = colorBrush;
                        typeWithColorStack.Add(circle);
                    }
            }
        }

        titleGrid.Add(typeWithColorStack, 1);

        stack.Add(titleGrid);
        stack.Add(new Label { Text = locationOrTrainers });
        stack.Add(new Label
        {
            Text = timeRange,
            FontSize = 12,
            Style = (Style)Application.Current!.Resources["MutedLabelStyle"]
        });

        grid.Add(background);
        grid.Add(stack);
        return grid;
    }

    private View CreateTrainerGroupHeader(string trainerTitle, List<EventService.EventTargetCohortLink>? cohorts = null)
    {
        var outer = new Grid
        {
            Margin = new Thickness(6, 6, 6, 0)
        };

        var background = new BoxView();
        try { background.Style = (Style)Application.Current!.Resources["GroupHeaderBackground"]; } catch { background.Color = Colors.Transparent; }

        var grid = new Grid
        {
            Padding = new Thickness(12, 8),
            ColumnDefinitions = new ColumnDefinitionCollection
            {
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto }
            }
        };

        grid.Add(new Label
        {
            Text = trainerTitle,
            FontAttributes = FontAttributes.Bold
        }, 0);

        var typeWithColorStack = new HorizontalStackLayout
        {
            Spacing = 6,
            HorizontalOptions = LayoutOptions.End,
            VerticalOptions = LayoutOptions.Center
        };

        typeWithColorStack.Add(new Label
        {
            Text = LocalizationService.Get("EventType_Lesson") ?? "Lekce",
            FontSize = 12,
            Style = (Style)Application.Current!.Resources["MutedLabelStyle"],
            VerticalOptions = LayoutOptions.Center
        });

        // Add cohort color circle if available (to the right of the label)
        if (cohorts != null && cohorts.Count > 0)
        {
            var firstCohort = cohorts[0];
            var colorRgb = firstCohort.Cohort?.ColorRgb;
            if (!string.IsNullOrWhiteSpace(colorRgb))
            {
                var colorBrush = TryParseColorBrush(colorRgb);
                    if (colorBrush != null)
                    {
                        var circle = new BoxView();
                        try { circle.Style = (Style)Application.Current!.Resources["CohortCircle"]; } catch { circle.WidthRequest = 12; circle.HeightRequest = 12; circle.CornerRadius = 6; }
                        if (colorBrush is SolidColorBrush scb)
                            circle.Color = scb.Color;
                        else
                            circle.Background = colorBrush;
                        typeWithColorStack.Add(circle);
                    }
            }
        }

        grid.Add(typeWithColorStack, 1);
        outer.Add(background);
        outer.Add(grid);
        return outer;
    }

    private View CreateTrainerDetailRow(TrainerDetailRow row)
    {
        var outer = new Grid
        {
            Margin = new Thickness(6, 3),
            BindingContext = row
        };

        var background = new BoxView();
        try { background.Style = (Style)Application.Current!.Resources["SelectableItemBackground"]; } catch { background.Color = Colors.Transparent; }

        var grid = new Grid
        {
            ColumnDefinitions = new ColumnDefinitionCollection
            {
                new ColumnDefinition { Width = new GridLength(120, GridUnitType.Absolute) },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto }
            },
            Padding = new Thickness(12, 8),
            ColumnSpacing = 8
        };

        var tapGesture = new TapGestureRecognizer();
        tapGesture.Tapped += OnGroupedRowTapped;
        grid.GestureRecognizers.Add(tapGesture);

        var timeLabel = new Label();
        timeLabel.SetBinding(Label.TextProperty, nameof(TrainerDetailRow.TimeRange));
        if (row.IsCancelled)
            timeLabel.TextDecorations = TextDecorations.Strikethrough;
        grid.Add(timeLabel, 0);

        // Cohort color circle as BoxView
        var cohortList = row.Instance?.Event?.EventTargetCohortsList;
        if (cohortList != null && cohortList.Count > 0)
        {
            var firstCohort = cohortList[0];
            var colorBrush = TryParseColorBrush(firstCohort?.Cohort?.ColorRgb);
                if (colorBrush != null)
                {
                    var circle = new BoxView();
                    try { circle.Style = (Style)Application.Current!.Resources["CohortCircleLarge"]; } catch { circle.WidthRequest = 14; circle.HeightRequest = 14; circle.CornerRadius = 7; }
                    if (colorBrush is SolidColorBrush scb)
                        circle.Color = scb.Color;
                    else
                        circle.Background = colorBrush;
                    grid.Add(circle, 1);
                }
        }

        var registrantLabel = new Label();
        registrantLabel.SetBinding(Label.TextProperty, nameof(TrainerDetailRow.FirstRegistrant));
        
        // Dynamic styling based on properties
        row.PropertyChanged += (s, e) =>
        {
            if (e.PropertyName == nameof(TrainerDetailRow.IsFree))
            {
                if (row.IsFree)
                {
                    registrantLabel.Text = LocalizationService.Get("Free") ?? "Volno";
                    registrantLabel.TextColor = Colors.Green;
                }
            }
            else if (e.PropertyName == nameof(TrainerDetailRow.IsHighlighted))
            {
                registrantLabel.FontAttributes = row.IsHighlighted ? FontAttributes.Bold : FontAttributes.None;
            }
        };
        
        if (row.IsCancelled)
            registrantLabel.TextDecorations = TextDecorations.Strikethrough;
        grid.Add(registrantLabel, 2);

        var durationLabel = new Label
        {
            HorizontalOptions = LayoutOptions.End
        };
        durationLabel.SetBinding(Label.TextProperty, nameof(TrainerDetailRow.DurationText));
        if (row.IsCancelled)
            durationLabel.TextDecorations = TextDecorations.Strikethrough;
        grid.Add(durationLabel, 3);

        outer.Add(background);
        outer.Add(grid);
        return outer;
    }

    private Microsoft.Maui.Controls.Brush? TryParseColorBrush(string? colorRgb)
    {
        if (string.IsNullOrWhiteSpace(colorRgb)) return null;
        var s = colorRgb.Trim();
        try
        {
            if (s.StartsWith("#"))
                return new SolidColorBrush(Color.FromArgb(s));

            if (s.Length == 6)
                return new SolidColorBrush(Color.FromArgb("#" + s));

            if (s.StartsWith("rgb", StringComparison.OrdinalIgnoreCase))
            {
                var digits = System.Text.RegularExpressions.Regex.Matches(s, "\\d+");
                if (digits.Count >= 3)
                {
                    var r = int.Parse(digits[0].Value);
                    var g = int.Parse(digits[1].Value);
                    var b = int.Parse(digits[2].Value);
                    return new SolidColorBrush(Color.FromRgb(r, g, b));
                }
            }
        }
        catch { }
        return null;
    }
}

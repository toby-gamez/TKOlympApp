using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Controls.Shapes;
using Microsoft.Maui.Devices;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.Layouts;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Globalization;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Pages;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

/// <summary>
/// ViewModel for CalendarViewPage that manages timeline rendering and event display.
/// Handles day/3-day/week views, My/All filtering, and real-time "now" line updates.
/// </summary>
public partial class CalendarViewViewModel : ViewModelBase
{
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    
    private readonly int _startHour = 6;
    private readonly int _endHour = 22;
    private readonly double _hourHeight = 60; // pixels per hour
    private CancellationTokenSource? _timerCts;
    private BoxView? _nowLine;
    private double _columnGapDp = -1;

    [ObservableProperty]
    private string _dateLabel = string.Empty;

    [ObservableProperty]
    private bool _onlyMine = true;

    [ObservableProperty]
    private int _daysVisible = 1;

    [ObservableProperty]
    private DateTime _date = DateTime.Now.Date;

    [ObservableProperty]
    private bool _isLoading = false;

    [ObservableProperty]
    private bool _viewDayEnabled = false;

    [ObservableProperty]
    private bool _view3DayEnabled = true;

    [ObservableProperty]
    private bool _viewWeekEnabled = true;

    // UI references that the View will set
    public AbsoluteLayout? TimelineLayout { get; set; }
    public VerticalStackLayout? TimeLabelsStack { get; set; }

    // Track if tab visuals need updating (set by View)
    public Action<bool>? SetTopTabVisualsAction { get; set; }
    public Action? UpdateViewButtonsVisualsAction { get; set; }

    public CalendarViewViewModel(
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService)
    {
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        
        UpdateDateLabel();
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadEventsAsync();
        StartNowTimer();
    }

    public override Task OnDisappearingAsync()
    {
        StopNowTimer();
        return base.OnDisappearingAsync();
    }

    public void SetColumnGapDp(double gapDp)
    {
        _columnGapDp = gapDp;
    }

    public void SetColumnGapMm(double mm)
    {
        var gapDp = mm * 160.0 / 25.4;
        _columnGapDp = gapDp;
    }

    private double GutterProportionFromDp(double gapDp)
    {
        try
        {
            var info = DeviceDisplay.MainDisplayInfo;
            var screenWidthDp = info.Width / info.Density;
            if (screenWidthDp <= 0) return 0.01;
            var prop = gapDp / screenWidthDp;
            var clamped = Math.Clamp(prop, 0.0, 0.5);
            Debug.WriteLine($"CalendarView: gapDp={gapDp:F2}, screenWidthDp={screenWidthDp:F2}, prop={prop:F4}, clamped={clamped:F4}");
            return clamped;
        }
        catch
        {
            return 0.01;
        }
    }

    [RelayCommand]
    private async Task PrevDay()
    {
        Date = Date.AddDays(-DaysVisible);
        UpdateDateLabel();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task NextDay()
    {
        Date = Date.AddDays(DaysVisible);
        UpdateDateLabel();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task Today()
    {
        var today = DateTime.Now.Date;
        if (Date == today) return;
        Date = today;
        UpdateDateLabel();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task TabMine()
    {
        OnlyMine = true;
        SetTopTabVisualsAction?.Invoke(true);
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task TabAll()
    {
        OnlyMine = false;
        SetTopTabVisualsAction?.Invoke(false);
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task ViewDay()
    {
        if (DaysVisible == 1) return;
        DaysVisible = 1;
        UpdateViewButtonsVisuals();
        UpdateDateLabel();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task View3Day()
    {
        if (DaysVisible == 3) return;
        DaysVisible = 3;
        UpdateViewButtonsVisuals();
        UpdateDateLabel();
        await LoadEventsAsync();
    }

    [RelayCommand]
    private async Task ViewWeek()
    {
        if (DaysVisible == 7) return;
        DaysVisible = 7;
        UpdateViewButtonsVisuals();
        UpdateDateLabel();
        await LoadEventsAsync();
    }

    private void UpdateViewButtonsVisuals()
    {
        ViewDayEnabled = (DaysVisible != 1);
        View3DayEnabled = (DaysVisible != 3);
        ViewWeekEnabled = (DaysVisible != 7);
        UpdateViewButtonsVisualsAction?.Invoke();
    }

    private void UpdateDateLabel()
    {
        if (DaysVisible <= 1)
        {
            // Single day: show only weekday when the date is in the current week, otherwise show short date (no year)
            var today = DateTime.Now.Date;
            var firstDayOfWeek = today.AddDays(-((int)today.DayOfWeek - (int)CultureInfo.CurrentCulture.DateTimeFormat.FirstDayOfWeek + 7) % 7);
            var lastDayOfWeek = firstDayOfWeek.AddDays(6);
            if (Date.Date >= firstDayOfWeek && Date.Date <= lastDayOfWeek)
            {
                var weekday = Date.ToString("dddd", CultureInfo.CurrentCulture);
                try { weekday = weekday.ToLower(CultureInfo.CurrentCulture); } catch { }
                DateLabel = weekday;
            }
            else
            {
                DateLabel = Date.ToString("d. M.", CultureInfo.CurrentCulture);
            }
        }
        else
        {
            var start = Date.Date;
            var end = start.AddDays(DaysVisible - 1);
            if (DaysVisible == 7)
            {
                // Week view: show the date range (short, without year)
                DateLabel = start.ToString("d. M.", CultureInfo.CurrentCulture) + " – " + end.ToString("d. M.", CultureInfo.CurrentCulture);
            }
            else
            {
                // multi-day (1<days<=3): show short range without year, e.g. "30. 1. – 1. 2."
                DateLabel = start.ToString("d. M.", CultureInfo.CurrentCulture) + " – " + end.ToString("d. M.", CultureInfo.CurrentCulture);
            }
        }
    }

    private async Task LoadEventsAsync()
    {
        if (IsLoading) return;
        IsLoading = true;
        try
        {
            var start = Date.Date;
            var end = start.AddDays(DaysVisible);
            List<EventInstance> events;
            if (OnlyMine)
                events = await _eventService.GetMyEventInstancesForRangeAsync(start, end);
            else
                events = await _eventService.GetEventInstancesForRangeListAsync(start, end);

            await MainThread.InvokeOnMainThreadAsync(() => RenderTimeline(events ?? new List<EventInstance>()));
        }
        catch (Exception ex)
        {
            await MainThread.InvokeOnMainThreadAsync(async () =>
            {
                await Application.Current?.MainPage?.DisplayAlert(
                    LocalizationService.Get("Error_Loading_Title") ?? "Chyba",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK")!;
            });
        }
        finally
        {
            IsLoading = false;
        }
    }

    private static string NormalizeName(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return string.Empty;
        var normalized = s.Normalize(System.Text.NormalizationForm.FormD);
        var chars = normalized.Where(c => System.Globalization.CharUnicodeInfo.GetUnicodeCategory(c) != System.Globalization.UnicodeCategory.NonSpacingMark).ToArray();
        return new string(chars).ToLowerInvariant().Trim();
    }

    private async Task RenderTimeline(List<EventInstance> events)
    {
        if (TimelineLayout == null || TimeLabelsStack == null) return;

        TimelineLayout.Children.Clear();
        TimeLabelsStack.Children.Clear();

        var totalHours = Math.Max(1, _endHour - _startHour);
        var totalHeight = totalHours * _hourHeight;
        var halfHourOffset = _hourHeight / 2.0;

        // Determine line color for hour/vertical separators
        var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
        Color lineColor = theme == AppTheme.Dark ? Color.FromArgb("#444444") : Colors.LightGray;
        double lineOpacity = 0.6;

        for (int h = _startHour; h <= _endHour; h++)
        {
            var y = (h - _startHour) * _hourHeight;
            var label = new Label { Text = h.ToString("D2") + ":00", FontSize = 12, HeightRequest = _hourHeight, VerticalTextAlignment = TextAlignment.Center };
            TimeLabelsStack.Children.Add(label);

            var line = new BoxView { HeightRequest = 1, BackgroundColor = lineColor, Opacity = lineOpacity };
            AbsoluteLayout.SetLayoutBounds(line, new Rect(0, y + halfHourOffset, 1, 1));
            AbsoluteLayout.SetLayoutFlags(line, AbsoluteLayoutFlags.WidthProportional);
            TimelineLayout.Children.Add(line);
        }

        // Prepare highlight names when showing "All" so we can mark my events
        List<string> normalizedHighlights = new();
        if (!OnlyMine)
        {
            try
            {
                var highlightNames = new List<string>();
                static void AddNameVariants(List<string> list, string? name)
                {
                    if (string.IsNullOrWhiteSpace(name)) return;
                    var v = name.Trim();
                    if (!list.Contains(v, StringComparer.OrdinalIgnoreCase)) list.Add(v);
                    var parts = v.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                    if (parts.Length > 0)
                    {
                        var last = parts[^1];
                        if (!list.Contains(last, StringComparer.OrdinalIgnoreCase)) list.Add(last);
                    }
                }
                try
                {
                    var currentUser = await _userService.GetCurrentUserAsync();
                    if (currentUser != null)
                    {
                        var full = ((currentUser.UJmeno ?? string.Empty) + " " + (currentUser.UPrijmeni ?? string.Empty)).Trim();
                        AddNameVariants(highlightNames, full);
                        AddNameVariants(highlightNames, currentUser.ULogin);
                    }
                }
                catch { }

                try
                {
                    var couples = await _userService.GetActiveCouplesFromUsersAsync();
                    foreach (var c in couples)
                    {
                        AddNameVariants(highlightNames, c.ManName);
                        AddNameVariants(highlightNames, c.WomanName);
                        AddNameVariants(highlightNames, string.IsNullOrWhiteSpace(c.ManName) || string.IsNullOrWhiteSpace(c.WomanName) ? null : c.ManName + " - " + c.WomanName);
                    }
                }
                catch { }

                var distinct = highlightNames.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
                normalizedHighlights = distinct.Select(NormalizeName).Where(x => !string.IsNullOrWhiteSpace(x)).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            }
            catch { }
        }

        // Filter out cancelled event instances
        events = events.Where(e => !(e.IsCancelled)).ToList();
        var startDate = Date.Date;
        var items = events.OrderBy(e => e.Since ?? e.UpdatedAt)
            .Select(e =>
            {
                var since = e.Since ?? e.UpdatedAt;
                var until = e.Until ?? since.AddMinutes(30);
                var dayIndex = (since.Date - startDate).Days;
                var rawStart = since.Hour * 60 + since.Minute;
                var duration = Math.Max(15, (int)(until - since).TotalMinutes);
                var rawEnd = rawStart + duration;
                var startMinutes = rawStart - (_startHour * 60);
                var top = startMinutes / 60.0 * _hourHeight;
                var height = duration / 60.0 * _hourHeight;
                return new LayoutItem { Inst = e, Top = top, Height = height, RawStart = rawStart, RawEnd = rawEnd, ColumnIndex = dayIndex };
            })
            .ToList();

        // Group items into overlapping clusters (transitive overlap)
        var groups = new List<List<LayoutItem>>();
        List<LayoutItem>? current = null;
        int currentMaxEnd = -1;
        foreach (var it in items)
        {
            if (current == null)
            {
                current = new List<LayoutItem> { it };
                currentMaxEnd = it.RawEnd;
                groups.Add(current);
                continue;
            }
            if (it.RawStart < currentMaxEnd)
            {
                current.Add(it);
                if (it.RawEnd > currentMaxEnd) currentMaxEnd = it.RawEnd;
            }
            else
            {
                current = new List<LayoutItem> { it };
                currentMaxEnd = it.RawEnd;
                groups.Add(current);
            }
        }

        // For each group, assign columns using greedy packing
        foreach (var group in groups)
        {
            group.Sort((a, b) => a.RawStart.CompareTo(b.RawStart));
            var colEnds = new List<int>();
            foreach (var it in group)
            {
                int assigned = -1;
                for (int c = 0; c < colEnds.Count; c++)
                {
                    if (it.RawStart >= colEnds[c])
                    {
                        assigned = c;
                        colEnds[c] = it.RawEnd;
                        break;
                    }
                }
                if (assigned == -1)
                {
                    colEnds.Add(it.RawEnd);
                    assigned = colEnds.Count - 1;
                }
                var prevDay = it.ColumnIndex;
                it.ColumnIndex = assigned;
            }
            var finalCols = colEnds.Count;
            foreach (var it in group) it.ColumnCount = finalCols;
        }

        // Render items
        var infoMain = DeviceDisplay.MainDisplayInfo;
        double screenWidthDp = infoMain.Width / infoMain.Density;
        double layoutWidthDp = TimelineLayout.Width;
        if (layoutWidthDp <= 1.0) layoutWidthDp = screenWidthDp;

        double dayGapDp = 8.0;
        var days = Math.Max(1, DaysVisible);

        const double marginRightProp = 0.03;
        var gutterPropGlobal = _columnGapDp > 0 ? GutterProportionFromDp(_columnGapDp) : 0.01;
        double gutterDpGlobal = _columnGapDp > 0 ? _columnGapDp : gutterPropGlobal * screenWidthDp;
        double marginRightDpGlobal = marginRightProp * screenWidthDp;

        var totalDayGaps = dayGapDp * (days - 1);
        double labelWidthDp = TimeLabelsStack?.WidthRequest > 0 ? TimeLabelsStack.WidthRequest : 56.0;
        double pagePaddingDp = 24.0;
        var availableViewportDp = Math.Max(120.0, screenWidthDp - labelWidthDp - pagePaddingDp);

        double dayWidthDp;
        if (days <= 3)
        {
            dayWidthDp = Math.Max(100.0, (availableViewportDp - totalDayGaps) / days);
            try { TimelineLayout.WidthRequest = availableViewportDp; } catch { }
        }
        else
        {
            dayWidthDp = Math.Max(120.0, screenWidthDp / 3.0);
            var totalContentWidthDp = days * dayWidthDp + totalDayGaps + marginRightDpGlobal;
            try { TimelineLayout.WidthRequest = totalContentWidthDp; } catch { }
        }

        foreach (var it in items)
        {
            var inst = it.Inst;
            var top = it.Top;
            var height = it.Height;
            if (top + height < 0 || top > totalHeight) continue;

            var since = inst.Since ?? inst.UpdatedAt;
            var dayIndex = (since.Date - startDate).Days;
            if (dayIndex < 0 || dayIndex >= days) continue;

            Color bgColor = Colors.LightBlue;
            if (string.Equals(inst.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var appTheme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
                    var key = appTheme == AppTheme.Dark ? "SecondaryDark" : "Secondary";
                    if (Application.Current?.Resources.TryGetValue(key, out var res) == true)
                    {
                        if (res is Color c) bgColor = c;
                    }
                }
                catch { }
            }

            var frame = new Border
            {
                Padding = new Thickness(6),
                Background = new SolidColorBrush(bgColor),
                StrokeShape = new RoundRectangle { CornerRadius = 6 },
                BindingContext = inst
            };

            string titleText = inst.Event?.Name ?? string.Empty;
            if (string.Equals(inst.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var first = TkOlympApp.MainPage.GroupedEventRow.ComputeFirstRegistrantPublic(inst);
                    var left = !string.IsNullOrEmpty(first) ? first : inst.Event?.Name ?? LocalizationService.Get("Lesson_Short") ?? "Lekce";
                    var trainerFull = EventTrainerDisplayHelper.GetTrainerDisplayName(inst.Event?.EventTrainersList?.FirstOrDefault());
                    if (!string.IsNullOrWhiteSpace(trainerFull))
                    {
                        var trainerShort = FormatTrainerShort(trainerFull);
                        titleText = left + ": " + trainerShort;
                    }
                    else
                    {
                        titleText = left;
                    }
                }
                catch { titleText = inst.Event?.Name ?? LocalizationService.Get("Lesson_Short") ?? "Lekce"; }
            }

            bool makeBold = OnlyMine;
            if (!OnlyMine && normalizedHighlights.Count > 0)
            {
                try
                {
                    var first = TkOlympApp.MainPage.GroupedEventRow.ComputeFirstRegistrantPublic(inst);
                    var frNorm = NormalizeName(first);
                    var matched = !string.IsNullOrWhiteSpace(frNorm) && normalizedHighlights.Any(h => frNorm.Contains(h) || h.Contains(frNorm));
                    if (matched) makeBold = true;
                }
                catch { }
            }

            var title = new Label { Text = titleText, FontAttributes = (makeBold ? FontAttributes.Bold : FontAttributes.None), FontSize = 12 };
            var time = new Label { Text = ((inst.Since.HasValue ? inst.Since.Value.ToString("HH:mm") : "--:--") + " – " + (inst.Until.HasValue ? inst.Until.Value.ToString("HH:mm") : "--:--")), FontSize = 11, TextColor = Colors.Gray };
            var stack = new VerticalStackLayout { Spacing = 2 };
            stack.Add(title);
            stack.Add(time);
            frame.Content = stack;

            var tap = new TapGestureRecognizer();
            tap.Tapped += async (s, e) => {
                if (frame.BindingContext is EventInstance ev && ev.Event?.Id is long id)
                {
                    if (id != 0)
                        await _navigationService.NavigateToAsync($"{nameof(EventPage)}?id={id}");
                }
            };
            frame.GestureRecognizers.Add(tap);

            var gutterDp = gutterDpGlobal;
            var colCount = Math.Max(1, it.ColumnCount);
            var totalGutterDp = gutterDp * (colCount - 1);
            var availableInnerDp = Math.Max(0.0, dayWidthDp - totalGutterDp);
            var colWidthDp = availableInnerDp / colCount;
            var leftDp = dayIndex * (dayWidthDp + dayGapDp) + it.ColumnIndex * (colWidthDp + gutterDp);

            AbsoluteLayout.SetLayoutBounds(frame, new Rect(leftDp, top + halfHourOffset, Math.Max(20, colWidthDp), Math.Max(20, height)));
            AbsoluteLayout.SetLayoutFlags(frame, AbsoluteLayoutFlags.None);
            TimelineLayout.Children.Add(frame);
        }

        // Draw vertical separators on top (for 3-day and 7-day views)
        if (days == 3 || days == 7)
        {
            for (int d = 1; d < days; d++)
            {
                var x = d * (dayWidthDp + dayGapDp);
                var vlineTop = new BoxView { WidthRequest = 1, BackgroundColor = lineColor, Opacity = lineOpacity };
                AbsoluteLayout.SetLayoutBounds(vlineTop, new Rect(x, 0, 1, totalHeight + halfHourOffset));
                AbsoluteLayout.SetLayoutFlags(vlineTop, AbsoluteLayoutFlags.None);
                TimelineLayout.Children.Add(vlineTop);
            }
        }

        _nowLine = new BoxView { HeightRequest = 2, BackgroundColor = Colors.Red, Opacity = 0.9 };
        TimelineLayout.Children.Add(_nowLine);
        UpdateNowLinePosition();
        TimelineLayout.HeightRequest = totalHeight + halfHourOffset;
    }

    private static string FormatTrainerShort(string? fullName)
    {
        if (string.IsNullOrWhiteSpace(fullName)) return string.Empty;
        var parts = fullName.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length == 1) return parts[0];
        var surname = parts[^1];
        var titles = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "Mgr.", "Ing.", "Bc.", "PhDr.", "MUDr.", "Mgr", "Ing", "PhDr", "Bc", "Dr.", "Dr"
        };
        string? given = null;
        foreach (var p in parts)
        {
            if (titles.Contains(p) || (p.EndsWith('.') && p.Length <= 4)) continue;
            given = p;
            break;
        }
        if (string.IsNullOrEmpty(given)) given = parts[0];
        var initial = !string.IsNullOrEmpty(given) ? given[0].ToString() : string.Empty;
        return (!string.IsNullOrEmpty(initial) ? initial + ". " : string.Empty) + surname;
    }

    public void UpdateNowLinePosition()
    {
        if (_nowLine == null) return;
        var now = DateTime.Now;
        var minutes = (now.Hour * 60 + now.Minute) - (_startHour * 60) + now.Second / 60.0;
        var top = minutes / 60.0 * _hourHeight;
        var halfHourOffset = _hourHeight / 2.0;
        var totalHours = Math.Max(1, _endHour - _startHour);
        var totalHeight = totalHours * _hourHeight;
        if (top < 0 || top > totalHeight) { _nowLine.IsVisible = false; return; }
        _nowLine.IsVisible = true;
        AbsoluteLayout.SetLayoutBounds(_nowLine, new Rect(0, top + halfHourOffset, 1, 2));
        AbsoluteLayout.SetLayoutFlags(_nowLine, AbsoluteLayoutFlags.WidthProportional);
    }

    private void StartNowTimer()
    {
        StopNowTimer();
        _timerCts = new CancellationTokenSource();
        var ct = _timerCts.Token;
        _ = Task.Run(async () => {
            while (!ct.IsCancellationRequested)
            {
                try { MainThread.BeginInvokeOnMainThread(UpdateNowLinePosition); } catch { }
                try { await Task.Delay(TimeSpan.FromSeconds(30), ct); } catch (TaskCanceledException) { break; }
            }
        }, ct);
    }

    private void StopNowTimer()
    {
        try { _timerCts?.Cancel(); _timerCts = null; } catch { }
    }

    private class LayoutItem
    {
        public EventInstance Inst { get; set; } = null!;
        public double Top { get; set; }
        public double Height { get; set; }
        public int RawStart { get; set; }
        public int RawEnd { get; set; }
        public int ColumnIndex { get; set; }
        public int ColumnCount { get; set; }
    }
}

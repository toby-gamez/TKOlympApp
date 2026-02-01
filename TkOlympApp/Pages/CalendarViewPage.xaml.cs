using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.ApplicationModel;
using System.Diagnostics;
using System.Globalization;
using Microsoft.Maui.Devices;
using Microsoft.Maui.Layouts;
using Microsoft.Maui.Controls.Shapes;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class CalendarViewPage : ContentPage
{
    private bool _isLoading;
    private bool _onlyMine = true;
    private int _daysVisible = 1; // 1 = day, 3 = three-day, 7 = week
    private DateTime _date;
    private readonly int _startHour = 6;
    private readonly int _endHour = 22;
    private readonly double _hourHeight = 60; // pixels per hour
    private CancellationTokenSource? _timerCts;
    private BoxView? _nowLine;
    // If set (in dp), this value will be used as the exact gap between columns.
    private double _columnGapDp = -1;

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

    private class LayoutItem
    {
        public EventService.EventInstance Inst { get; set; } = null!;
        public double Top { get; set; }
        public double Height { get; set; }
        public int RawStart { get; set; }
        public int RawEnd { get; set; }
        public int ColumnIndex { get; set; }
        public int ColumnCount { get; set; }
    }
    public CalendarViewPage()
    {
        InitializeComponent();
        _date = DateTime.Now.Date;
        UpdateDateLabel();
        UpdateViewButtonsVisuals();
        try { SetTopTabVisuals(_onlyMine); } catch { }
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _ = LoadEventsAsync();
        StartNowTimer();
    }

    protected override void OnDisappearing()
    {
        base.OnDisappearing();
        StopNowTimer();
    }

    private void UpdateDateLabel()
    {
        if (_daysVisible <= 1)
        {
            // Single day: show only weekday when the date is in the current week, otherwise show short date (no year)
            var today = DateTime.Now.Date;
            // determine week start (use Monday as first day of week according to current culture)
            var diffToday = (int)CultureInfo.CurrentCulture.DateTimeFormat.FirstDayOfWeek;
            var firstDayOfWeek = today.AddDays(-((int)today.DayOfWeek - (int)CultureInfo.CurrentCulture.DateTimeFormat.FirstDayOfWeek + 7) % 7);
            var lastDayOfWeek = firstDayOfWeek.AddDays(6);
            if (_date.Date >= firstDayOfWeek && _date.Date <= lastDayOfWeek)
            {
                var weekday = _date.ToString("dddd", CultureInfo.CurrentCulture);
                try { weekday = weekday.ToLower(CultureInfo.CurrentCulture); } catch { }
                DateLabel.Text = weekday;
            }
            else
            {
                DateLabel.Text = _date.ToString("d. M.", CultureInfo.CurrentCulture);
            }
        }
        else
        {
            var start = _date.Date;
            var end = start.AddDays(_daysVisible - 1);
            if (_daysVisible == 7)
            {
                // Week view: show the date range (short, without year)
                DateLabel.Text = start.ToString("d. M.", CultureInfo.CurrentCulture) + " – " + end.ToString("d. M.", CultureInfo.CurrentCulture);
            }
            else
            {
                // multi-day (1<days<=3): show short range without year, e.g. "30. 1. – 1. 2."
                DateLabel.Text = start.ToString("d. M.", CultureInfo.CurrentCulture) + " – " + end.ToString("d. M.", CultureInfo.CurrentCulture);
            }
        }
    }

    private async Task LoadEventsAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        try
        {
            var start = _date.Date;
            var end = start.AddDays(_daysVisible);
            List<EventService.EventInstance> events;
            if (_onlyMine)
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end);
            else
                events = await EventService.GetEventInstancesForRangeListAsync(start, end);

            await RenderTimeline(events ?? new List<EventService.EventInstance>());
        }
        catch (Exception ex)
        {
            try { await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title") ?? "Chyba", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
        finally
        {
            _isLoading = false;
        }
    }

    private static string NormalizeName(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return string.Empty;
        var normalized = s.Normalize(System.Text.NormalizationForm.FormD);
        var chars = normalized.Where(c => System.Globalization.CharUnicodeInfo.GetUnicodeCategory(c) != System.Globalization.UnicodeCategory.NonSpacingMark).ToArray();
        return new string(chars).ToLowerInvariant().Trim();
    }

    private async Task RenderTimeline(List<EventService.EventInstance> events)
    {
        TimelineLayout.Children.Clear();
        TimeLabelsStack.Children.Clear();

        var totalHours = Math.Max(1, _endHour - _startHour);
        var totalHeight = totalHours * _hourHeight;

        // offset everything by half an hour so the label text can be vertically centered
        var halfHourOffset = _hourHeight / 2.0;

        // determine a single line color for hour/vertical separators that works in both themes
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
                catch { }

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
                catch { }

                var distinct = highlightNames.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
                normalizedHighlights = distinct.Select(NormalizeName).Where(x => !string.IsNullOrWhiteSpace(x)).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            }
            catch { }
        }

        // Prepare layout items (compute raw start/end and pixel positions)
        // Filter out cancelled event instances so they are not shown in the calendar
        events = events.Where(e => !(e.IsCancelled)).ToList();
        var startDate = _date.Date;
        var items = events.OrderBy(e => e.Since ?? e.UpdatedAt)
            .Select(e =>
            {
                var since = e.Since ?? e.UpdatedAt;
                var until = e.Until ?? since.AddMinutes(30);
                // determine which day column this event belongs to
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
                // If the item already carries a day index (in ColumnIndex), keep it as DayIndex in a temp field by reusing ColumnIndex for column packing
                var prevDay = it.ColumnIndex; // day index set earlier
                it.ColumnIndex = assigned;
                // store day index in RawStart temporarily? Instead, use ColumnCount to store day index offset by columns later
                // We'll keep day index separately in a local map when rendering
            }
            var finalCols = colEnds.Count;
            foreach (var it in group) it.ColumnCount = finalCols;
        }

        // Render items using column layout to avoid overlaps
        // compute layout width once (use screen width as viewport reference)
        var infoMain = DeviceDisplay.MainDisplayInfo;
        double screenWidthDp = infoMain.Width / infoMain.Density;
        double layoutWidthDp = TimelineLayout.Width;
        if (layoutWidthDp <= 1.0) layoutWidthDp = screenWidthDp;

        // day-level gap between day columns (dp)
        double dayGapDp = 8.0;
        var days = Math.Max(1, _daysVisible);

        // compute gutter/margins and per-day width once (so we can set timeline width for horizontal scroll)
        const double marginRightProp = 0.03;
        var gutterPropGlobal = _columnGapDp > 0 ? GutterProportionFromDp(_columnGapDp) : 0.01;
        double gutterDpGlobal = _columnGapDp > 0 ? _columnGapDp : gutterPropGlobal * screenWidthDp;
        double marginRightDpGlobal = marginRightProp * screenWidthDp;

        var totalDayGaps = dayGapDp * (days - 1);
        // compute available viewport width for non-scrolling layouts (subtract label width and page padding)
        double labelWidthDp = TimeLabelsStack?.WidthRequest > 0 ? TimeLabelsStack.WidthRequest : 56.0;
        double pagePaddingDp = 24.0; // Grid Padding="12" on both sides
        var availableViewportDp = Math.Max(120.0, screenWidthDp - labelWidthDp - pagePaddingDp);

        double dayWidthDp;
        if (days <= 3)
        {
            // Fit into viewport exactly (no horizontal scroll)
            dayWidthDp = Math.Max(100.0, (availableViewportDp - totalDayGaps) / days);
            // set timeline width to viewport so ScrollView won't scroll
            try { TimelineLayout.WidthRequest = availableViewportDp; } catch { }
        }
        else
        {
            // For many days, allow horizontal scrolling by using a fixed per-day width
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

            // determine day index for this event
            var since = inst.Since ?? inst.UpdatedAt;
            var dayIndex = (since.Date - startDate).Days;
            if (dayIndex < 0 || dayIndex >= days) continue;

            // Choose background: use Secondary / SecondaryDark for lessons, fallback to LightBlue
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
                    var trainerFull = EventService.GetTrainerDisplayName(inst.Event?.EventTrainersList?.FirstOrDefault());
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
            // Decide whether to bold the title: always bold in "Mine" view; in "All" only bold if event matches current user
            bool makeBold = _onlyMine;
            if (!_onlyMine && normalizedHighlights.Count > 0)
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
                if (frame.BindingContext is EventService.EventInstance ev && ev.Event?.Id is long id)
                {
                    var page = new EventPage();
                    if (id != 0) page.EventId = id;
                    await Navigation.PushAsync(page);
                }
            };
            frame.GestureRecognizers.Add(tap);

            // Within this day, compute column widths for overlapping items
            var gutterDp = gutterDpGlobal;
            var colCount = Math.Max(1, it.ColumnCount);
            var totalGutterDp = gutterDp * (colCount - 1);
            var availableInnerDp = Math.Max(0.0, dayWidthDp - totalGutterDp);
            var colWidthDp = availableInnerDp / colCount;
            // left offset: day offset + column offset inside day
            var leftDp = dayIndex * (dayWidthDp + dayGapDp) + it.ColumnIndex * (colWidthDp + gutterDp);

            AbsoluteLayout.SetLayoutBounds(frame, new Rect(leftDp, top + halfHourOffset, Math.Max(20, colWidthDp), Math.Max(20, height)));
            AbsoluteLayout.SetLayoutFlags(frame, AbsoluteLayoutFlags.None);
            TimelineLayout.Children.Add(frame);
        }

        // draw vertical separators on top so they are visible over events (only for 3-day and 7-day views)
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
        // Skip common prefix/title tokens (e.g. "Mgr.", "Ing.") when determining first name
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

    private void UpdateNowLinePosition()
    {
        if (_nowLine == null) return;
        var now = DateTime.Now;
        var minutes = (now.Hour * 60 + now.Minute) - (_startHour * 60) + now.Second / 60.0;
        var top = minutes / 60.0 * _hourHeight;
        // keep in sync with the half-hour offset used when rendering hour lines/events
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

    private void OnPrevDayClicked(object? sender, EventArgs e)
    {
        _date = _date.AddDays(-_daysVisible);
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnNextDayClicked(object? sender, EventArgs e)
    {
        _date = _date.AddDays(_daysVisible);
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnTodayClicked(object? sender, EventArgs e)
    {
        var today = DateTime.Now.Date;
        if (_date == today) return;
        _date = today;
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnTabMineClicked(object? sender, EventArgs e)
    {
        _onlyMine = true;
        try { SetTopTabVisuals(true); } catch { }
        _ = LoadEventsAsync();
    }

    private void OnTabAllClicked(object? sender, EventArgs e)
    {
        _onlyMine = false;
        try { SetTopTabVisuals(false); } catch { }
        _ = LoadEventsAsync();
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
        catch { }
    }

    private void OnViewDayClicked(object? sender, EventArgs e)
    {
        if (_daysVisible == 1) return;
        _daysVisible = 1;
        UpdateViewButtonsVisuals();
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnView3DayClicked(object? sender, EventArgs e)
    {
        if (_daysVisible == 3) return;
        _daysVisible = 3;
        UpdateViewButtonsVisuals();
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnViewWeekClicked(object? sender, EventArgs e)
    {
        if (_daysVisible == 7) return;
        _daysVisible = 7;
        UpdateViewButtonsVisuals();
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void UpdateViewButtonsVisuals()
    {
        try
        {
            if (ViewDayAction != null) ViewDayAction.IsEnabled = (_daysVisible != 1);
            if (View3DayAction != null) View3DayAction.IsEnabled = (_daysVisible != 3);
            if (ViewWeekAction != null) ViewWeekAction.IsEnabled = (_daysVisible != 7);
        }
        catch { }
    }
}


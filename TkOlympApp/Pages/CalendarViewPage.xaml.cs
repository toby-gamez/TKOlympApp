using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.ApplicationModel;
using System.Diagnostics;
using Microsoft.Maui.Devices;
using Microsoft.Maui.Layouts;
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
        DateLabel.Text = _date.ToString("dddd, dd.MM.yyyy");
    }

    private async Task LoadEventsAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        try
        {
            var start = _date.Date;
            var end = start.AddDays(1);
            List<EventService.EventInstance> events;
            if (_onlyMine)
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end);
            else
                events = await EventService.GetEventInstancesForRangeListAsync(start, end);

            await RenderTimeline(events ?? new List<EventService.EventInstance>());
        }
        catch (Exception ex)
        {
            try { await DisplayAlert(LocalizationService.Get("Error_Loading_Title") ?? "Chyba", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
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
        for (int h = _startHour; h <= _endHour; h++)
        {
            var y = (h - _startHour) * _hourHeight;
            var label = new Label { Text = h.ToString("D2") + ":00", FontSize = 12, HeightRequest = _hourHeight, VerticalTextAlignment = TextAlignment.Center };
            TimeLabelsStack.Children.Add(label);

            var line = new BoxView { HeightRequest = 1, BackgroundColor = Colors.LightGray };
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
        var items = events.OrderBy(e => e.Since ?? e.UpdatedAt)
            .Select(e =>
            {
                var since = e.Since ?? e.UpdatedAt;
                var until = e.Until ?? since.AddMinutes(30);
                var rawStart = since.Hour * 60 + since.Minute;
                var duration = Math.Max(15, (int)(until - since).TotalMinutes);
                var rawEnd = rawStart + duration;
                var startMinutes = rawStart - (_startHour * 60);
                var top = startMinutes / 60.0 * _hourHeight;
                var height = duration / 60.0 * _hourHeight;
                return new LayoutItem { Inst = e, Top = top, Height = height, RawStart = rawStart, RawEnd = rawEnd };
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
                it.ColumnIndex = assigned;
            }
            var finalCols = colEnds.Count;
            foreach (var it in group) it.ColumnCount = finalCols;
        }

        // Render items using column layout to avoid overlaps
        foreach (var it in items)
        {
            var inst = it.Inst;
            var top = it.Top;
            var height = it.Height;
            if (top + height < 0 || top > totalHeight) continue;

            // Choose background: use Secondary / SecondaryDark for lessons, fallback to LightBlue
            Color bgColor = Colors.LightBlue;
            if (string.Equals(inst.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
                    var key = theme == AppTheme.Dark ? "SecondaryDark" : "Secondary";
                    if (Application.Current?.Resources.TryGetValue(key, out var res) == true)
                    {
                        if (res is Color c) bgColor = c;
                    }
                }
                catch { }
            }

            var frame = new Frame
            {
                CornerRadius = 6,
                Padding = new Thickness(6),
                HasShadow = false,
                BackgroundColor = bgColor,
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
                    if (ev.Id > 0) page.EventInstanceId = ev.Id;
                    await Navigation.PushAsync(page);
                }
            };
            frame.GestureRecognizers.Add(tap);

            // first column starts at the very left (no left margin); others get gutter spacing
            const double baseLeft = 0.0; // no extra left offset for first column
            const double marginRight = 0.03;
            var gutter = _columnGapDp > 0 ? GutterProportionFromDp(_columnGapDp) : 0.01; // space between columns
            var colCount = Math.Max(1, it.ColumnCount);
            // Try to use actual layout width (dp). If not measured yet, fallback to screen width in dp.
            double layoutWidthDp = TimelineLayout.Width;
            if (layoutWidthDp <= 1.0)
            {
                var info = DeviceDisplay.MainDisplayInfo;
                layoutWidthDp = info.Width / info.Density;
            }

            // gutter is in proportion when configured via _columnGapDp -> converted earlier
            // but if _columnGapDp is set, prefer exact dp value; otherwise derive dp from proportional gutter
            double gutterDp;
            if (_columnGapDp > 0) gutterDp = _columnGapDp;
            else gutterDp = gutter * layoutWidthDp; // default small gap

            // margins in dp: baseLeft is 0, marginRight was proportion 0.03 -> convert to dp
            double marginRightDp = marginRight * layoutWidthDp;

            var totalGutterDp = gutterDp * (colCount - 1);
            var availableDp = Math.Max(0.0, layoutWidthDp - /*left*/ 0.0 - marginRightDp - totalGutterDp);
            var colWidthDp = availableDp / colCount;
            var leftDp = /*left*/ 0.0 + it.ColumnIndex * (colWidthDp + gutterDp);

            AbsoluteLayout.SetLayoutBounds(frame, new Rect(leftDp, top + halfHourOffset, Math.Max(20, colWidthDp), Math.Max(20, height)));
            AbsoluteLayout.SetLayoutFlags(frame, AbsoluteLayoutFlags.None);
            TimelineLayout.Children.Add(frame);
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
        _date = _date.AddDays(-1);
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnNextDayClicked(object? sender, EventArgs e)
    {
        _date = _date.AddDays(1);
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnTodayClicked(object? sender, EventArgs e)
    {
        _date = DateTime.Now.Date;
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnTabMineClicked(object? sender, EventArgs e)
    {
        _onlyMine = true;
        _ = LoadEventsAsync();
    }

    private void OnTabAllClicked(object? sender, EventArgs e)
    {
        _onlyMine = false;
        _ = LoadEventsAsync();
    }
}


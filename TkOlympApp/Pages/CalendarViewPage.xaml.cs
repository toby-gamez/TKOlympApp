using Microsoft.Maui.Controls;
using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.Controls.Shapes;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class CalendarViewPage : ContentPage
{
    private enum ViewMode { Day, ThreeDay, Week }
    private ViewMode _mode = ViewMode.Day;
    private readonly System.Collections.Generic.Dictionary<int, Grid> _dayContainers = new();

    public CalendarViewPage()
    {
        InitializeComponent();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _ = LoadAndRenderAsync();
    }

    private void OnViewModeClicked(object? sender, EventArgs e)
    {
        if (sender == DayViewButton) _mode = ViewMode.Day;
        else if (sender == ThreeDayViewButton) _mode = ViewMode.ThreeDay;
        else if (sender == WeekViewButton) _mode = ViewMode.Week;
        _ = LoadAndRenderAsync();
    }

    private async Task LoadAndRenderAsync()
    {
        try
        {
            BuildGrid();
            // Determine range
            var start = DateTime.Now.Date;
            int days = _mode == ViewMode.Day ? 1 : _mode == ViewMode.ThreeDay ? 3 : 7;
            var end = start.AddDays(days).AddDays(1); // inclusive

            List<EventService.EventInstance> events = await EventService.GetEventInstancesForRangeListAsync(start, end);
            RenderEvents(events, start, days);
        }
        catch
        {
            // ignore errors for now
        }
    }

    private void BuildGrid()
    {
        HoursGrid.Children.Clear();
        HoursGrid.RowDefinitions.Clear();
        HoursGrid.ColumnDefinitions.Clear();

        // Left column for hour labels
        HoursGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(50, GridUnitType.Absolute) });

        int cols = _mode == ViewMode.Day ? 1 : _mode == ViewMode.ThreeDay ? 3 : 7;
        for (int c = 0; c < cols; c++)
            HoursGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });

        // 30-minute rows (48 rows for 24h)
        for (int r = 0; r < 48; r++)
        {
            HoursGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(30) });
            if (r % 2 == 0)
            {
                var hour = (r / 2).ToString("D2") + ":00";
                var label = new Label { Text = hour, FontSize = 12, VerticalTextAlignment = TextAlignment.Start, Margin = new Thickness(4,2,0,0), Opacity = 0.8 };
                HoursGrid.Add(label, 0, r);
            }
        }

        // Light separators: add horizontal lines
        for (int r = 0; r < 48; r++)
        {
            var line = new BoxView { HeightRequest = 1, BackgroundColor = Colors.Transparent };
            HoursGrid.Add(line, 1, r);
            Grid.SetColumnSpan(line, HoursGrid.ColumnDefinitions.Count - 1);
        }

        // Create a nested Grid container per day column (spans all rows) for columnized positioning
        for (int d = 0; d < cols; d++)
        {
            var container = new Grid { BackgroundColor = Colors.Transparent };
            // create 48 rows to match hours (30-min rows)
            for (int r = 0; r < 48; r++)
                container.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            // start with single column; will expand when placing events
            container.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });

            int colIndex = 1 + d;
            HoursGrid.Add(container, colIndex, 0);
            Grid.SetRowSpan(container, 48);
            _dayContainers[d] = container;
        }
    }
    private void RenderEvents(List<EventService.EventInstance> events, DateTime startDate, int days)
    {
        if (events == null) return;

        // Group events by day index
        var eventsByDay = new Dictionary<int, List<EventService.EventInstance>>();
        foreach (var inst in events)
        {
            try
            {
                var since = inst.Since ?? inst.UpdatedAt;
                var dayIndex = (since.Date - startDate).Days;
                if (dayIndex < 0 || dayIndex >= days) continue;
                if (!eventsByDay.TryGetValue(dayIndex, out var list)) { list = new List<EventService.EventInstance>(); eventsByDay[dayIndex] = list; }
                list.Add(inst);
            }
            catch { }
        }

        // For each day, compute overlap slots and render into the day's AbsoluteLayout
        foreach (var kv in eventsByDay)
        {
            int day = kv.Key;
            var list = kv.Value.OrderBy(i => (i.Since ?? i.UpdatedAt)).ToList();
            var intervals = new List<(EventService.EventInstance inst, double startMin, double endMin)>();
            foreach (var inst in list)
            {
                var s = inst.Since ?? inst.UpdatedAt;
                var u = inst.Until ?? inst.Since?.AddHours(1) ?? inst.UpdatedAt.AddHours(1);
                var startMin = s.TimeOfDay.TotalMinutes;
                var endMin = u.TimeOfDay.TotalMinutes;
                intervals.Add((inst, startMin, endMin));
            }

            // Assign clusters and columns
            var assigned = new Dictionary<EventService.EventInstance, (int slot, int total)>();
            var visited = new HashSet<EventService.EventInstance>();

            for (int i = 0; i < intervals.Count; i++)
            {
                var root = intervals[i].inst;
                if (visited.Contains(root)) continue;
                var clusterIdx = new List<int>();
                var queue = new Queue<int>();
                queue.Enqueue(i);
                visited.Add(root);
                while (queue.Count > 0)
                {
                    var idx = queue.Dequeue();
                    clusterIdx.Add(idx);
                    for (int j = 0; j < intervals.Count; j++)
                    {
                        if (visited.Contains(intervals[j].inst)) continue;
                        var a = intervals[idx];
                        var b = intervals[j];
                        if (!(a.endMin <= b.startMin || b.endMin <= a.startMin))
                        {
                            visited.Add(intervals[j].inst);
                            queue.Enqueue(j);
                        }
                    }
                }

                var clusterEvents = clusterIdx.Select(ix => intervals[ix]).OrderBy(x => x.startMin).ToList();
                var columnEnd = new List<double>();
                foreach (var ev in clusterEvents)
                {
                    int place = -1;
                    for (int c = 0; c < columnEnd.Count; c++)
                    {
                        if (ev.startMin >= columnEnd[c]) { place = c; columnEnd[c] = ev.endMin; break; }
                    }
                    if (place == -1)
                    {
                        place = columnEnd.Count;
                        columnEnd.Add(ev.endMin);
                    }
                    assigned[ev.inst] = (place, -1);
                }
                int totalCols = columnEnd.Count;
                foreach (var ev in clusterEvents)
                {
                    var info = assigned[ev.inst];
                    assigned[ev.inst] = (info.slot, totalCols);
                }
            }

            if (!_dayContainers.TryGetValue(day, out var container)) continue;
            // ensure container has enough columns for the maximum total
            int maxCols = assigned.Values.Select(v => v.total).DefaultIfEmpty(1).Max();
            if (container.ColumnDefinitions.Count < maxCols)
            {
                for (int cc = container.ColumnDefinitions.Count; cc < maxCols; cc++)
                    container.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });
            }

            foreach (var kvp in assigned)
            {
                try
                {
                    var inst = kvp.Key;
                    var slot = kvp.Value.slot;
                    var total = kvp.Value.total;
                    var since = inst.Since ?? inst.UpdatedAt;
                    var until = inst.Until ?? inst.Since?.AddHours(1) ?? inst.UpdatedAt.AddHours(1);
                    var startRow = since.Hour * 2 + (since.Minute >= 30 ? 1 : 0);
                    var durationMins = Math.Max(30, (until - since).TotalMinutes);
                    int rowSpan = (int)Math.Ceiling(durationMins / 30.0);
                    var bgBrush = GetBrushForType(inst.Event?.Type);
                    bool isLesson = string.Equals(inst.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase);
                    string titleText = isLesson ? GetTrainerLabel(inst) : (inst.Event?.Name ?? string.Empty);
                    string firstRegistrant = isLesson ? ComputeFirstRegistrantSimple(inst) : string.Empty;

                    var titleLabel = new Label { Text = titleText, FontAttributes = FontAttributes.Bold, FontSize = 14, LineBreakMode = LineBreakMode.TailTruncation };
                    titleLabel.TextColor = GetTextColorForType(inst.Event?.Type);
                    var subtitleLabel = new Label { Text = firstRegistrant, FontSize = 12, LineBreakMode = LineBreakMode.TailTruncation };
                    subtitleLabel.TextColor = GetTextColorForType(inst.Event?.Type);
                    var timeLabel = new Label { Text = since.ToString("HH:mm") + "–" + until.ToString("HH:mm"), FontSize = 12, LineBreakMode = LineBreakMode.TailTruncation };
                    timeLabel.TextColor = GetTextColorForType(inst.Event?.Type);
                    var stack = new VerticalStackLayout { Spacing = 2 };
                    stack.Add(titleLabel);
                    if (!string.IsNullOrWhiteSpace(firstRegistrant)) stack.Add(subtitleLabel);
                    stack.Add(timeLabel);

                    var border = new Border
                    {
                        Background = bgBrush,
                        Padding = new Thickness(6),
                        Stroke = null,
                        StrokeShape = new RoundRectangle { CornerRadius = 6 },
                        Content = stack
                    };

                    var tap = new TapGestureRecognizer();
                    tap.Tapped += async (s, e) =>
                    {
                        try
                        {
                            if (inst.Event?.Id is long id)
                            {
                                var page = new EventPage();
                                if (id != 0) page.EventId = id;
                                if (inst.Id > 0) page.EventInstanceId = inst.Id;
                                await Navigation.PushAsync(page);
                            }
                        }
                        catch { }
                    };
                    border.GestureRecognizers.Add(tap);

                    Grid.SetColumn(border, slot);
                    Grid.SetRow(border, startRow);
                    Grid.SetRowSpan(border, Math.Max(1, rowSpan));
                    container.Add(border);
                }
                catch { }
            }
        }
    }

    private Color GetColorForType(string? type)
    {
        if (string.IsNullOrWhiteSpace(type)) return Color.FromArgb("#CC999999");
        switch (type.Trim().ToLowerInvariant())
        {
            case "group": return Color.FromArgb("#CC2D9CDB"); // blue (primary)
            case "lesson": return Color.FromArgb("#CCFFB74D"); // orange (secondary)
            case "camp": return Color.FromArgb("#CC8E24AA"); // purple
            case "reservation": return Color.FromArgb("#CC26A69A"); // teal
            case "holiday": return Color.FromArgb("#CCBDBDBD"); // gray
            default: return Color.FromArgb("#CC90A4AE");
        }
    }

    private Color GetTextColorForType(string? type)
    {
        // use black for light backgrounds, white for dark
        switch (type?.Trim().ToLowerInvariant())
        {
            case "group": return Colors.White;
            case "lesson": return Colors.Black;
            case "camp": return Colors.White;
            case "reservation": return Colors.White;
            case "holiday": return Colors.Black;
            default: return Colors.Black;
        }
    }

    private string GetTrainerLabel(EventService.EventInstance inst)
    {
        try
        {
            var trainers = inst.Event?.EventTrainersList;
            if (trainers != null && trainers.Count > 0)
            {
                var t = trainers[0];
                if (t != null && !string.IsNullOrWhiteSpace(t.Name)) return t.Name;
            }
            return inst.Event?.Name ?? string.Empty;
        }
        catch { return inst.Event?.Name ?? string.Empty; }
    }

    private string ComputeFirstRegistrantSimple(EventService.EventInstance inst)
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

    private Microsoft.Maui.Controls.Brush GetBrushForType(string? type)
    {
        try
        {
            var col = GetColorForType(type);
            return new SolidColorBrush(col);
        }
        catch { return new SolidColorBrush(Color.FromArgb("#CC90A4AE")); }
    }
}

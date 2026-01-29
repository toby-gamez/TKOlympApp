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
    private readonly System.Collections.Generic.Dictionary<int, AbsoluteLayout> _dayEventLayers = new();

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
        // clear previously stored day containers to avoid stale references
        _dayContainers.Clear();

        // Left column for hour labels
        HoursGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(50, GridUnitType.Absolute) });

        int cols = _mode == ViewMode.Day ? 1 : _mode == ViewMode.ThreeDay ? 3 : 7;
        for (int c = 0; c < cols; c++)
            HoursGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });

        // Header row for trainer names
        HoursGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        // 30-minute rows (48 rows for 24h) placed after header row
        for (int r = 0; r < 48; r++)
        {
            HoursGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(30) });
            if (r % 2 == 0)
            {
                var hour = (r / 2).ToString("D2") + ":00";
                var label = new Label { Text = hour, FontSize = 12, VerticalTextAlignment = TextAlignment.Start, Margin = new Thickness(4,2,0,0), Opacity = 0.8 };
                HoursGrid.Add(label, 0, r + 1);
            }
        }

        // Light separators: add horizontal lines
        for (int r = 0; r < 48; r++)
        {
            var line = new BoxView { HeightRequest = 1, BackgroundColor = Colors.Transparent };
            HoursGrid.Add(line, 1, r + 1);
            Grid.SetColumnSpan(line, HoursGrid.ColumnDefinitions.Count - 1);
        }

        // Create a nested Grid container per day column (spans all rows) for columnized positioning
        for (int d = 0; d < cols; d++)
        {
            var container = new Grid { BackgroundColor = Colors.Transparent };
                // container.RowSpacing = 0; // Removed to eliminate extra gaps
            // container: row 0 = header, row 1 = body (events layer)
            container.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            container.RowDefinitions.Add(new RowDefinition { Height = GridLength.Star });
            // start with single column; will expand when placing events
            container.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });

            int colIndex = 1 + d;
            // place container starting at row 1 (below the global header row)
            HoursGrid.Add(container, colIndex, 1);
            Grid.SetRowSpan(container, 48);
            // add an AbsoluteLayout inside the container to render events precisely (row 1)
            var eventsLayer = new AbsoluteLayout { BackgroundColor = Colors.Transparent };
            container.Add(eventsLayer, 0, 1);
            Grid.SetColumnSpan(eventsLayer, container.ColumnDefinitions.Count);
            _dayEventLayers[d] = eventsLayer;
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

            // Assign clusters and columns (group overlapping intervals into clusters,
            // then assign columns inside each cluster so items in the same timeslot don't overlap)
            var assigned = new Dictionary<EventService.EventInstance, (int slot, int total)>();

            // create a sorted list of intervals with original indices
            var indexed = intervals.Select((iv, idx) => new { iv.inst, iv.startMin, iv.endMin, idx })
                                   .OrderBy(x => x.startMin)
                                   .ToList();

            // Build clusters: contiguous overlapping groups
            var clusters = new List<List<int>>();
            List<int>? current = null;
            double currentMax = double.MinValue;
            foreach (var item in indexed)
            {
                if (current == null)
                {
                    current = new List<int> { item.idx };
                    currentMax = item.endMin;
                }
                else
                {
                    if (item.startMin < currentMax)
                    {
                        // overlaps with current cluster
                        current.Add(item.idx);
                        if (item.endMin > currentMax) currentMax = item.endMin;
                    }
                    else
                    {
                        clusters.Add(current);
                        current = new List<int> { item.idx };
                        currentMax = item.endMin;
                    }
                }
            }
            if (current != null) clusters.Add(current);

            // For each cluster, assign columns greedily
            foreach (var cluster in clusters)
            {
                // get cluster events ordered by start
                var clusterEvents = cluster.Select(i => intervals[i]).OrderBy(x => x.startMin).ToList();
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

            // Build trainer columns for this day: each distinct trainer gets its own column
            var trainerLabels = list
                .Select(i => GetTrainerLabel(i))
                .Where(s => !string.IsNullOrWhiteSpace(s))
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            int trainerCount = trainerLabels.Count;
            var trainerIndex = trainerLabels.Select((t, idx) => new { t, idx })
                                           .ToDictionary(x => x.t, x => x.idx, StringComparer.OrdinalIgnoreCase);


            // Precompute simple column assignment for each event so we create only needed columns
            var columnAssignment = new Dictionary<EventService.EventInstance, int>();
            foreach (var kvp2 in assigned)
            {
                var inst2 = kvp2.Key;
                bool isLesson2 = string.Equals(inst2.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase);
                if (isLesson2)
                {
                    var tName2 = GetTrainerLabel(inst2);
                    if (!string.IsNullOrWhiteSpace(tName2) && trainerIndex.TryGetValue(tName2, out var tIdx2))
                        columnAssignment[inst2] = tIdx2;
                    else
                        columnAssignment[inst2] = trainerCount > 0 ? trainerCount - 1 : 0;
                }
                else
                {
                    // place non-lesson into last trainer column if exists, otherwise column 0
                    columnAssignment[inst2] = trainerCount > 0 ? trainerCount - 1 : 0;
                }
            }

            var usedCols = columnAssignment.Values.DefaultIfEmpty(0).Distinct().OrderBy(i => i).ToList();
            int neededColumns = usedCols.Count > 0 ? usedCols.Max() + 1 : 1;
            if (container.ColumnDefinitions.Count < neededColumns)
            {
                for (int cc = container.ColumnDefinitions.Count; cc < neededColumns; cc++)
                    container.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });
            }

            // remove existing header labels (row 0) so we can refresh them
            var existingHeaders = container.Children.Where(ch => Microsoft.Maui.Controls.Grid.GetRow((Microsoft.Maui.Controls.BindableObject)ch) == 0).ToList();
            foreach (var h in existingHeaders) container.Children.Remove(h);

            // Render trainer header labels inside the per-day container (row 0)
            int headerCount = Math.Min(trainerCount, neededColumns);
            for (int ti = 0; ti < headerCount; ti++)
            {
                try
                {
                    var lbl = new Label { Text = trainerLabels[ti], FontSize = 12, HorizontalTextAlignment = TextAlignment.Center, VerticalTextAlignment = TextAlignment.Center, Margin = new Thickness(4,4) };
                    lbl.TextColor = Colors.Black;
                    container.Add(lbl, ti, 0);
                }
                catch { }
            }

            // ensure we have an events layer for precise positioning and clear previous events
            if (!_dayEventLayers.TryGetValue(day, out var eventsLayer))
            {
                eventsLayer = new AbsoluteLayout { BackgroundColor = Colors.Transparent };
                container.Add(eventsLayer, 0, 1);
                Grid.SetColumnSpan(eventsLayer, neededColumns);
                _dayEventLayers[day] = eventsLayer;
            }
            else
            {
                eventsLayer.Children.Clear();
                Grid.SetColumn(eventsLayer, 0);
                Grid.SetColumnSpan(eventsLayer, neededColumns);
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
                    var startMin = (since.TimeOfDay.TotalMinutes);
                    var durationMins = Math.Max(1, (until - since).TotalMinutes);
                    var bgBrush = GetBrushForType(inst.Event?.Type);
                    bool isLesson = string.Equals(inst.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase);
                    // For lessons: prefer registration-based naming and append trainer to the title
                    string firstRegistrant = isLesson ? ComputeFirstRegistrantSimple(inst) : string.Empty;
                    string trainerName = isLesson ? GetTrainerLabel(inst) : string.Empty;
                    string titleText;
                    if (isLesson)
                    {
                        if (!string.IsNullOrWhiteSpace(firstRegistrant))
                            titleText = firstRegistrant + (string.IsNullOrWhiteSpace(trainerName) ? string.Empty : " • " + trainerName);
                        else
                            titleText = (!string.IsNullOrWhiteSpace(trainerName) ? trainerName : (inst.Event?.Name ?? string.Empty));
                        // subtitle: show event name (if different from title)
                        if (string.Equals(titleText, inst.Event?.Name ?? string.Empty, StringComparison.OrdinalIgnoreCase))
                            firstRegistrant = string.Empty;
                        else
                            firstRegistrant = inst.Event?.Name ?? string.Empty;
                    }
                    else
                    {
                        titleText = inst.Event?.Name ?? string.Empty;
                        firstRegistrant = string.Empty;
                    }

                    var titleLabel = new Label { Text = titleText, FontAttributes = FontAttributes.Bold, FontSize = 14, LineBreakMode = LineBreakMode.TailTruncation };
                    titleLabel.TextColor = GetTextColorForType(inst.Event?.Type);
                    var subtitleLabel = new Label { Text = firstRegistrant, FontSize = 12, LineBreakMode = LineBreakMode.TailTruncation };
                    subtitleLabel.TextColor = GetTextColorForType(inst.Event?.Type);
                    var timeLabel = new Label { Text = since.ToString("HH:mm") + "–" + until.ToString("HH:mm"), FontSize = 12, LineBreakMode = LineBreakMode.TailTruncation };
                    timeLabel.TextColor = GetTextColorForType(inst.Event?.Type);
                    var stack = new VerticalStackLayout { Spacing = 2 };
                    stack.Add(titleLabel);
                    // hide subtitle if there isn't enough vertical space (prioritize time and title)
                    if (!string.IsNullOrWhiteSpace(firstRegistrant) && durationMins >= 40) stack.Add(subtitleLabel);
                    stack.Add(timeLabel);

                    var border = new Border
                    {
                        Background = bgBrush,
                        Padding = new Thickness(6),
                        Stroke = null,
                        StrokeShape = new RoundRectangle { CornerRadius = 6 },
                        Content = stack,
                        HorizontalOptions = LayoutOptions.Fill,
                        VerticalOptions = LayoutOptions.Start
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

                    // Position precisely inside the eventsLayer (proportional to full day = 1440 minutes)
                    double dayMinutes = 24.0 * 60.0;
                    double yProp = startMin / dayMinutes;
                    double hProp = Math.Max(0.01, durationMins / dayMinutes);

                    double xProp, wProp;
                    if (!isLesson)
                    {
                        // place non-lesson events into any existing column (choose last trainer column if present)
                        int chosen = Math.Min(Math.Max(0, trainerCount - 1), Math.Max(0, neededColumns - 1));
                        xProp = chosen / (double)Math.Max(1, neededColumns);
                        wProp = 1.0 / Math.Max(1, neededColumns);
                        // ensure we use full precise duration for vertical size (no artificial shrinking)
                        hProp = Math.Max((durationMins / dayMinutes), 0.0005);
                    }
                    else
                    {
                        int actualSlot = 0;
                        if (isLesson && !string.IsNullOrWhiteSpace(trainerName) && trainerIndex.TryGetValue(trainerName, out var tIdx))
                            actualSlot = tIdx;
                        else
                            actualSlot = Math.Max(0, trainerCount) + Math.Max(0, slot);
                        xProp = actualSlot / (double) Math.Max(1, neededColumns);
                        wProp = 1.0 / Math.Max(1, neededColumns);
                    }

                    AbsoluteLayout.SetLayoutFlags(border, Microsoft.Maui.Layouts.AbsoluteLayoutFlags.All);
                    AbsoluteLayout.SetLayoutBounds(border, new Rect(xProp, yProp, wProp, hProp));
                    eventsLayer.Add(border);
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
                if (t != null && !string.IsNullOrWhiteSpace(t.Name))
                {
                    // Format trainer as initial + lastname (no titles), e.g. "A. Novák"
                    var parts = t.Name.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                    if (parts.Length == 0) return t.Name;
                    var last = parts[^1];
                    // find a token to use as given name (skip tokens that look like titles ending with '.')
                    string? first = parts.FirstOrDefault(p => !p.EndsWith('.') && p != last);
                    if (string.IsNullOrWhiteSpace(first)) first = parts.First();
                    var initial = !string.IsNullOrWhiteSpace(first) ? char.ToUpperInvariant(first[0]) + "." : string.Empty;
                    return string.IsNullOrWhiteSpace(initial) ? last : initial + " " + last;
                }
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
                    return parts.Length > 1 ? parts[parts.Length - 1] : parts[0];
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
                        if (!string.IsNullOrWhiteSpace(full)) return ExtractSurname(full);
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
                    if (!string.IsNullOrWhiteSpace(p.LastName)) return p.LastName;
                    if (!string.IsNullOrWhiteSpace(p.Name))
                    {
                        var parts = p.Name.Split(' ', System.StringSplitOptions.RemoveEmptyEntries);
                        if (parts.Length > 0) return parts[^1];
                    }
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

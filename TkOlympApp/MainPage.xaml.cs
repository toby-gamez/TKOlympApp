using Microsoft.Maui.Controls;
using System.Diagnostics;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class MainPage : ContentPage
{
    private readonly ObservableCollection<WeekGroup> _weeks = new();
    private bool _isLoading;
    private bool _onlyMine = true;

    public MainPage()
    {
        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            try { Debug.WriteLine($"MainPage: InitializeComponent failed: {ex}"); } catch { }
            Content = new Microsoft.Maui.Controls.StackLayout
            {
                Children =
                {
                    new Microsoft.Maui.Controls.Label { Text = "Chyba načítání stránky: " + ex.Message }
                }
            };
            return;
        }

        EventsCollection.ItemsSource = _weeks;
        try
        {
            OnlyMineToolbarItem.Text = _onlyMine ? "Mé: Ano" : "Mé: Ne";
        }
        catch
        {
            // ignore if toolbar item not available in some platforms
        }

        UpdateEmptyView();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        try { Debug.WriteLine("MainPage: OnAppearing"); } catch { }
        await LoadEventsAsync();
    }

    private async Task LoadEventsAsync()
    {
        try { Debug.WriteLine("MainPage: LoadEventsAsync start"); } catch { }
        if (_isLoading) return;
        UpdateEmptyView();
        _isLoading = true;
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        EventsCollection.IsVisible = false;
        try
        {
            var today = DateTime.Now.Date;
            var start = today;
            var end = today.AddDays(30);
            List<EventService.EventInstance> events;
            if (_onlyMine)
            {
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end, onlyMine: true);
            }
            else
            {
                events = await EventService.GetEventInstancesForRangeListAsync(start, end);
            }

            // Group events by day (date only) and then into weeks (Monday-based)
            var groupsByDate = events
                .GroupBy(e => e.Since.HasValue ? e.Since.Value.Date : e.UpdatedAt.Date)
                .ToDictionary(g => g.Key, g => g.OrderBy(ev => ev.Since).ToList());

            // Week map: weekStart -> list of dates
            var weekMap = new SortedDictionary<DateTime, List<DateTime>>();
            foreach (var date in groupsByDate.Keys.OrderBy(d => d))
            {
                var isoDow = ((int)date.DayOfWeek == 0) ? 7 : (int)date.DayOfWeek; // Monday=1..Sunday=7
                var weekStart = date.AddDays(1 - isoDow).Date; // Monday
                if (!weekMap.TryGetValue(weekStart, out var list))
                {
                    list = new List<DateTime>();
                    weekMap[weekStart] = list;
                }
                list.Add(date);
            }

            _weeks.Clear();
            var culture = new CultureInfo("cs-CZ");
            foreach (var kv in weekMap)
            {
                var weekStart = kv.Key;
                var weekEnd = weekStart.AddDays(6);
                var wg = new WeekGroup($"{weekStart:dd.MM.yyyy} – {weekEnd:dd.MM.yyyy}");
                foreach (var date in kv.Value.OrderBy(d => d))
                {
                    if (!groupsByDate.TryGetValue(date, out var dayEvents)) continue;
                    if (dayEvents.Count == 0) continue; // skip empty days
                    string dayLabel;
                    var todayDate = DateTime.Now.Date;
                    if (date == todayDate) dayLabel = "Dnes";
                    else if (date == todayDate.AddDays(1)) dayLabel = "Zítra";
                    else
                    {
                        var name = culture.DateTimeFormat.GetDayName(date.DayOfWeek);
                        dayLabel = char.ToUpper(name[0], culture) + name.Substring(1) + $" {date:dd.MM.}";
                    }

                    var dg = new DayGroup(dayLabel, dayEvents);
                    wg.Add(dg);
                }
                if (wg.Count > 0)
                    _weeks.Add(wg);
            }
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba načtení", ex.Message, "OK");
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
            EventsCollection.IsVisible = true;
            try
            {
                if (EventsRefresh != null)
                    EventsRefresh.IsRefreshing = false;
            }
            catch
            {
                // ignore UI update failures
            }
            _isLoading = false;
            UpdateEmptyView();
        }
    }

    private async void OnlyMineSwitch_Toggled(object? sender, ToggledEventArgs e)
    {
        UpdateEmptyView();
        await LoadEventsAsync();
    }

    private void UpdateEmptyView()
    {
        try
        {
            var onlyMine = _onlyMine;
            var emptyText = onlyMine ? "Nemáte žádné události." : "Nejsou žádné události.";
            if (EmptyLabel != null)
            {
                EmptyLabel.Text = emptyText;
                var total = _weeks.Sum(w => w.Sum(d => d.TotalRows));
                var showEmpty = !_isLoading && total == 0;
                EmptyLabel.IsVisible = showEmpty;
                if (EventsCollection != null)
                    EventsCollection.IsVisible = !showEmpty;
            }
        }
        catch
        {
            // ignore UI update failures
        }
    }

    private async void EventsCollection_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        var selected = e.CurrentSelection?.FirstOrDefault() as EventService.EventInstance;
        if (selected != null)
        {
            // Clear selection on the source CollectionView (inner collection)
            try { (sender as CollectionView).SelectedItem = null; } catch { }
            if (selected.IsCancelled) return;
            if (selected.Event?.Id is long eventId)
            {
                var since = selected.Since.HasValue ? selected.Since.Value.ToString("o") : null;
                var until = selected.Until.HasValue ? selected.Until.Value.ToString("o") : null;
                var uri = $"EventPage?id={eventId}" +
                          (since != null ? $"&since={Uri.EscapeDataString(since)}" : string.Empty) +
                          (until != null ? $"&until={Uri.EscapeDataString(until)}" : string.Empty);
                await Shell.Current.GoToAsync(uri);
            }
        }
    }

    private async void OnEventCardTapped(object? sender, TappedEventArgs e)
    {
        if (sender is Border frame && frame.BindingContext is EventService.EventInstance instance && instance.Event?.Id is long eventId)
        {
            if (instance.IsCancelled) return;
            var since = instance.Since.HasValue ? instance.Since.Value.ToString("o") : null;
            var until = instance.Until.HasValue ? instance.Until.Value.ToString("o") : null;
            var uri = $"EventPage?id={eventId}" +
                      (since != null ? $"&since={Uri.EscapeDataString(since)}" : string.Empty) +
                      (until != null ? $"&until={Uri.EscapeDataString(until)}" : string.Empty);
            await Shell.Current.GoToAsync(uri);
        }
    }

    private async void OnGroupedRowTapped(object? sender, TappedEventArgs e)
    {
        try
        {
            if (sender is Border border && border.BindingContext is GroupedEventRow row && row.Instance?.Event?.Id is long eventId)
            {
                if (row.Instance.IsCancelled) return;
                var since = row.Instance.Since.HasValue ? row.Instance.Since.Value.ToString("o") : null;
                var until = row.Instance.Until.HasValue ? row.Instance.Until.Value.ToString("o") : null;
                var uri = $"EventPage?id={eventId}" + (since != null ? $"&since={Uri.EscapeDataString(since)}" : string.Empty) + (until != null ? $"&until={Uri.EscapeDataString(until)}" : string.Empty);
                await Shell.Current.GoToAsync(uri);
            }
        }
        catch { }
    }

    private async void OnReloadClicked(object? sender, EventArgs e)
    {
        await LoadEventsAsync();
    }

    private async void OnToggleOnlyMineClicked(object? sender, EventArgs e)
    {
        _onlyMine = !_onlyMine;
        try
        {
            OnlyMineToolbarItem.Text = _onlyMine ? "Mé: Ano" : "Mé: Ne";
        }
        catch
        {
            // ignore
        }
        UpdateEmptyView();
        await LoadEventsAsync();
    }

    private async void OnEventsRefresh(object? sender, EventArgs e)
    {
        try { Debug.WriteLine("MainPage: OnEventsRefresh invoked"); } catch { }
        await LoadEventsAsync();
        try
        {
            if (EventsRefresh != null)
                EventsRefresh.IsRefreshing = false;
        }
        catch
        {
            // ignore
        }
    }

    // Helper grouping types
    public sealed class WeekGroup : ObservableCollection<DayGroup>
    {
        public string WeekLabel { get; }
        public WeekGroup(string weekLabel) { WeekLabel = weekLabel; }
    }

    public sealed class DayGroup
    {
        public string DayLabel { get; }
        public ObservableCollection<GroupedTrainer> GroupedTrainers { get; }
        public ObservableCollection<EventService.EventInstance> SingleEvents { get; }

        public DayGroup(string dayLabel, IEnumerable<EventService.EventInstance> events)
        {
            DayLabel = dayLabel;
            GroupedTrainers = new ObservableCollection<GroupedTrainer>();
            SingleEvents = new ObservableCollection<EventService.EventInstance>();

            // group by event name + trainer name (same day already)
            var groups = events.GroupBy(e => new {
                Name = e.Event?.Name ?? string.Empty,
                Trainer = e.Event?.EventTrainersList?.FirstOrDefault()?.Name ?? string.Empty
            })
            .OrderBy(g => g.Min(x => x.Since ?? x.UpdatedAt));

            foreach (var g in groups)
            {
                var ordered = g.OrderBy(i => i.Since).ToList();
                if (ordered.Count == 1)
                {
                    SingleEvents.Add(ordered[0]);
                }
                else
                {
                    var trainerTitle = string.IsNullOrWhiteSpace(g.Key.Trainer) ? g.Key.Name : g.Key.Trainer;
                    var gt = new GroupedTrainer(trainerTitle);
                    for (int i = 0; i < ordered.Count; i++)
                    {
                        var inst = ordered[i];
                        var firstRegistrant = i == 0 ? GroupedEventRow.ComputeFirstRegistrantPublic(inst) : string.Empty;
                        gt.Rows.Add(new GroupedEventRow(inst, firstRegistrant));
                    }
                    GroupedTrainers.Add(gt);
                }
            }
        }

        // Count rows including single-event wrappers
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

    // (no wrapper class) single events are stored in DayGroup.SingleEvents

    public sealed class GroupedEventRow
    {
        public EventService.EventInstance Instance { get; }
        public string TimeRange { get; }
        public string FirstRegistrant { get; }
        public string DurationText { get; }
        public GroupedEventRow(EventService.EventInstance instance, string firstRegistrantOverride = "")
        {
            Instance = instance;
            var since = instance.Since;
            var until = instance.Until;
            TimeRange = (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " - " + (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
            FirstRegistrant = string.IsNullOrEmpty(firstRegistrantOverride) ? ComputeFirstRegistrant(instance) : firstRegistrantOverride;
            DurationText = ComputeDuration(instance);
        }

        // expose a public helper to compute the registrant without needing an instance method call
        public static string ComputeFirstRegistrantPublic(EventService.EventInstance inst) => ComputeFirstRegistrant(inst);

        private static string ComputeFirstRegistrant(EventService.EventInstance inst)
        {
            try
            {
                // prefer eventRegistrationsList if available
                var evt = inst.Event;
                if (evt?.EventRegistrationsList != null && evt.EventRegistrationsList.Count > 0)
                {
                    var node = evt.EventRegistrationsList[0];
                    if (node?.Person != null && !string.IsNullOrWhiteSpace(node.Person.Name))
                        return node.Person.Name;
                    if (node?.Couple != null)
                    {
                        var manLn = node.Couple.Man?.LastName;
                        var womanLn = node.Couple.Woman?.LastName;
                        if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn))
                            return manLn + " - " + womanLn;
                        if (!string.IsNullOrWhiteSpace(manLn)) return manLn;
                        if (!string.IsNullOrWhiteSpace(womanLn)) return womanLn;
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
    }
}
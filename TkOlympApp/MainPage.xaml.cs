using Microsoft.Maui.Controls;
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
                    new Microsoft.Maui.Controls.Label { Text = LocalizationService.Get("Error_Loading_Prefix") + ex.Message }
                }
            };
            return;
        }

        EventsCollection.ItemsSource = _weeks;
        try
        {
            SetTopTabVisuals(_onlyMine);
        }
        catch
        {
            // ignore if buttons not available on some platforms
        }

        UpdateEmptyView();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        try { Debug.WriteLine("MainPage: OnAppearing"); } catch { }

        try
        {
            Dispatcher.Dispatch(async () =>
            {
                try { await LoadEventsAsync(); } catch { }
            });
        }
        catch
        {
            // fallback if dispatch isn't available
            _ = LoadEventsAsync();
        }
    }

    private async Task LoadEventsAsync()
    {
        try { Debug.WriteLine("MainPage: LoadEventsAsync start"); } catch { }

        // Ensure the RefreshView shows a refresh indicator when we start programmatically
        try { if (EventsRefresh != null && !EventsRefresh.IsRefreshing) EventsRefresh.IsRefreshing = true; } catch { }

        if (_isLoading)
        {
            // If a load is already in progress, ensure RefreshView isn't left spinning.
            try { EventsRefresh?.IsRefreshing = false; } catch { }
            return;
        }
        UpdateEmptyView();
        _isLoading = true;
        Loading?.IsVisible = true;
        Loading?.IsRunning = true;
        EventsCollection?.IsVisible = false;
        try
        {
            var today = DateTime.Now.Date;
            var start = today;
            var end = today.AddDays(30);
            List<EventService.EventInstance> events;
            if (_onlyMine)
            {
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end);
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
            var culture = CultureInfo.CurrentUICulture ?? CultureInfo.CurrentCulture;
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
                    if (date == todayDate) dayLabel = LocalizationService.Get("Today") ?? "Dnes";
                    else if (date == todayDate.AddDays(1)) dayLabel = LocalizationService.Get("Tomorrow") ?? "Zítra";
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

            // background fetch for missing first-registrant names using GetEventAsync
            _ = Task.Run(async () =>
            {
                try
                {
                    foreach (var week in _weeks)
                    {
                        foreach (var day in week)
                        {
                            foreach (var gt in day.GroupedTrainers)
                            {
                                foreach (var row in gt.Rows)
                                {
                                    var inst = row.Instance;
                                    var evt = inst?.Event;
                                    if (string.IsNullOrWhiteSpace(row.FirstRegistrant) && evt != null && evt.Id != 0)
                                    {
                                        try
                                        {
                                            var id = evt.Id;
                                            var details = await EventService.GetEventAsync(id);
                                            if (details?.EventRegistrations?.Nodes != null && details.EventRegistrations.Nodes.Count > 0)
                                            {
                                                var node = details.EventRegistrations.Nodes[0];
                                                string name = string.Empty;
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
                                                if (!string.IsNullOrWhiteSpace(name))
                                                    Dispatcher?.Dispatch(() => row.FirstRegistrant = name);
                                            }
                                        }
                                        catch { }
                                    }
                                }
                            }
                        }
                    }
                }
                catch { }
            });
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            Loading?.IsRunning = false;
            Loading?.IsVisible = false;
            EventsCollection?.IsVisible = true;
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

            // no-op
        }
    }

    // Public helper to request a refresh from external callers (e.g. AppShell after auth)
    public async Task RefreshEventsAsync()
    {
        try { EventsRefresh?.IsRefreshing = true; } catch { }
        await LoadEventsAsync();
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
            var emptyText = onlyMine ? (LocalizationService.Get("Empty_MyEvents") ?? "Nemáte žádné události.") : (LocalizationService.Get("Empty_NoEvents") ?? "Nejsou žádné události.");
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
            try { var cv = sender as CollectionView; if (cv != null) cv.SelectedItem = null; } catch { }
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
            GroupedEventRow? row = null;
            if (sender is VisualElement ve)
                row = ve.BindingContext as GroupedEventRow;
            // fallback checks for other possible sender types
            if (row == null)
            {
                if (sender is Border b) row = b.BindingContext as GroupedEventRow;
                else if (sender is CollectionView cv) row = cv.BindingContext as GroupedEventRow;
            }

            if (row?.Instance?.Event?.Id is long eventId)
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
        try { SetTopTabVisuals(_onlyMine); } catch { }
        UpdateEmptyView();
        await LoadEventsAsync();
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

    private async void OnTabMineClicked(object? sender, EventArgs e)
    {
        _onlyMine = true;
        try { SetTopTabVisuals(true); } catch { }
        UpdateEmptyView();
        await LoadEventsAsync();
    }

    private async void OnTabAllClicked(object? sender, EventArgs e)
    {
        _onlyMine = false;
        try { SetTopTabVisuals(false); } catch { }
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
                    // Always prefix grouped titles with localized lesson prefix
                    var lessonPrefix = LocalizationService.Get("Lesson_Prefix") ?? "Lekce: ";
                    trainerTitle = lessonPrefix + trainerTitle;
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

    public sealed class GroupedEventRow : INotifyPropertyChanged
    {
        public EventService.EventInstance Instance { get; }
        public string TimeRange { get; }
        private string _firstRegistrant;
        public string FirstRegistrant
        {
            get => _firstRegistrant;
            set
            {
                if (_firstRegistrant != value)
                {
                    _firstRegistrant = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FirstRegistrant)));
                }
            }
        }
        public string DurationText { get; }

        public GroupedEventRow(EventService.EventInstance instance, string firstRegistrantOverride = "")
        {
            Instance = instance;
            var since = instance.Since;
            var until = instance.Until;
            TimeRange = (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " - " + (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
            _firstRegistrant = string.IsNullOrEmpty(firstRegistrantOverride) ? ComputeFirstRegistrant(instance) : firstRegistrantOverride;
            DurationText = ComputeDuration(instance);
        }

        public event PropertyChangedEventHandler? PropertyChanged;

        // helper used when constructing groups
        public static string ComputeFirstRegistrantPublic(EventService.EventInstance inst) => ComputeFirstRegistrant(inst);

        private static string ComputeFirstRegistrant(EventService.EventInstance inst)
        {
            try
            {
                // prefer eventRegistrationsList if available
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
    }
}
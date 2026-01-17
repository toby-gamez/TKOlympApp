using Microsoft.Maui.Controls;
using System.Diagnostics;
using System;
using System.Collections.ObjectModel;
using System.Collections.Generic;
using System.ComponentModel;
using System.Globalization;
using System.Linq;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class MainPage : ContentPage
{
    private DayGroup? _currentDay;
    private bool _isLoading;
    public ObservableCollection<NoticeboardService.Announcement> RecentAnnouncements { get; } = new();
    public ObservableCollection<CampItem> UpcomingCamps { get; } = new();

    public MainPage()
    {
        try
        {
            InitializeComponent();
            BindingContext = this;
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
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        try { Debug.WriteLine("MainPage: OnAppearing"); } catch { }

        // Initialization for notifications (moved from old calendar logic)
        try
        {
            Dispatcher.Dispatch(async () =>
            {
                try
                {
                    // Schedule notifications for upcoming events (1 hour and 5 minutes before)
                    // Load events 2 days ahead for notifications
                    var notifStart = DateTime.Now.Date;
                    var notifEnd = DateTime.Now.Date.AddDays(2).AddHours(23).AddMinutes(59);
                    var notifEvents = await EventService.GetMyEventInstancesForRangeAsync(notifStart, notifEnd);

                    // Check for changes and cancellations first
                    await EventNotificationService.CheckAndNotifyChangesAsync(notifEvents);

                    // Then schedule upcoming notifications
                    await EventNotificationService.ScheduleNotificationsForEventsAsync(notifEvents);
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"MainPage: Failed to schedule notifications: {ex}");
                }

                // Load upcoming events for display
                try { await LoadUpcomingEventsAsync(); } catch { }
                
                // Load recent announcements
                try { await LoadRecentAnnouncementsAsync(); } catch { }
                
                // Load upcoming camps
                try { await LoadUpcomingCampsAsync(); } catch { }
            });
        }
        catch
        {
            // fallback if dispatch isn't available
        }
    }

    private async Task LoadUpcomingEventsAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        try { Loading.IsVisible = true; } catch { }
        try { Loading.IsRunning = true; } catch { }

        try
        {
            var now = DateTime.Now;
            var start = now.Date;
            var end = now.Date.AddDays(14); // Načte tréninky na 14 dní dopředu

            var events = await EventService.GetMyEventInstancesForRangeAsync(start, end);
            
            // Seskupíme eventy podle dne
            var groupsByDate = events
                .Where(e => e.Since.HasValue)
                .GroupBy(e => e.Since!.Value.Date)
                .OrderBy(g => g.Key)
                .ToDictionary(g => g.Key, g => g.OrderBy(ev => ev.Since).ToList());

            // Vezmeme první den (dnešek nebo nejbližší den s eventy)
            var firstDate = groupsByDate.Keys.FirstOrDefault();
            
            _currentDay = null;
            if (firstDate != default && groupsByDate.TryGetValue(firstDate, out var dayEvents) && dayEvents.Count > 0)
            {
                var culture = CultureInfo.CurrentUICulture ?? CultureInfo.CurrentCulture;
                string dayLabel;
                var todayDate = DateTime.Now.Date;
                if (firstDate == todayDate) 
                    dayLabel = LocalizationService.Get("Today") ?? "Dnes";
                else if (firstDate == todayDate.AddDays(1)) 
                    dayLabel = LocalizationService.Get("Tomorrow") ?? "Zítra";
                else
                {
                    var name = culture.DateTimeFormat.GetDayName(firstDate.DayOfWeek);
                    dayLabel = char.ToUpper(name[0], culture) + name.Substring(1) + $" {firstDate:dd.MM.}";
                }

                _currentDay = new DayGroup(dayLabel, firstDate, dayEvents);
            }

            // Bind day to UI
            try { DayContent.BindingContext = _currentDay; } catch { }
            try { EmptyLabel.IsVisible = _currentDay == null; } catch { }
            try { DayContent.IsVisible = _currentDay != null; } catch { }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Failed to load upcoming events: {ex}");
            try { EmptyLabel.IsVisible = true; } catch { }
            try { DayContent.IsVisible = false; } catch { }
        }
        finally
        {
            _isLoading = false;
            try { Loading.IsVisible = false; } catch { }
            try { Loading.IsRunning = false; } catch { }
        }
    }

    private async void OnEventCardTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Border border && border.BindingContext is EventService.EventInstance evt && evt.Event?.Id != null)
            {
                await Shell.Current.GoToAsync($"EventPage?id={evt.Event.Id}");
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Navigation failed: {ex}");
        }
    }

    private async void OnGroupedRowTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Grid grid && grid.BindingContext is GroupedEventRow row)
            {
                var inst = row.Instance;
                var evt = inst?.Event;
                if (evt?.Id != null)
                {
                    await Shell.Current.GoToAsync($"EventPage?id={evt.Id}");
                }
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Navigation from grouped row failed: {ex}");
        }
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        try
        {
            await LoadUpcomingEventsAsync();
            await LoadRecentAnnouncementsAsync();
            await LoadUpcomingCampsAsync();
        }
        finally
        {
            try { if (sender is RefreshView rv) rv.IsRefreshing = false; } catch { }
        }
    }

    private async Task LoadRecentAnnouncementsAsync()
    {
        try
        {
            var announcements = await NoticeboardService.GetMyAnnouncementsAsync();
            RecentAnnouncements.Clear();
            
            // Vezmi 2 nejnovější (seřazené sestupně podle CreatedAt)
            var recent = announcements
                .OrderByDescending(a => a.CreatedAt)
                .Take(2)
                .ToList();
            
            foreach (var announcement in recent)
            {
                RecentAnnouncements.Add(announcement);
            }
            
            // Nastav viditelnost sekce
            try { RecentAnnouncementsSection.IsVisible = RecentAnnouncements.Count > 0; } catch { }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Failed to load recent announcements: {ex}");
            try { RecentAnnouncementsSection.IsVisible = false; } catch { }
        }
    }

    private async void OnAnnouncementTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Border border && border.BindingContext is NoticeboardService.Announcement announcement)
            {
                await Shell.Current.GoToAsync($"NoticePage?id={announcement.Id}");
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Navigation to notice failed: {ex}");
        }
    }

    private async Task LoadUpcomingCampsAsync()
    {
        try
        {
            var now = DateTime.Now;
            var pastStart = now.Date.AddYears(-1); // Načte soustředění rok zpět
            var futureEnd = now.Date.AddYears(1); // Načte soustředění na rok dopředu

            var events = await EventService.GetMyEventInstancesForRangeAsync(pastStart, futureEnd);
            
            // Filtrujeme pouze CAMP eventy a seřadíme podle absolutní vzdálenosti od dnešního dne
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

            // Nastav viditelnost sekce
            try { UpcomingCampsSection.IsVisible = UpcomingCamps.Count > 0; } catch { }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Failed to load upcoming camps: {ex}");
            try { UpcomingCampsSection.IsVisible = false; } catch { }
        }
    }

    private async void OnCampTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Border border && border.BindingContext is CampItem camp)
            {
                await Shell.Current.GoToAsync($"EventPage?id={camp.EventId}");
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"MainPage: Navigation to camp failed: {ex}");
        }
    }

    // Helper classes copied from CalendarPage
    public sealed class DayGroup
    {
        public string DayLabel { get; }
        public DateTime Date { get; }
        public ObservableCollection<GroupedTrainer> GroupedTrainers { get; }
        public ObservableCollection<EventService.EventInstance> SingleEvents { get; }

        public DayGroup(string dayLabel, DateTime date, IEnumerable<EventService.EventInstance> events)
        {
            DayLabel = dayLabel;
            Date = date;
            GroupedTrainers = new ObservableCollection<GroupedTrainer>();
            SingleEvents = new ObservableCollection<EventService.EventInstance>();

            var groupableEvents = new List<EventService.EventInstance>();
            var singleEventsList = new List<EventService.EventInstance>();

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

            var groups = groupableEvents.GroupBy(e => e.Event?.EventTrainersList?.FirstOrDefault()?.Name?.Trim() ?? string.Empty)
                .OrderBy(g => g.Min(x => x.Since ?? x.UpdatedAt));

            foreach (var g in groups)
            {
                var ordered = g.OrderBy(i => i.Since ?? i.UpdatedAt).ToList();
                var trainerName = g.Key;
                var trainerTitle = string.IsNullOrWhiteSpace(trainerName) ? LocalizationService.Get("Lessons") ?? "Lekce" : trainerName;
                var gt = new GroupedTrainer(trainerTitle);

                for (int i = 0; i < ordered.Count; i++)
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
        public EventService.EventInstance Instance { get; }
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

        public GroupedEventRow(EventService.EventInstance instance, string firstRegistrantOverride = "")
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

        public static string ComputeFirstRegistrantPublic(EventService.EventInstance inst) => ComputeFirstRegistrant(inst);

        private static string ComputeFirstRegistrant(EventService.EventInstance inst)
        {
            try
            {
                var evt = inst.Event;
                if (evt?.EventRegistrationsList != null && evt.EventRegistrationsList.Count > 0)
                {
                    var regs = evt.EventRegistrationsList;
                    int count = regs.Count;

                    var surnames = new List<string>();
                    static string ExtractSurname(string full)
                    {
                        if (string.IsNullOrWhiteSpace(full)) return string.Empty;
                        var parts = full.Split(' ', StringSplitOptions.RemoveEmptyEntries);
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

    public sealed class CampItem
    {
        public long EventId { get; set; }
        public string EventName { get; set; } = string.Empty;
        public string LocationName { get; set; } = string.Empty;
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
    }
}

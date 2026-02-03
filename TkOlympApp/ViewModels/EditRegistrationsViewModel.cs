using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Linq;
using System.Threading.Tasks;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EditRegistrationsViewModel : ViewModelBase
{
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;
    private EventDetails? _currentEvent;
    private RegItem? _selected;
    private RegGroup? _selectedGroup;

    [ObservableProperty]
    private long _eventId;

    [ObservableProperty]
    private bool _confirmEnabled = false;

    [ObservableProperty]
    private bool _trainerSelectionVisible = false;

    [ObservableProperty]
    private bool _trainerSelectionHeaderVisible = false;

    [ObservableProperty]
    private bool _successOverlayVisible = false;

    [ObservableProperty]
    private string _successText = string.Empty;

    [ObservableProperty]
    private bool _isRefreshing = false;

    public ObservableCollection<RegGroup> Groups { get; } = new();
    public ObservableCollection<TrainerOption> TrainerOptions { get; } = new();

    public EditRegistrationsViewModel(
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService,
        IUserNotifier notifier)
    {
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    partial void OnEventIdChanged(long value)
    {
        if (value != 0)
        {
            _ = LoadAsync();
        }
    }

    public override async Task InitializeAsync()
    {
        await base.InitializeAsync();
        if (EventId != 0)
        {
            await LoadAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        IsRefreshing = true;
        await LoadAsync();
        IsRefreshing = false;
    }

    public void OnSelectionChanged(RegItem? selected)
    {
        _selected = selected;
        if (_selected != null)
        {
            _selectedGroup = Groups.FirstOrDefault(g => g.Any(i => i.Id == _selected.Id));
            ConfirmEnabled = true;
            _ = LoadTrainerSelectionAsync();
        }
        else
        {
            _selectedGroup = null;
            ConfirmEnabled = false;
            TrainerSelectionVisible = false;
            TrainerSelectionHeaderVisible = false;
        }
    }

    public void OnRemainingItemsThresholdReached()
    {
        try
        {
            const int toAddTotal = 10;
            int added = 0;
            
            // First pass: add one per group
            foreach (var g in Groups)
            {
                if (added >= toAddTotal) break;
                if (g.AllCount > g.Count)
                {
                    g.RevealMore(1);
                    added++;
                }
            }

            // Second pass: fill remaining
            if (added < toAddTotal)
            {
                foreach (var g in Groups)
                {
                    if (added >= toAddTotal) break;
                    var remaining = g.AllCount - g.Count;
                    if (remaining > 0)
                    {
                        var need = Math.Min(toAddTotal - added, remaining);
                        g.RevealMore(need);
                        added += need;
                    }
                }
            }
        }
        catch (Exception ex) { LoggerService.SafeLogWarning<EditRegistrationsViewModel>("OnRemainingItemsThresholdReached failed: {0}", new object[] { ex.Message }); }
    }

    private async Task LoadAsync()
    {
        Groups.Clear();
        try
        {
            IsBusy = true;
            if (EventId == 0) return;

            try
            {
                _currentEvent = await _eventService.GetEventAsync(EventId);
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<EditRegistrationsViewModel>("Failed to load event {0}: {1}", new object[] { EventId, ex.Message });
                _currentEvent = null;
            }

            await _userService.InitializeAsync();
            var myCouples = new List<CoupleInfo>();
            try { myCouples = await _userService.GetActiveCouplesFromUsersAsync(); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<EditRegistrationsViewModel>("Failed to load couples: {0}", new object[] { ex.Message });
            }

            var myCoupleIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var c in myCouples)
            {
                if (!string.IsNullOrWhiteSpace(c.Id)) myCoupleIds.Add(c.Id!);
            }

            var myPersonId = _userService.CurrentPersonId ?? string.Empty;
            var me = await _userService.GetCurrentUserAsync();
            var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
            var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
            var myFull = string.IsNullOrWhiteSpace(myFirst)
                ? myLast
                : string.IsNullOrWhiteSpace(myLast)
                    ? myFirst
                    : (myFirst + " " + myLast).Trim();

            var startRange = DateTime.Now.Date.AddYears(-1);
            var endRange = DateTime.Now.Date.AddYears(1);
            var instances = await _eventService.GetEventInstancesForRangeListAsync(startRange, endRange);

            var seenRegIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var inst in instances)
            {
                var ev = inst.Event;
                if (ev == null) continue;
                if (ev.Id != EventId) continue;

                var evtName = ev.Name ?? string.Empty;
                var regs = ev.EventRegistrationsList ?? new List<EventRegistrationShort>();
                foreach (var reg in regs)
                {
                    var regId = reg.Id;
                    if (string.IsNullOrWhiteSpace(regId)) continue;
                    if (seenRegIds.Contains(regId)) continue;

                    var isMine = false;
                    var coupleId = reg.Couple?.Id;
                    if (!string.IsNullOrWhiteSpace(coupleId) && myCoupleIds.Contains(coupleId))
                    {
                        isMine = true;
                    }

                    if (!isMine && !string.IsNullOrWhiteSpace(myPersonId) &&
                        !string.IsNullOrWhiteSpace(reg.Person?.Id) &&
                        string.Equals(reg.Person.Id, myPersonId, StringComparison.OrdinalIgnoreCase))
                    {
                        isMine = true;
                    }

                    if (!isMine && !string.IsNullOrWhiteSpace(myFull))
                    {
                        var pf = reg.Person?.FirstName ?? string.Empty;
                        var pl = reg.Person?.LastName ?? string.Empty;
                        var pFull = string.IsNullOrWhiteSpace(pf)
                            ? pl
                            : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                        if (!string.IsNullOrWhiteSpace(pFull) && string.Equals(pFull, myFull, StringComparison.OrdinalIgnoreCase))
                        {
                            isMine = true;
                        }
                    }

                    if (!isMine) continue;
                    seenRegIds.Add(regId);

                    string display = string.Empty;
                    if (reg.Person != null)
                    {
                        var fn = reg.Person.FirstName ?? string.Empty;
                        var ln = reg.Person.LastName ?? string.Empty;
                        display = string.IsNullOrWhiteSpace(fn)
                            ? (string.IsNullOrWhiteSpace(ln) ? reg.Person.Name ?? string.Empty : ln)
                            : (string.IsNullOrWhiteSpace(ln) ? fn : (fn + " " + ln).Trim());
                    }
                    else if (reg.Couple != null)
                    {
                        var manFn = reg.Couple.Man?.FirstName ?? string.Empty;
                        var manLn = reg.Couple.Man?.LastName ?? string.Empty;
                        var womanFn = reg.Couple.Woman?.FirstName ?? string.Empty;
                        var womanLn = reg.Couple.Woman?.LastName ?? string.Empty;
                        var manName = string.IsNullOrWhiteSpace(manFn) ? manLn : (manFn + " " + manLn).Trim();
                        var womanName = string.IsNullOrWhiteSpace(womanFn) ? womanLn : (womanFn + " " + womanLn).Trim();
                        display = !string.IsNullOrWhiteSpace(manName) && !string.IsNullOrWhiteSpace(womanName)
                            ? (manName + " - " + womanName)
                            : (manName + womanName);
                    }

                    var item = new RegItem { Id = regId, Text = string.IsNullOrWhiteSpace(display) ? regId : display, Secondary = evtName };
                    var groupKey = string.IsNullOrWhiteSpace(item.Text) ? string.Empty : item.Text;
                    var grp = Groups.FirstOrDefault(g => string.Equals(g.Key, groupKey, StringComparison.OrdinalIgnoreCase));
                    if (grp == null)
                    {
                        grp = new RegGroup(groupKey);
                        Groups.Add(grp);
                    }
                    grp.AddToGroup(item);
                }
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EditRegistrationsViewModel>("LoadAsync error: {0}", new object[] { ex.Message });
        }
        finally
        {
            IsBusy = false;
        }
    }

    private async Task LoadTrainerSelectionAsync()
    {
        try
        {
            if (EventId == 0) return;
            
            var ev = _currentEvent ?? await _eventService.GetEventAsync(EventId);
            _currentEvent = ev;
            
            if (ev == null || ev.EventTrainersList == null || ev.EventTrainersList.Count == 0)
            {
                TrainerSelectionVisible = false;
                TrainerSelectionHeaderVisible = false;
                return;
            }
            
            TrainerOptions.Clear();
            foreach (var t in ev.EventTrainersList.OrderBy(x => EventTrainerDisplayHelper.GetTrainerDisplayName(x)))
            {
                var trainerName = EventTrainerDisplayHelper.GetTrainerDisplayName(t) ?? string.Empty;
                TrainerOptions.Add(new TrainerOption(trainerName, 0, t?.Id));
            }

            TrainerSelectionHeaderVisible = true;
            TrainerSelectionVisible = true;

            // Prefill existing demands
            try
            {
                if (_selected != null)
                {
                    var registrations = _currentEvent?.EventRegistrationsList ?? new List<EventRegistrationNode>();
                    var reg = registrations.FirstOrDefault(r => string.Equals(r.Id.ToString(), _selected.Id, StringComparison.OrdinalIgnoreCase));
                    if (reg?.EventLessonDemandsByRegistrationIdList == null) return;

                    foreach (var d in reg.EventLessonDemandsByRegistrationIdList)
                    {
                        try
                        {
                            var cnt = d.LessonCount;
                            var demandTrainerId = d.TrainerId?.ToString();

                            TrainerOption? match = null;
                            if (!string.IsNullOrWhiteSpace(demandTrainerId))
                            {
                                match = TrainerOptions.FirstOrDefault(o =>
                                    !string.IsNullOrWhiteSpace(o.Id) &&
                                    string.Equals(o.Id, demandTrainerId, StringComparison.OrdinalIgnoreCase));
                            }

                            if (match != null)
                            {
                                match.Count = cnt;
                                match.OriginalCount = cnt;
                            }
                        }
                        catch (Exception ex)
                        {
                            LoggerService.SafeLogWarning<EditRegistrationsViewModel>("Prefill demand item failed: {0}", new object[] { ex.Message });
                        }
                    }
                }
            }
            catch (Exception ex) { LoggerService.SafeLogWarning<EditRegistrationsViewModel>("Prefill existing demands failed: {0}", new object[] { ex.Message }); }
        }
        catch (Exception ex) { LoggerService.SafeLogWarning<EditRegistrationsViewModel>("LoadTrainerSelectionAsync failed: {0}", new object[] { ex.Message }); }
    }

    [RelayCommand]
    private async Task ConfirmAsync()
    {
        if (_selected == null) return;

        // Collect trainer demands
        var trainers = new List<TrainerOption>();
        foreach (var t in TrainerOptions)
        {
            if (t != null && (t.Count > 0 || t.OriginalCount > 0))
            {
                trainers.Add(t);
            }
        }

        if (trainers.Count == 0)
        {
            await _notifier.ShowAsync(
                LocalizationService.Get("Error_Title") ?? "Error",
                LocalizationService.Get("EditRegistrations_Error_NoTrainer") ?? "Vyberte trenéra.",
                LocalizationService.Get("Button_OK") ?? "OK");
            return;
        }

        try
        {
            IsBusy = true;
            
            foreach (var t in trainers)
            {
                if (string.IsNullOrWhiteSpace(t.Id)) continue;

                var ok = await _eventService.SetLessonDemandAsync(_selected.Id, t.Id, t.Count);
                if (!ok)
                {
                    throw new InvalidOperationException(LocalizationService.Get("EditRegistrations_Error_Save") ?? "Uložení změn selhalo.");
                }
            }

            SuccessText = LocalizationService.Get("EditRegistrations_Success_Text") ?? "Změny uloženy";
            SuccessOverlayVisible = true;
            
            await Task.Delay(900);
            await _navigationService.GoBackAsync();
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EditRegistrationsViewModel>("ConfirmAsync failed: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<EditRegistrationsViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
        finally
        {
            IsBusy = false;
        }
    }

    // Nested classes
    public sealed class RegItem
    {
        public string Id { get; set; } = string.Empty;
        public string Text { get; set; } = string.Empty;
        public string Secondary { get; set; } = string.Empty;
    }

    public sealed class RegGroup : ObservableCollection<RegItem>
    {
        private readonly List<RegItem> _all = new();
        public string Key { get; }
        public int AllCount => _all.Count;

        public RegGroup(string key)
        {
            Key = key;
        }

        public void AddToGroup(RegItem item)
        {
            _all.Add(item);
            if (this.Count == 0)
            {
                base.Add(item);
            }
        }

        public void RefreshVisible()
        {
            this.Clear();
            var first = _all.FirstOrDefault();
            if (first != null) base.Add(first);
        }

        public void RevealMore(int maxToAdd)
        {
            if (maxToAdd <= 0) return;
            int added = 0;
            int start = this.Count;
            for (int i = start; i < _all.Count && added < maxToAdd; i++)
            {
                base.Add(_all[i]);
                added++;
            }
        }

        public IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

        public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
    }

    public sealed class TrainerOption : INotifyPropertyChanged
    {
        private int _count;
        public string Name { get; set; }
        public string? Id { get; set; }
        public int OriginalCount { get; set; }
        public int Count
        {
            get => _count;
            set
            {
                if (_count != value)
                {
                    _count = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Count)));
                }
            }
        }

        public TrainerOption(string name, int count, string? id)
        {
            Name = name;
            _count = count;
            OriginalCount = count;
            Id = id;
        }

        public event PropertyChangedEventHandler? PropertyChanged;
    }
}
